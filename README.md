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
| [Cursor skill: Linear tickets](.cursor/skills/create-linear-tickets/SKILL.md) | Create Linear backlog from plan phases (AI agent dev & test) |

## Target users & UX principles

- **Who:** Anyone in Hong Kong who uses public transport — including children, the elderly, and people of all backgrounds and nationalities
- **Ease of use:** ≤ 3 steps to see a favorite ETA after sign-in; plain language; icon + text labels
- **Speed:** Dashboard LCP < 2.5 s; ETA refresh without full-page reload
- **Clean UI:** ETA minutes as the hero element; max 3 arrivals per card; ads visually distinct from transit info
- **Accessibility:** WCAG 2.1 AA, 44 px touch targets, 16 px+ body text, bilingual EN + zh-Hant

Full measurable criteria: [docs/success-criteria.md](docs/success-criteria.md).

## Local development (Phase 1 backend)

The Phase 1 scaffold is a reactive Quarkus 3 application. Docker / PostgreSQL
are not required yet — migrations land in [PTD-3](docs/implementation-phases.md).

```bash
# From the repo root
export JAVA_HOME=/opt/homebrew/opt/openjdk@21   # or any Java 21 install
./mvnw quarkus:dev                               # App on :8081
```

Then verify the app is alive:

```bash
curl -s http://localhost:8081/q/health/live | jq .
# => { "status": "UP", "checks": [ { "name": "PTDashboard", "status": "UP" } ] }
```

Run the test suite:

```bash
./mvnw test
```

### Configuration

All runtime config is supplied via environment variables — no secrets are
committed to the repo. The defaults in `src/main/resources/application.properties`
assume a local PostgreSQL on `:5432` (not required for PTD-2; required in PTD-3).

| Env var | Purpose | Default |
|---------|---------|---------|
| `HTTP_PORT` | App port | `8081` (8080 is occupied by the SDLC webhook server on this host) |
| `QUARKUS_DATASOURCE_USERNAME` | PostgreSQL user | `ptdashboard` |
| `QUARKUS_DATASOURCE_PASSWORD` | PostgreSQL password | `ptdashboard` |
| `QUARKUS_DATASOURCE_REACTIVE_URL` | Reactive URL | `postgresql://localhost:5432/ptdashboard` |
| `FIREBASE_PROJECT_ID` | Firebase project id (required from PTD-4 onward) | _(empty)_ |
| `FIREBASE_SERVICE_ACCOUNT_PATH` | Path to Firebase service-account JSON (required from PTD-4 onward) | _(empty)_ |

### Authentication

Backend verifies Firebase ID tokens on the Mutiny worker pool via
`FirebaseAuthFilter` (`com.faworkshop.ptdashboard.security.FirebaseAuthFilter`).

- Protected paths require `Authorization: Bearer <firebase-id-token>`.
- Public paths (currently `/q/health/*`, `/q/openapi`, etc.) bypass the filter.
- Verification runs off the Vert.x event loop (see
  `src/main/java/com/faworkshop/ptdashboard/security/FirebaseTokenVerifier.java`).
- Verified tokens set `SecurityIdentity` with the Firebase `uid` as principal
  (`identity.getPrincipal().getName()` → uid), plus `firebase_email` and
  `firebase_token` attributes and the `user` role.
- If either `FIREBASE_PROJECT_ID` or `FIREBASE_SERVICE_ACCOUNT_PATH` is
  blank at boot, `FirebaseAppLifecycle` logs a single warning and the
  filter rejects every protected request with `401 auth_unavailable`. This
  is intentional: dev environments without secrets never accidentally grant
  access to protected paths.

Service-account JSON must NEVER be committed. See `.gitignore` for the
patterns that exclude `*-firebase-adminsdk-*.json` and
`firebase-service-account*.json`.

## Status

Phase 1 backend scaffold is in place. See [implementation phases](docs/implementation-phases.md) for the build roadmap.
