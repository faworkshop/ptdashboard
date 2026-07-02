package com.faworkshop.ptdashboard.health;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * Smoke test that boots the Quarkus app in test mode and verifies the
 * liveness endpoint returns {@code UP}.
 *
 * <p>Phase 1 only requires a single boot + {@code /q/health/live} check;
 * richer integration tests will be added per-ticket in later phases.
 */
@QuarkusTest
class HealthCheckSmokeTest {

    @Test
    void livenessEndpointReturnsUp() {
        given()
                .when().get("/q/health/live")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"))
                .body("checks[0].name", equalTo("PTDashboard"))
                .body("checks[0].status", equalTo("UP"));
    }
}