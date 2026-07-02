package com.faworkshop.ptdashboard.security;

import com.google.firebase.auth.FirebaseToken;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Integration test for {@link FirebaseAuthFilter}.
 *
 * <p>Uses {@link InjectMock} to substitute {@link FirebaseTokenVerifier} with
 * a Mockito stub so the test does not need a real Firebase service account.
 * The {@link FirebaseAppLifecycle} bean will log a warning at boot (no
 * service account configured in test profile) — that's expected and does
 * NOT prevent app start; the filter still runs and the mock verifier takes
 * over.
 *
 * <p>Test profile intentionally leaves both Firebase env vars unset so the
 * filter's behaviour can be exercised in CI without secrets.
 */
@QuarkusTest
class FirebaseAuthFilterTest {

    @InjectMock
    FirebaseTokenVerifier verifier;

    private FirebaseToken mockToken(String uid, String email) {
        FirebaseToken token = Mockito.mock(FirebaseToken.class);
        when(token.getUid()).thenReturn(uid);
        when(token.getEmail()).thenReturn(email);
        return token;
    }

    @BeforeEach
    void resetMocks() {
        Mockito.reset(verifier);
    }

    @Test
    @DisplayName("Protected endpoint without Authorization header returns 401")
    void protectedPath_noHeader_returns401() {
        given()
                .when().get("/test/protected")
                .then()
                .statusCode(401)
                .header("WWW-Authenticate", equalTo("Bearer error=\"invalid_token\""))
                .body("error", equalTo("unauthorized"))
                .body("reason", equalTo("missing_bearer_token"));
    }

    @Test
    @DisplayName("Protected endpoint with non-Bearer Authorization header returns 401")
    void protectedPath_nonBearerHeader_returns401() {
        given()
                .header("Authorization", "Basic dXNlcjpwYXNz")
                .when().get("/test/protected")
                .then()
                .statusCode(401)
                .body("reason", equalTo("missing_bearer_token"));
    }

    @Test
    @DisplayName("Protected endpoint with Bearer prefix but no token returns 401")
    void protectedPath_emptyBearerToken_returns401() {
        // "Bearer" alone (no space, no token) is rejected as "missing_bearer_token".
        // HTTP wire-trimming prevents sending a header that is literally "Bearer "
        // followed by spaces only — both representations collapse to "missing".
        given()
                .header("Authorization", "Bearer")
                .when().get("/test/protected")
                .then()
                .statusCode(401)
                .body("reason", equalTo("missing_bearer_token"));
    }

    @Test
    @DisplayName("Filter's extractBearerToken helper correctly classifies inputs")
    void extractBearerToken_classifiesInputs() {
        // null input → no token at all
        org.junit.jupiter.api.Assertions.assertNull(FirebaseAuthFilter.extractBearerToken(null));
        // Non-Bearer scheme → no token
        org.junit.jupiter.api.Assertions.assertNull(FirebaseAuthFilter.extractBearerToken("Basic dXNlcjpwYXNz"));
        // Bare "Bearer" (no trailing space) → no token
        org.junit.jupiter.api.Assertions.assertNull(FirebaseAuthFilter.extractBearerToken("Bearer"));
        // Bearer with token → returns token
        org.junit.jupiter.api.Assertions.assertEquals("abc.def.ghi",
                FirebaseAuthFilter.extractBearerToken("Bearer abc.def.ghi"));
        // Lowercase scheme works (RFC 7235 says case-insensitive)
        org.junit.jupiter.api.Assertions.assertEquals("xyz",
                FirebaseAuthFilter.extractBearerToken("bearer xyz"));
        // Whitespace around the scheme and token is stripped
        org.junit.jupiter.api.Assertions.assertEquals("tok",
                FirebaseAuthFilter.extractBearerToken("  Bearer   tok  "));
    }

    @Test
    @DisplayName("Protected endpoint with invalid token returns 401")
    void protectedPath_invalidToken_returns401() {
        when(verifier.verify(anyString()))
                .thenReturn(Uni.createFrom().failure(
                        new FirebaseTokenVerifier.FirebaseVerificationException("bad signature")));

        given()
                .header("Authorization", "Bearer not-a-real-token")
                .when().get("/test/protected")
                .then()
                .statusCode(401)
                .body("reason", equalTo("invalid_token"));
    }

    @Test
    @DisplayName("Protected endpoint with expired token returns 401")
    void protectedPath_expiredToken_returns401() {
        when(verifier.verify(anyString()))
                .thenReturn(Uni.createFrom().failure(
                        new FirebaseTokenVerifier.FirebaseVerificationException("TOKEN_EXPIRED")));

        given()
                .header("Authorization", "Bearer expired.token.value")
                .when().get("/test/protected")
                .then()
                .statusCode(401)
                .body("reason", equalTo("invalid_token"));
    }

    @Test
    @DisplayName("Protected endpoint when Firebase unavailable returns 401")
    void protectedPath_firebaseUnavailable_returns401() {
        when(verifier.verify(anyString()))
                .thenReturn(Uni.createFrom().failure(
                        new FirebaseTokenVerifier.FirebaseAuthUnavailableException(
                                "Firebase Admin SDK not initialised")));

        given()
                .header("Authorization", "Bearer any.token.here")
                .when().get("/test/protected")
                .then()
                .statusCode(401)
                .body("reason", equalTo("auth_unavailable"));
    }

    @Test
    @DisplayName("Protected endpoint with valid token returns 200 and echoes uid")
    void protectedPath_validToken_returns200WithUid() {
        FirebaseToken token = mockToken("firebase-uid-abc123", "user@example.com");
        when(verifier.verify("good.token.value")).thenReturn(Uni.createFrom().item(token));

        given()
                .header("Authorization", "Bearer good.token.value")
                .when().get("/test/protected")
                .then()
                .statusCode(200)
                .body("uid", equalTo("firebase-uid-abc123"));
    }

    @Test
    @DisplayName("Public /q/health/live path is accessible without any token")
    void publicHealthPath_noHeader_returns200() {
        given()
                .when().get("/q/health/live")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"))
                .body("checks[0].name", notNullValue());
    }

    @Test
    @DisplayName("Public /q/health/live path is accessible even with an invalid token (filter short-circuits)")
    void publicHealthPath_invalidToken_returns200() {
        // The filter should not even invoke the verifier for public paths,
        // so an "invalid token" header on a public path must NOT cause 401.
        when(verifier.verify(anyString()))
                .thenReturn(Uni.createFrom().failure(
                        new FirebaseTokenVerifier.FirebaseVerificationException("would-be-bad")));

        given()
                .header("Authorization", "Bearer would-be-bad")
                .when().get("/q/health/live")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }
}