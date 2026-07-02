package com.faworkshop.ptdashboard.db;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/**
 * Test resource that boots a PostgreSQL 16 instance for the duration of the
 * test suite and points the Quarkus JDBC datasource (and Flyway) at it.
 *
 * <p>Boot order (first successful strategy wins):
 * <ol>
 *   <li><b>Testcontainers</b> if Docker is reachable from the JVM (CI runners
 *       and most Linux dev boxes). This is the preferred path because the
 *       container is auto-removed.</li>
 *   <li><b>{@code docker run} subprocess</b> as a fallback for hosts where
 *       the JVM can't talk to Docker directly (e.g. macOS Docker Desktop's
 *       per-user CLI socket). The container is torn down on {@link #stop()}.</li>
 *   <li><b>{@code PTDASHBOARD_TEST_POSTGRES_URL} env var</b> — a pre-running
 *       Postgres (developer service container, sidecar, etc.). Treated as
 *       a last-resort escape hatch.</li>
 * </ol>
 *
 * <p>The container is started in {@link #start()} and stopped in
 * {@link #stop()}; Quarkus calls those once per test class lifecycle.
 */
public class FlywayMigrationTestResource
        implements QuarkusTestResourceLifecycleManager {

    private static final DockerImageName POSTGRES_IMAGE =
            DockerImageName.parse("postgres:16-alpine");

    @SuppressWarnings("resource") // container is closed in stop()
    private static final PostgreSQLContainer<?> TESTCONTAINERS_POSTGRES =
            new PostgreSQLContainer<>(POSTGRES_IMAGE)
                    .withDatabaseName("ptdashboard_test")
                    .withUsername("ptdashboard_test")
                    .withPassword("ptdashboard_test")
                    .withReuse(false);

    /** Mode selected in {@link #start()}; affects {@link #stop()}. */
    private static BootMode mode = BootMode.NONE;

    /** Populated when {@link BootMode#DOCKER_CLI} is used. */
    private static String dockerCliContainerId;

    private enum BootMode { NONE, TESTCONTAINERS, DOCKER_CLI, ENV_URL }

    // --- public accessor for the test (used by the disabled-enable check) --

    static BootMode bootMode() {
        return mode;
    }

    // --- lifecycle --------------------------------------------------------

    @Override
    public Map<String, String> start() {
        Map<String, String> overrides = new HashMap<>();

        // Strategy 1: Testcontainers (CI runners, Linux dev boxes).
        if (dockerReachableFromJvm()) {
            try {
                if (!TESTCONTAINERS_POSTGRES.isRunning()) {
                    TESTCONTAINERS_POSTGRES.start();
                }
                mode = BootMode.TESTCONTAINERS;
                applyOverrides(overrides, TESTCONTAINERS_POSTGRES.getJdbcUrl(),
                        TESTCONTAINERS_POSTGRES.getUsername(),
                        TESTCONTAINERS_POSTGRES.getPassword());
                return overrides;
            } catch (Throwable t) {
                System.err.println("[FlywayMigrationTestResource] Testcontainers failed: "
                        + t.getMessage() + " — falling back to docker CLI");
            }
        }

        // Strategy 2: docker run subprocess (macOS Docker Desktop, etc.).
        try {
            String[] urlParts = startPostgresViaDockerCli();
            mode = BootMode.DOCKER_CLI;
            applyOverrides(overrides, urlParts[0], urlParts[1], urlParts[2]);
            return overrides;
        } catch (Throwable t) {
            System.err.println("[FlywayMigrationTestResource] docker CLI fallback failed: "
                    + t.getMessage());
        }

        // Strategy 3: env-var escape hatch.
        String envUrl = System.getenv("PTDASHBOARD_TEST_POSTGRES_URL");
        if (envUrl != null && !envUrl.isBlank()) {
            mode = BootMode.ENV_URL;
            applyOverrides(overrides, envUrl,
                    System.getenv().getOrDefault("PTDASHBOARD_TEST_POSTGRES_USER", "ptdashboard"),
                    System.getenv().getOrDefault("PTDASHBOARD_TEST_POSTGRES_PASSWORD", "ptdashboard"));
            return overrides;
        }

        throw new IllegalStateException(
                "No PostgreSQL is available for the Flyway migration test. " +
                "Configure Docker (Testcontainers / docker CLI) or set " +
                "PTDASHBOARD_TEST_POSTGRES_URL to a reachable database.");
    }

    @Override
    public void stop() {
        switch (mode) {
            case TESTCONTAINERS -> {
                if (TESTCONTAINERS_POSTGRES.isRunning()) {
                    TESTCONTAINERS_POSTGRES.stop();
                }
            }
            case DOCKER_CLI -> {
                if (dockerCliContainerId != null) {
                    runCommand("docker", "rm", "-f", dockerCliContainerId);
                    dockerCliContainerId = null;
                }
            }
            case ENV_URL, NONE -> { /* nothing to do */ }
        }
        mode = BootMode.NONE;
    }

    // --- helpers ----------------------------------------------------------

    private static void applyOverrides(Map<String, String> overrides, String jdbcUrl,
                                       String user, String pass) {
        overrides.put("quarkus.datasource.jdbc.url", jdbcUrl);
        overrides.put("quarkus.datasource.username", user);
        overrides.put("quarkus.datasource.password", pass);
        overrides.put("quarkus.flyway.migrate-at-start", "true");
        overrides.put("quarkus.flyway.clean-at-start", "false");
        overrides.put("quarkus.flyway.baseline-on-migrate", "true");
        overrides.put("quarkus.hibernate-orm.database.generation", "none");
        // Empty reactive URL so reactive-pg-client does not try to
        // connect at boot — this test class only exercises Flyway + JDBC.
        overrides.put("quarkus.datasource.reactive.url", "");
    }

    /**
     * Heuristic: probe Docker by issuing {@code docker info} through the
     * Docker CLI. This is more permissive than the Testcontainers probe,
     * which only succeeds when the JVM can talk to the daemon socket
     * directly. On macOS Docker Desktop the {@code docker} CLI works even
     * when {@code /var/run/docker.sock} returns 400 to non-CLI clients.
     */
    private static boolean dockerReachableFromJvm() {
        try {
            Process p = new ProcessBuilder("docker", "info").redirectErrorStream(true).start();
            boolean finished = p.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    /**
     * Boots a Postgres 16 container via the {@code docker run} CLI and
     * returns a {@code [jdbcUrl, user, password]} triple.
     */
    private static String[] startPostgresViaDockerCli() throws IOException, InterruptedException {
        String containerId = null;
        // Try a few times in case a previous run left a host port in
        // TIME_WAIT. Each attempt picks a fresh port from a high range to
        // minimise collisions with anything else on the host.
        for (int attempt = 0; attempt < 5; attempt++) {
            int hostPort = pickFreePort();
            String[] runArgs = {
                    "docker", "run", "-d", "--rm",
                    "-p", "127.0.0.1:" + hostPort + ":5432",
                    "-e", "POSTGRES_USER=ptdashboard_test",
                    "-e", "POSTGRES_PASSWORD=ptdashboard_test",
                    "-e", "POSTGRES_DB=ptdashboard_test",
                    "postgres:16-alpine"
            };
            try {
                containerId = runCommandAndCapture(runArgs).trim();
            } catch (IllegalStateException e) {
                if (e.getMessage() != null && e.getMessage().contains("address already in use")
                        && attempt < 4) {
                    Thread.sleep(250L * (attempt + 1));
                    continue;
                }
                throw e;
            }
            if (containerId.isEmpty()) {
                throw new IllegalStateException("docker run produced no container id");
            }

            // Wait for the container to accept connections. Polling with
            // `docker exec pg_isready` is the standard pattern.
            long deadline = System.currentTimeMillis() + 60_000;
            while (System.currentTimeMillis() < deadline) {
                Process p = new ProcessBuilder("docker", "exec", containerId,
                        "pg_isready", "-U", "ptdashboard_test").redirectErrorStream(true).start();
                if (p.waitFor(2, TimeUnit.SECONDS) && p.exitValue() == 0) {
                    dockerCliContainerId = containerId;
                    String jdbcUrl = "jdbc:postgresql://127.0.0.1:" + hostPort + "/ptdashboard_test";
                    return new String[]{jdbcUrl, "ptdashboard_test", "ptdashboard_test"};
                }
                Thread.sleep(500);
            }
            throw new IllegalStateException("Postgres container did not become ready within 60s");
        }
        throw new IllegalStateException("Exhausted retries binding a free host port for Postgres");
    }

    private static int pickFreePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private static String runCommandAndCapture(String... args) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(args).redirectErrorStream(true).start();
        StringBuilder out = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                out.append(line).append('\n');
            }
        }
        if (!p.waitFor(120, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IllegalStateException("Timed out: " + String.join(" ", args));
        }
        if (p.exitValue() != 0) {
            throw new IllegalStateException(
                    "Command failed (" + p.exitValue() + "): " + String.join(" ", args)
                    + "\n" + out);
        }
        return out.toString();
    }

    private static void runCommand(String... args) {
        try {
            runCommandAndCapture(args);
        } catch (Exception e) {
            // Best-effort cleanup — don't fail the test run over a
            // leftover container; the OS / Docker daemon will GC it.
            System.err.println("[FlywayMigrationTestResource] cleanup failed: " + e.getMessage());
        }
    }
}
