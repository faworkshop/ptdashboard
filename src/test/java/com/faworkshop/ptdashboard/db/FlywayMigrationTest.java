package com.faworkshop.ptdashboard.db;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Verifies that Flyway V1__init.sql applies cleanly to a fresh PostgreSQL
 * 16 database and that the schema satisfies the PTD-3 acceptance criteria.
 *
 * <p>The Testcontainer-managed PostgreSQL boots via
 * {@link FlywayMigrationTestResource} before the Quarkus app starts. The
 * migration runs at startup (`quarkus.flyway.migrate-at-start=true`); the
 * tests below then drive the schema directly through JDBC to assert that
 * the constraints behave as documented in {@code docs/data-model.md}.
 */
@QuarkusTest
@QuarkusTestResource(FlywayMigrationTestResource.class)
class FlywayMigrationTest {

    @Inject
    DataSource dataSource;

    @Inject
    Flyway flyway;

    @Test
    @DisplayName("V1__init.sql applies at boot and Flyway records it")
    void migrationAppliesAtBoot() {
        // Flyway should have applied exactly the one migration shipped in
        // this PR. If the count ever grows, the test fails — the developer
        // who adds a new migration is expected to update the count here,
        // not to blanket-allow arbitrary growth.
        assertThat("Flyway must have applied V1__init.sql",
                flyway.info().applied().length, is(1));
    }

