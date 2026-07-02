package com.faworkshop.ptdashboard.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.concurrent.Executor;

/**
 * Reactive wrapper around {@link FirebaseAuth#verifyIdToken(String)}.
 *
 * <p>The Firebase Admin SDK's {@code verifyIdToken} blocks on a network call
 * to Google's public-key servers (cached after first hit). We MUST NOT call
 * it from a Vert.x event loop, so this wrapper schedules the verification
 * on the Quarkus/Mutiny-managed worker pool via
 * {@link Infrastructure#getDefaultWorkerPool()} and exposes the result as a
 * {@link Uni}.
 *
 * <p>Reference: {@code docs/auth.md} §"Token verification (reactive)".
 *
 * <p>Behavior when Firebase is not initialised (no service account configured):
 * every {@link #verify(String)} call fails with a
 * {@link FirebaseAuthUnavailableException} containing a descriptive message.
 * The {@link FirebaseAuthFilter} treats that as an authentication failure
 * (401), consistent with how a bad token is handled — there is no
 * public-by-default fall-through for protected paths just because the
 * verifier is unconfigured.
 */
@ApplicationScoped
public class FirebaseTokenVerifier {

    private static final Logger LOG = Logger.getLogger(FirebaseTokenVerifier.class);

    @Inject
    FirebaseAppLifecycle firebaseLifecycle;

    /**
     * Verify a Firebase ID token off the event loop.
     *
     * @param idToken the raw token from {@code Authorization: Bearer ...}
     * @return a {@link Uni} emitting the verified {@link FirebaseToken} on
     *         success, or failing with a {@link FirebaseVerificationException}
     *         for any verification problem (bad signature, expired, revoked,
     *         Firebase unavailable).
     */
    public Uni<FirebaseToken> verify(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            return Uni.createFrom().failure(
                    new FirebaseVerificationException("missing token"));
        }

        Executor worker = Infrastructure.getDefaultExecutor();

        return Uni.createFrom().item(idToken)
                // Move the synchronous verify call off the Vert.x event loop.
                .runSubscriptionOn(worker)
                .onItem().transform(this::doVerify);
    }

    /**
     * Performs the synchronous {@code FirebaseAuth.verifyIdToken} call.
     * Extracted so it can be substituted in unit tests if needed (the test
     * layer mocks the whole {@link FirebaseTokenVerifier} bean via
     * {@code @InjectMock}).
     */
    private FirebaseToken doVerify(String idToken) {
        if (!firebaseLifecycle.isInitialised()) {
            String reason = firebaseLifecycle.getInitError() != null
                    ? firebaseLifecycle.getInitError()
                    : "Firebase Admin SDK not initialised (FIREBASE_PROJECT_ID / FIREBASE_SERVICE_ACCOUNT_PATH missing)";
            throw new FirebaseAuthUnavailableException(reason);
        }
        try {
            return FirebaseAuth.getInstance().verifyIdToken(idToken);
        } catch (FirebaseAuthException e) {
            throw new FirebaseVerificationException(e.getMessage(), e);
        } catch (RuntimeException e) {
            LOG.errorf(e, "Unexpected error verifying Firebase token");
            throw new FirebaseVerificationException("verification error: " + e.getMessage(), e);
        }
    }

    /**
     * Raised when the token itself is invalid (bad signature, expired, revoked,
     * wrong audience, etc.) or when the token string is missing/blank.
     *
     * <p>The {@link FirebaseAuthFilter} maps this to {@code 401 Unauthorized}.
     */
    public static class FirebaseVerificationException extends RuntimeException {
        public FirebaseVerificationException(String message) {
            super(message);
        }
        public FirebaseVerificationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Raised when Firebase itself is not initialised (no service account in this
     * environment). Treated as a configuration error, but also surfaces as 401
     * at the HTTP layer so dev-mode without secrets never accidentally grants
     * access to protected paths.
     */
    public static class FirebaseAuthUnavailableException extends RuntimeException {
        public FirebaseAuthUnavailableException(String message) {
            super(message);
        }
    }
}