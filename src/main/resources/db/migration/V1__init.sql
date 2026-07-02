-- =============================================================================
-- PT Dashboard — Flyway V1__init.sql
-- Phase 1 schema foundation (PTD-3).
--
-- Builds the `users` and `favorites` tables. Panache entities / repositories
-- land in PTD-5 (Auth) and PTD-6 (Favorites CRUD); this migration only owns
-- the schema.
--
-- Notes:
--   * All identifiers use snake_case to match the data-model doc and to keep
--     generated Panache column attributes clean.
--   * `uuid` columns are backed by PostgreSQL `uuid` (not `varchar`) so the
--     Flyway-managed JDBC datasource and the reactive-pg-client used by
--     PTD-5 / PTD-6 agree on the column type.
--   * `favorites.transport_type` is stored as a PostgreSQL ENUM type named
--     `transport_type` — the seven operator values match `docs/data-model.md`.
--   * Duplicate-favorite protection uses STORED generated columns that pull
--     `stopId` / `stop` / `station` / `stationId` (`stop_id`) and
--     `route` / `routeId` / `routeNo` / `routeName` (`route_key`) out of the
--     JSONB `config`. A standard UNIQUE index on
--     `(user_id, transport_type, stop_id, route_key)` then enforces the rule
--     at the database layer (see ticket "Open Question / option 1").
--   * MTR heavy-rail favorites do not have a `route` field, so for those
--     rows the generated `route_key` is NULL and any number of MTR favorites
--     for the same (user, station) is allowed as long as the route-key slot
--     is empty — see ticket acceptance criterion #6.
-- =============================================================================

-- ---- ENUM: transport_type ------------------------------------------------
CREATE TYPE transport_type AS ENUM (
    'KMB',
    'CTB',
    'NLB',
    'GMB',
    'MTR',
    'LRT',
    'MTR_BUS'
);

-- ---- users --------------------------------------------------------------
CREATE TABLE users (
    id              UUID         PRIMARY KEY,
    firebase_uid    VARCHAR(128) NOT NULL UNIQUE,
    email           VARCHAR(320) NOT NULL UNIQUE,
    display_name    VARCHAR(255),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---- favorites ----------------------------------------------------------
-- `config` is JSONB so future transport operators can add fields without an
-- immediate migration. `sort_order` defaults to 0; the application is free
-- to renumber later (PTD-6 plans to expose ordering in the API).
CREATE TABLE favorites (
    id              UUID         PRIMARY KEY,
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    transport_type  transport_type NOT NULL,
    label           VARCHAR(255),
    config          JSONB        NOT NULL,
    sort_order      INTEGER      NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---- Generated identifier columns ---------------------------------------
-- `stop_id` and `route_key` are extracted from the polymorphic `config`
-- payload. They are STORED so the UNIQUE index below can use a plain btree.
--
-- COALESCE walks the documented field names in priority order
-- (`stopId` → `busStopId` → `station` → `stationId` per data-model.md).
-- Any result that is not a non-empty string is normalized to NULL so the
-- UNIQUE constraint treats "missing" the same as "empty" and doesn't blow
-- up on numeric values such as LRT's `stationId: 600`.
CREATE OR REPLACE FUNCTION favorites_stop_id(config JSONB) RETURNS TEXT
LANGUAGE sql IMMUTABLE AS $$
    SELECT NULLIF(
        COALESCE(
            NULLIF(config ->> 'stopId', ''),
            NULLIF(config ->> 'busStopId', ''),
            NULLIF(config ->> 'station', ''),
            NULLIF(config ->> 'stationId', '')
        ),
        ''
    );
$$;

CREATE OR REPLACE FUNCTION favorites_route_key(config JSONB) RETURNS TEXT
LANGUAGE sql IMMUTABLE AS $$
    SELECT NULLIF(
        COALESCE(
            NULLIF(config ->> 'route', ''),
            NULLIF(config ->> 'routeId', ''),
            NULLIF(config ->> 'routeNo', ''),
            NULLIF(config ->> 'routeName', '')
        ),
        ''
    );
$$;

ALTER TABLE favorites
    ADD COLUMN stop_id   TEXT GENERATED ALWAYS AS (favorites_stop_id(config))   STORED,
    ADD COLUMN route_key TEXT GENERATED ALWAYS AS (favorites_route_key(config)) STORED;

-- Same physical stop may appear multiple times with DIFFERENT routes. This
-- constraint matches the data-model doc: UNIQUE (user_id, transport_type,
-- stop_id, route_key). For MTR (no route) the route_key is NULL — Postgres
-- treats NULLs as distinct under standard btree unique semantics, which is
-- the desired behaviour for AC #6.
CREATE UNIQUE INDEX favorites_user_stop_route_uniq
    ON favorites (user_id, transport_type, stop_id, route_key);

CREATE INDEX favorites_user_id_idx ON favorites (user_id);

-- =============================================================================
-- End of V1__init.sql
-- =============================================================================
