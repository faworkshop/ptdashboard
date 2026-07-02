package com.faworkshop.ptdashboard.security;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Bootstraps the Firebase Admin SDK exactly once per JVM.
 *
 * <p>Reads its configuration from two environment variables, both optional
 * (declared with empty defaults in {@code application.properties}):
 * <ul>
 *   <li>{@code FIREBASE_PROJECT_ID} — Firebase project identifier.</li>
 *   <li>{@code FIREBASE_SERVICE_ACCOUNT_PATH} — absolute or relative path
 *       to the service-account JSON file. Must NOT be committed to the repo.</li>
 * </ul>
 *
 * <p>If either is missing/blank the bean logs a clear, single warning and
 * skips initialisation. This keeps local-only flows (Phase 1 smoke tests,
 * CI without secrets) bootable while making it obvious that token
 * verification will be disabled at runtime.
 *
 * <p>When {@link FirebaseApp#getApps()} already contains an initialised
 * instance, this bean is a no-op (idempotent re-init guard).
 */
@ApplicationScoped
public class FirebaseAppLifecycle {

    private static final Logger LOG = Logger.getLogger(FirebaseAppLifecycle.class);
    private static final String DEFAULT_APP_NAME = "[DEFAULT]";

    /**
     * Firebase project ID. Resolved from {@code FIREBASE_PROJECT_ID} env var
     * via the SmallRye Config mapping in {@code application.properties}.
     * Optional — when blank the lifecycle bean skips initialisation.
     */
    @ConfigProperty(name = "firebase.project-id")
    java.util.Optional<String> projectId;

    /**
     * Path to the service-account JSON file. Resolved from
     * {@code FIREBASE_SERVICE_ACCOUNT_PATH}. Optional — when blank the
     * lifecycle bean skips initialisation.
     */
    @ConfigProperty(name = "firebase.service-account-path")
    java.util.Optional<String> serviceAccountPath;

    private volatile boolean initialised = false;
    private volatile String initError = null;

    /**
     * Initialise the Firebase Admin SDK from the configured service-account JSON.
     *
     * <p>If {@link FirebaseApp#getApps()} already contains an initialised app
     * (e.g. when running tests that initialise Firebase first), this is a no-op.
     * If either env var is blank the method logs a single warning and returns
     * without throwing — the application still boots, but
     * {@link FirebaseTokenVerifier} will refuse every token.
     */
    @PostConstruct
    void init() {
        if (!FirebaseApp.getApps().isEmpty()) {
            LOG.debug("FirebaseApp already initialised; skipping lifecycle init");
            initialised = true;
            return;
        }

        String resolvedProjectId = projectId.orElse("").trim();
        String resolvedServiceAccountPath = serviceAccountPath.orElse("").trim();

        if (resolvedProjectId.isEmpty()) {
            LOG.warn("firebase.project-id is empty — Firebase Admin SDK NOT initialised. " +
                     "Token verification will reject all requests. " +
                     "Set FIREBASE_PROJECT_ID and FIREBASE_SERVICE_ACCOUNT_PATH to enable auth.");
            return;
        }
        if (resolvedServiceAccountPath.isEmpty()) {
            LOG.warn("firebase.service-account-path is empty — Firebase Admin SDK NOT initialised. " +
                     "Set FIREBASE_SERVICE_ACCOUNT_PATH to the service-account JSON path to enable auth.");
            return;
        }

        try (InputStream in = new FileInputStream(resolvedServiceAccountPath)) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(in))
                    .setProjectId(resolvedProjectId)
                    .build();
            FirebaseApp.initializeApp(options, DEFAULT_APP_NAME);
            initialised = true;
            LOG.infof("Firebase Admin SDK initialised for project '%s'", resolvedProjectId);
        } catch (IOException e) {
            initError = e.getMessage();
            LOG.errorf(e, "Failed to initialise Firebase Admin SDK from '%s'", resolvedServiceAccountPath);
        }
    }

    /**
     * @return {@code true} iff {@link FirebaseApp} is initialised and
     *         {@link FirebaseAuth#getInstance()} can be used.
     */
    public boolean isInitialised() {
        return initialised;
    }

    /**
     * @return the last init error message, or {@code null} on success.
     */
    public String getInitError() {
        return initError;
    }
}