    @Test
    @DisplayName("users and favorites tables exist after migration")
    void tablesExist() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT table_name FROM information_schema.tables " +
                     "WHERE table_schema = 'public' " +
                     "ORDER BY table_name")) {
            boolean users = false;
            boolean favorites = false;
            while (rs.next()) {
                String name = rs.getString("table_name");
                if ("users".equals(name)) users = true;
                if ("favorites".equals(name)) favorites = true;
            }
            assertThat("`users` must exist", users, is(true));
            assertThat("`favorites` must exist", favorites, is(true));
        }
    }

    @Test
    @DisplayName("users.firebase_uid and users.email have unique constraints")
    void usersUniqueness() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            assertThat("firebase_uid must have a UNIQUE constraint",
                    hasUniqueConstraint(stmt, "users", "firebase_uid"),
                    is(true));
            assertThat("email must have a UNIQUE constraint",
                    hasUniqueConstraint(stmt, "users", "email"),
                    is(true));
        }
    }

    @Test
    @DisplayName("favorites.transport_type is an ENUM with all seven operator types")
    void transportTypeEnumHasAllSevenValues() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT enumlabel FROM pg_enum " +
                     "WHERE enumtypid = 'transport_type'::regtype " +
                     "ORDER BY enumsortorder")) {
            StringBuilder labels = new StringBuilder();
            while (rs.next()) {
                if (labels.length() > 0) labels.append(",");
                labels.append(rs.getString("enumlabel"));
            }
            assertThat("transport_type ENUM must contain all operators",
                    labels.toString(),
                    equalToIgnoringCase("KMB,CTB,NLB,GMB,MTR,LRT,MTR_BUS"));
        }
    }

    @Test
    @DisplayName("duplicate (user, transport, stop, route) favorite is rejected")
    void duplicateFavoriteViolatesUniqueConstraint() throws SQLException {
        UUID userId = createUser("dup-uid@example.com");
        insertFavorite(userId, "KMB",
                "{\"stopId\":\"0B150F9A4BFF8F5F\",\"route\":\"720\"}");
        SQLException ex = assertThrows(SQLException.class,
                () -> insertFavorite(userId, "KMB",
                        "{\"stopId\":\"0B150F9A4BFF8F5F\",\"route\":\"720\"}"));
        assertThat("violation must be the favorites uniqueness index",
                ex.getMessage(), containsString("favorites_user_stop_route_uniq"));
    }

    @Test
    @DisplayName("same stop with a DIFFERENT route is allowed")
    void sameStopDifferentRouteIsAllowed() throws SQLException {
        UUID userId = createUser("multi-uid@example.com");
        insertFavorite(userId, "KMB",
                "{\"stopId\":\"0B150F9A4BFF8F5F\",\"route\":\"720\"}");
        // Different route on the same stop must succeed — only a full
        // (user, transport, stop, route) collision is rejected.
        assertDoesNotThrow(() -> insertFavorite(userId, "KMB",
                "{\"stopId\":\"0B150F9A4BFF8F5F\",\"route\":\"721\"}"));
    }

    @Test
    @DisplayName("MTR favorites allow `config` without a route field")
    void mtrFavoritesAllowConfigWithoutRoute() throws SQLException {
        UUID userId = createUser("mtr-uid@example.com");
        // MTR heavy-rail favorites only carry line + station — no route.
        // Multiple MTR favorites for the same station must succeed because
        // the generated `route_key` is NULL (PostgreSQL considers NULLs
        // distinct under the UNIQUE btree).
        assertDoesNotThrow(() -> insertFavorite(userId, "MTR",
                "{\"line\":\"TWL\",\"station\":\"ADM\",\"direction\":\"UP\"}"));
        assertDoesNotThrow(() -> insertFavorite(userId, "MTR",
                "{\"line\":\"TWL\",\"station\":\"ADM\",\"platform\":\"1\"}"));
        // A second MTR favorite with a route provided must still work and
        // matches a different uniqueness key.
        assertDoesNotThrow(() -> insertFavorite(userId, "MTR",
                "{\"line\":\"TWL\",\"station\":\"ADM\",\"route\":\"extra\"}"));
    }

    @Test
    @DisplayName("favorites.config is JSONB and sort_order defaults to 0")
    void configIsJsonbAndSortOrderDefaultsToZero() throws SQLException {
        UUID userId = createUser("defaults-uid@example.com");
        // Insert without specifying sort_order — the column DEFAULT 0 must
        // kick in. We also confirm the JSONB column accepted an object
        // value (not text) by inserting an actual JSON object.
        UUID favId = UUID.randomUUID();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO favorites " +
                     "(id, user_id, transport_type, config) " +
                     "VALUES (?, ?, ?::transport_type, ?::jsonb)")) {
            ps.setObject(1, favId);
            ps.setObject(2, userId);
            ps.setString(3, "CTB");
            ps.setString(4, "{\"stopId\":\"001313\",\"route\":\"720\"}");
            ps.executeUpdate();
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT sort_order, jsonb_typeof(config) AS config_type " +
                     "FROM favorites WHERE id = ?")) {
            ps.setObject(1, favId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat("row must exist", rs.next(), is(true));
                assertThat("sort_order default must be 0",
                        rs.getInt("sort_order"), is(0));
                assertThat("config must be JSONB object",
                        rs.getString("config_type"), is("object"));
            }
        }
    }

    // --- helpers ----------------------------------------------------------

    private boolean hasUniqueConstraint(Statement stmt, String table, String column)
            throws SQLException {
        try (ResultSet rs = stmt.executeQuery(
                "SELECT 1 FROM pg_constraint c " +
                "JOIN pg_class t ON t.oid = c.conrelid " +
                "JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = ANY(c.conkey) " +
                "WHERE t.relname = '" + table + "' " +
                "AND a.attname = '" + column + "' " +
                "AND c.contype IN ('u','p')")) {
            return rs.next();
        }
    }

    private UUID createUser(String email) throws SQLException {
        UUID id = UUID.randomUUID();
        String uid = "uid-" + id;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO users (id, firebase_uid, email) " +
                     "VALUES (?, ?, ?)")) {
            ps.setObject(1, id);
            ps.setString(2, uid);
            ps.setString(3, email);
            ps.executeUpdate();
        }
        assertThat("inserted user must have a UUID id", id, is(notNullValue()));
        return id;
    }

    private void insertFavorite(UUID userId, String transportType, String configJson)
            throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO favorites " +
                     "(id, user_id, transport_type, config) " +
                     "VALUES (?, ?, ?::transport_type, ?::jsonb)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, userId);
            ps.setString(3, transportType);
            ps.setString(4, configJson);
            ps.executeUpdate();
        }
    }
}
