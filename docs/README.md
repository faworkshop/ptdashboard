# PT Dashboard — Documentation

Design and planning documentation for the Hong Kong public transport ETA dashboard.

## Contents

1. **[Architecture](architecture.md)** — Reactive Quarkus backend, data flow, caching strategy, project layout
2. **[Authentication](auth.md)** — Firebase Auth integration, token verification, user sync
3. **[Data model](data-model.md)** — Users, favorites (stop + route), ad slots, JSON config schemas
4. **[API reference](api.md)** — REST endpoints under `/api/v1`
5. **[Upstream APIs](upstream-apis.md)** — External HK government ETA data sources and per-operator integration
6. **[Frontend](frontend.md)** — React SPA pages, dashboard cards, add-favorite wizard
7. **[Advertisements](ads.md)** — Ad placements, admin CRUD, impression tracking
8. **[Implementation phases](implementation-phases.md)** — Six-phase build plan with deliverables

## Key design decisions

| Decision | Choice |
|----------|--------|
| Backend paradigm | Fully reactive (Mutiny `Uni`/`Multi`, Hibernate Reactive) |
| Bus/minibus favorites | **Route required** per stop — one favorite = one stop + one route |
| MTR favorites | Line + station (+ optional direction/platform) |
| Favorites storage | PostgreSQL with user accounts linked via `firebase_uid` |
| Authentication | Firebase Auth (client) + Firebase Admin SDK (server token verification) |
| ETA data | Proxied through Quarkus (CORS, caching, rate-limit handling) |
| Ads | Self-hosted ad slots with future third-party network support |

## Out of scope

- Red minibus (no official open-data ETA API)
- User favorites API from operators (all favorites are app-owned)
