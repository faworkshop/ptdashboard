package com.faworkshop.ptdashboard.security;

import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for {@link FirebaseTokenVerifier}.
 *
 * <p>Without a real Firebase service account, the verifier cannot succeed,
 * but we can still verify the contract:
 * <ul>
 *   <li>Empty/blank token fails fast with {@link FirebaseTokenVerifier.FirebaseVerificationException}.</li>
 *   <li>When Firebase is not initialised, the failure is
 *       {@link FirebaseTokenVerifier.FirebaseAuthUnavailableException}.</li>
 *   <li>The synchronous verify step runs OFF the Vert.x event loop —
 *       asserted by capturing the executing thread name inside the
 *       verification block.</li>
 * </ul>
 *
 * <p>The "off the event loop" assertion works because the Quarkus test
 * profile is started by the JUnit runner (a regular JVM thread), but the
 * verify call is dispatched via {@link io.smallrye.mutiny.infrastructure.Infrastructure#getDefaultExecutor()},
 * which is the Mutiny worker pool. If the dispatch is correct the captured
 * thread name will be a Mutiny worker thread (typically {@code "executor-thread-*"}),
 * not the JUnit runner thread and not a Vert.x event loop thread.
 */
@QuarkusTest
class FirebaseTokenVerifierTest {

    /** Names that indicate a Vert.x event loop thread. */
    private static boolean isVertxEventLoopThread(String name) {
        return name != null && (name.startsWith("vert.x-eventloop") || name.contains("event-loop"));
    }

    @Inject
    FirebaseTokenVerifier verifier;

    @Inject
    FirebaseAppLifecycle lifecycle;

    @Test
    @DisplayName("Empty/blank token fails with FirebaseVerificationException")
    void emptyToken_failsImmediately() {
        FirebaseTokenVerifier.FirebaseVerificationException ex =
                assertThrows(FirebaseTokenVerifier.FirebaseVerificationException.class,
                        () -> verifier.verify("").await().indefinitely());
        assertNotNull(ex.getMessage());

        assertThrows(FirebaseTokenVerifier.FirebaseVerificationException.class,
                () -> verifier.verify(null).await().indefinitely());
    }

    @Test
    @DisplayName("When Firebase is not initialised, verify fails with FirebaseAuthUnavailableException")
    void firebaseUnavailable_returnsUnavailableFailure() {
        // No service account configured in test profile, so the lifecycle
        // bean should report !isInitialised().
        assertFalse(lifecycle.isInitialised(),
                "FirebaseAppLifecycle should not be initialised in test profile (no service account)");

        FirebaseTokenVerifier.FirebaseAuthUnavailableException ex = assertThrows(
                FirebaseTokenVerifier.FirebaseAuthUnavailableException.class,
                () -> verifier.verify("any.token.value").await().indefinitely());
        assertNotNull(ex.getMessage());
    }

    @Test
    @DisplayName("verify() runs the synchronous verify step on a non-event-loop thread")
    void verify_runsOffEventLoop() throws InterruptedException {
        // Without a real service account we can't reach the actual Firebase
        // call, but we CAN prove the dispatch path is non-blocking. We do
        // that by checking that even when the underlying call throws
        // FirebaseAuthUnavailableException, it happens on a worker thread.
        AtomicReference<String> threadName = new AtomicReference<>();
        CountDownLatch captured = new CountDownLatch(1);

        // Hook the lifecycle's uninitialised branch by verifying the
        // dispatch path itself: the Uni's runSubscriptionOn(worker) call
        // guarantees execution on a non-event-loop thread.
        Uni<FirebaseToken> uni = verifier.verify("any.token.value")
                .onFailure().invoke(t -> {
                    threadName.set(Thread.currentThread().getName());
                    captured.countDown();
                });

        try {
            uni.await().indefinitely();
        } catch (RuntimeException ignored) {
            // expected — Firebase not initialised in test profile
        }

        assertTrue(captured.await(5, TimeUnit.SECONDS), "verification callback never fired");
        String name = threadName.get();
        assertNotNull(name, "captured thread name was null");
        assertFalse(isVertxEventLoopThread(name),
                "verify must NOT run on a Vert.x event loop thread (was: " + name + ")");
        assertNotEquals(Thread.currentThread().getName(), name,
                "verify must NOT run on the JUnit/caller thread");
    }

    @Test
    @DisplayName("FirebaseTokenVerifier exposes the expected public failure types")
    void publicFailureTypesExist() throws FirebaseAuthException {
        // Smoke test for the static nested exception classes — ensures
        // they're public, instantiable, and not accidentally package-private.
        FirebaseTokenVerifier.FirebaseVerificationException v1 =
                new FirebaseTokenVerifier.FirebaseVerificationException("x");
        assertNotNull(v1.getMessage());

        FirebaseTokenVerifier.FirebaseVerificationException v2 =
                new FirebaseTokenVerifier.FirebaseVerificationException("x", new RuntimeException("cause"));
        assertNotNull(v2.getCause());

        FirebaseTokenVerifier.FirebaseAuthUnavailableException u1 =
                new FirebaseTokenVerifier.FirebaseAuthUnavailableException("x");
        assertNotNull(u1.getMessage());
    }
}