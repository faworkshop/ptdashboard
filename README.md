# PT Dashboard

A Hong Kong public transport ETA dashboard. Users save favorite bus stops (with a specific route), green minibus stops (with route), and MTR stations, then view live arrival times on a single screen.

## Features (planned)

- **Franchised bus** — KMB/LWB, Citybus (CTB), NLB
- **Green minibus** — all GMB routes via open data
- **MTR** — heavy rail next-train times (AEL, TCL, TML, TKL, EAL, SIL, TWL, ISL, KTL, DRL)
- **User accounts** — PostgreSQL-backed favorites synced across devices
- **Route-per-stop favorites** — bus and minibus favorites require a route, not just a stop
- **Advertisement slots** — configurable ad placements on the dashboard

Red minibus is **not supported** (no official open-data ETA API).

## Tech stack

| Layer | Technology |
|-------|------------|
| Backend | Quarkus 3.x (Java 21), fully reactive (Mutiny, Hibernate Reactive) |
| Database | PostgreSQL |
| Auth | Firebase Authentication (client SDK + Admin SDK token verification) |
| Frontend | React + TypeScript (Quarkus Web Bundler) |
| Styling | Tailwind CSS 4 |

## Documentation

| Document | Description |
|----------|-------------|
| [Architecture](docs/architecture.md) | System design, reactive stack, caching |
| [Data model](docs/data-model.md) | Entities, favorites config, validation rules |
| [Authentication](docs/auth.md) | Firebase Auth flow, token verification, admin claims |
| [API reference](docs/api.md) | REST endpoints and response shapes |
| [Upstream APIs](docs/upstream-apis.md) | Hong Kong government ETA data sources |
| [Frontend](docs/frontend.md) | Pages, components, user flows |
| [Advertisements](docs/ads.md) | Ad slot system and impression tracking |
| [Implementation phases](docs/implementation-phases.md) | Build order and deliverables |

## Local development (once scaffolded)

```bash
docker compose up -d    # PostgreSQL on :5432
./mvnw quarkus:dev      # App on :8080
```

## Scaffold command

```bash
quarkus create app com.faworkshop.ptdashboard:pt-dashboard:1.0.0-SNAPSHOT \
  --extension=rest-jackson,hibernate-reactive-panache,reactive-pg-client,flyway,security,caffeine,rest-client-reactive-jackson
# Add manually: com.google.firebase:firebase-admin
quarkus ext add io.quarkiverse.web-bundler:quarkus-web-bundler
```

## Status

This repository is in the **planning / documentation** stage. Application code has not been implemented yet. See [implementation phases](docs/implementation-phases.md) for the build roadmap.
