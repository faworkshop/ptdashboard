package com.faworkshop.ptdashboard.security;

import com.google.firebase.auth.FirebaseToken;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/**
 * Extracts {@code Authorization: Bearer <token>} from incoming requests,
 * verifies the token via {@link FirebaseTokenVerifier}, and binds the
 * resulting {@link SecurityIdentity} to the request via Quarkus'
 * {@link CurrentIdentityAssociation}.
 *
 * <p><b>Public paths</b> (no token required) are matched by
 * {@link #PUBLIC_PATH_PREFIXES} and short-circuited before any token
 * extraction. Currently this covers the Quarkus management endpoints
 * ({@code /q/health/*}, {@code /q/openapi}, etc.) — these are intentionally
 * unauthenticated so liveness/readiness probes work in container
 * orchestrators.
 *
 * <p><b>Error contract</b>: any verification failure (missing header,
 * malformed header, bad/expired/revoked token, Firebase unavailable) yields
 * {@code 401 Unauthorized} with a small JSON body. The failure category is
 * recorded in the application log and surfaced via the
 * {@code WWW-Authenticate: Bearer} header as required by RFC 6750 §3.
 *
 * <p><b>Priority</b>: {@link Priorities#AUTHENTICATION} so this runs before
 * any user-supplied filters and before authorization checks
 * ({@code @RolesAllowed}).
 *
 * <p><b>Off the event loop</b>: the filter method itself only parses the
 * header and awaits a {@link io.smallrye.mutiny.Uni} backed by
 * {@link FirebaseTokenVerifier}, which schedules the blocking
 * {@code FirebaseAuth.verifyIdToken} call on the Mutiny worker pool.
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class FirebaseAuthFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(FirebaseAuthFilter.class);

    /**
     * Paths that bypass authentication entirely. Matched as prefix on the
     * request path (after {@link #normalisePath}).
     */
    private static final String[] PUBLIC_PATH_PREFIXES = {
            "/q/"   // Quarkus management (health, openapi, metrics)
    };

    /** Role assigned to every authenticated Firebase user. */
    private static final String DEFAULT_ROLE = "user";

    @Inject
    FirebaseTokenVerifier verifier;

    @Inject
    CurrentIdentityAssociation identityAssociation;

    @Override
    public void filter(ContainerRequestContext ctx) {
        String path = normalisePath(ctx.getUriInfo().getPath());

        if (isPublic(path)) {
            return;
        }

        String header = ctx.getHeaderString(HttpHeaders.AUTHORIZATION);
        String token = extractBearerToken(header);
        if (token == null) {
            abort(ctx, "missing_bearer_token");
            return;
        }
        if (token.isEmpty()) {
            abort(ctx, "empty_bearer_token");
            return;
        }

        // Blocking wait on the worker-pool-backed Uni. The actual verify
        // call runs off the event loop inside FirebaseTokenVerifier; this
        // await is just the synchronous bridge into JAX-RS.
        FirebaseToken verified;
        try {
            verified = verifier.verify(token).await().indefinitely();
        } catch (FirebaseTokenVerifier.FirebaseVerificationException e) {
            LOG.debugf("Firebase token verification failed: %s", e.getMessage());
            abort(ctx, "invalid_token");
            return;
        } catch (FirebaseTokenVerifier.FirebaseAuthUnavailableException e) {
            LOG.warnf("Firebase Admin SDK unavailable; rejecting request: %s", e.getMessage());
            abort(ctx, "auth_unavailable");
            return;
        } catch (RuntimeException e) {
            LOG.errorf(e, "Unexpected error during Firebase authentication");
            abort(ctx, "auth_error");
            return;
        }

        String uid = verified.getUid();
        if (uid == null || uid.isEmpty()) {
            LOG.warn("Verified Firebase token has no uid claim; rejecting");
            abort(ctx, "invalid_token");
            return;
        }

        // Build the SecurityIdentity with firebase_uid as the principal name.
        // Downstream code can do: @Inject SecurityIdentity identity;
        //                          identity.getPrincipal().getName() → uid.
        SecurityIdentity identity = QuarkusSecurityIdentity.builder()
                .setPrincipal(() -> uid)
                .addRole(DEFAULT_ROLE)
                .addAttribute("firebase_uid", uid)
                .addAttribute("firebase_email", verified.getEmail())
                .addAttribute("firebase_token", verified)
                .build();
        identityAssociation.setIdentity(identity);

        LOG.debugf("Authenticated Firebase uid=%s for %s %s", uid, ctx.getMethod(), path);
    }

    /**
     * Normalises the JAX-RS path so prefix checks don't need to handle both
     * {@code "q/health"} and {@code "/q/health"} (JAX-RS strips the leading
     * slash by default, but the container can hand us either depending on
     * the request URL shape).
     */
    private static String normalisePath(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "/";
        }
        return raw.startsWith("/") ? raw : "/" + raw;
    }

    private static boolean isPublic(String normalisedPath) {
        for (String prefix : PUBLIC_PATH_PREFIXES) {
            if (normalisedPath.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static void abort(ContainerRequestContext ctx, String reason) {
        ctx.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"")
                .type(MediaType.APPLICATION_JSON)
                .entity("{\"error\":\"unauthorized\",\"reason\":\"" + reason + "\"}")
                .build());
    }

    /**
     * Pulls the raw token out of an {@code Authorization} header.
     *
     * @param header the value of the {@code Authorization} header (may be
     *               {@code null}, blank, or in any case).
     * @return the trimmed token, or {@code null} if the header is missing
     *         or does not start with {@code "Bearer "} (case-insensitive).
     *         A non-null but blank value (e.g. {@code "Bearer   "}) is
     *         returned as an empty string — the caller distinguishes
     *         "no token" from "empty token" so the response can name
     *         the right reason.
     */
    static String extractBearerToken(String header) {
        if (header == null) {
            return null;
        }
        String trimmed = header.trim();
        if (trimmed.length() < 7) {
            return null;
        }
        if (!trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        return trimmed.substring(7).trim();
    }
}