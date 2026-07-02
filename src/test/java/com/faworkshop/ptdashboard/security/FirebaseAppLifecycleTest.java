package com.faworkshop.ptdashboard.security;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests {@link FirebaseAppLifecycle} in the test profile where both
 * Firebase env vars are unset.
 *
 * <p>The lifecycle bean should:
 * <ul>
 *   <li>NOT throw if either env var is blank.</li>
 *   <li>Report {@code isInitialised() == false}.</li>
 *   <li>NOT expose a non-null {@code initError} for the "blank env var"
 *       branch (it only records an error if the SDK init attempt fails,
 *       e.g. malformed service-account JSON).</li>
 * </ul>
 *
 * <p>A positive-path test (successful init from a real service-account JSON)
 * is intentionally omitted here — it requires a file fixture on disk and
 * is not safely mockable through {@code @ConfigProperty} in
 * {@code @QuarkusTest}. That coverage will land with the CI integration
 * story (Firebase Auth Emulator) tracked under
 * {@code docs/auth.md} §"Risks / Open Questions".
 */
@QuarkusTest
class FirebaseAppLifecycleTest {

    @Inject
    FirebaseAppLifecycle lifecycle;

    @Test
    @DisplayName("Lifecycle bean is created and exposes its API")
    void beanIsInjectable() {
        assertNotNull(lifecycle);
    }

    @Test
    @DisplayName("With blank env vars, Firebase is NOT initialised and no initError is recorded")
    void blankEnvVars_lifecycleNotInitialised() {
        // Test profile intentionally leaves FIREBASE_PROJECT_ID and
        // FIREBASE_SERVICE_ACCOUNT_PATH unset (see application.properties).
        assertFalse(lifecycle.isInitialised(),
                "Firebase must NOT be initialised when env vars are blank");
        assertNull(lifecycle.getInitError(),
                "Blank-env-var branch must not record an initError");
    }
}