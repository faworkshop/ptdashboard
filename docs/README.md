# PT Dashboard — Documentation

Design and planning documentation for the Hong Kong public transport ETA dashboard.

## Contents

1. **[Architecture](architecture.md)** — Reactive Quarkus backend, data flow, caching strategy, project layout
2. **[Authentication](auth.md)** — Firebase Auth integration, token verification, user sync
3. **[Data model](data-model.md)** — Users, favorites (stop + route), ad slots, JSON config schemas
4. **[API reference](api.md)** — REST endpoints under `/api/v1`
5. **[Upstream APIs](upstream-apis.md)** — External HK government ETA data sources and per-operator integration
6. **[Frontend](frontend.md)** — React SPA pages, dashboard cards, add-favorite wizard
7. **[Analytics](analytics.md)** — Google Analytics 4, events, cookie consent
8. **[Advertisements](ads.md)** — Ad placements, admin CRUD, impression tracking
9. **[Implementation phases](implementation-phases.md)** — Seven-phase build plan with deliverables
10. **[Monetization](monetization.md)** — Pro tier (HK$18/mo), Stripe, feature gating, push alerts
11. **[Success criteria](success-criteria.md)** — Usability, accessibility, performance, and launch readiness

## Key design decisions

| Decision | Choice |
|----------|--------|
| Backend paradigm | Fully reactive (Mutiny `Uni`/`Multi`, Hibernate Reactive) |
| Bus/minibus favorites | **Route required** per stop — one favorite = one stop + one route |
| MTR Bus favorites | **Route + stop required** — `routeName` + `busStopId` |
| MTR favorites | Heavy rail: line + station (+ optional direction/platform) |
| LRT favorites | Light Rail: station + route required (+ optional platform) |
| Favorites storage | PostgreSQL with user accounts linked via `firebase_uid` |
| Authentication | Firebase Auth (client) + Firebase Admin SDK (server token verification) |
| ETA data | Proxied through Quarkus (CORS, caching, rate-limit handling) |
| Ads | Self-hosted ad slots (free tier); ad-free for Pro |
| Monetization | Freemium — Pro HK$18/mo via Stripe |
| Analytics | Google Analytics 4 (GA4), consent-gated, no PII |
| Target users | All HK public transport users — children, elderly, diverse languages |
| UX goals | Easy to use, fast, clean interface; WCAG 2.1 AA; EN + zh-Hant |

## Out of scope

- Red minibus (no official open-data ETA API)
- User favorites API from operators (all favorites are app-owned)

## License

Proprietary — not open source. All rights reserved by FA Workshop. See [LICENSE](../LICENSE).
