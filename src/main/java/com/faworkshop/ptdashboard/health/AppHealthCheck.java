package com.faworkshop.ptdashboard.health;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

/**
 * Application liveness probe.
 *
 * <p>Marked {@link Liveness} (not {@code @Readiness}) because liveness is
 * DB-independent — readiness would require a live PostgreSQL connection which
 * is not available in the Phase 1 scaffold (the {@code V1__init.sql} migration
 * is delivered in PTD-3).
 *
 * <p>Exposed at {@code GET /q/health/live} by the {@code quarkus-smallrye-health}
 * extension.
 */
@ApplicationScoped
@Liveness
public class AppHealthCheck implements org.eclipse.microprofile.health.HealthCheck {

    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.named("PTDashboard").up().build();
    }
}