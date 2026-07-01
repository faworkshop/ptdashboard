# PT Dashboard

A Hong Kong public transport ETA dashboard. Users save favorite bus stops (with a specific route), green minibus stops (with route), and MTR stations, then view live arrival times on a single screen.

## Features (planned)

- **Franchised bus** — KMB/LWB, Citybus (CTB), NLB
- **Green minibus** — all GMB routes via open data
- **MTR** — heavy rail next-train times (AEL, TCL, TML, TKL, EAL, SIL, TWL, ISL, KTL, DRL)
- **Light Rail (LRT)** — MTR Light Rail arrival times at all stations
- **MTR Bus** — MTR Bus & feeder bus (K12, K65, 506, etc.)
- **User accounts** — PostgreSQL-backed favorites synced across devices
- **Route-per-stop favorites** — bus and minibus favorites require a route, not just a stop
- **Advertisement slots** — configurable ad placements on the dashboard (free tier)
- **Google Analytics (GA4)** — usage analytics with cookie consent
- **Pro subscription** — HK$18/month for unlimited favorites, ad-free, push alerts, and more

Red minibus is **not supported** (no official open-data ETA API).

This is a **proprietary, non-open-source** project. See [LICENSE](LICENSE) for terms.

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
| [Analytics](docs/analytics.md) | Google Analytics 4, events, consent, privacy |
| [API reference](docs/api.md) | REST endpoints and response shapes |
| [Upstream APIs](docs/upstream-apis.md) | Hong Kong government ETA data sources |
| [Frontend](docs/frontend.md) | Pages, components, user flows |
| [Advertisements](docs/ads.md) | Ad slot system and impression tracking |
| [Implementation phases](docs/implementation-phases.md) | Build order and deliverables |
| [Monetization](docs/monetization.md) | Pro tier, Stripe billing, feature gating |
| [Success criteria](docs/success-criteria.md) | Usability, accessibility, performance, launch checklist |

## Target users & UX principles

- **Who:** Anyone in Hong Kong who uses public transport — including children, the elderly, and people of all backgrounds and nationalities
- **Ease of use:** ≤ 3 steps to see a favorite ETA after sign-in; plain language; icon + text labels
- **Speed:** Dashboard LCP < 2.5 s; ETA refresh without full-page reload
- **Clean UI:** ETA minutes as the hero element; max 3 arrivals per card; ads visually distinct from transit info
- **Accessibility:** WCAG 2.1 AA, 44 px touch targets, 16 px+ body text, bilingual EN + zh-Hant

Full measurable criteria: [docs/success-criteria.md](docs/success-criteria.md).

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
