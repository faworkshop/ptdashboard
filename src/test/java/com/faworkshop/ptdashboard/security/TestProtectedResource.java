package com.faworkshop.ptdashboard.security;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Minimal test-only JAX-RS resource used by {@link FirebaseAuthFilterTest} to
 * exercise the Firebase authentication filter on a protected endpoint.
 *
 * <p>This resource lives in {@code src/test/java} — it is NOT part of the
 * production build. PTD-5 ({@code POST /auth/sync}) and PTD-6
 * ({@code /api/v1/favorites/*}) are the real protected endpoints; once they
 * land this fixture can be deleted.
 */
@Path("/test/protected")
public class TestProtectedResource {

    @Inject
    SecurityIdentity identity;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response whoAmI() {
        String uid = identity.getPrincipal() != null ? identity.getPrincipal().getName() : null;
        return Response.ok().entity("{\"uid\":\"" + uid + "\"}").build();
    }
}