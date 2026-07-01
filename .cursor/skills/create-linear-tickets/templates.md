# Linear Issue Templates

Templates follow the **FAW-52** structure used in Faworkshop Linear issues.  
Reference: [FAW-52 — Admin Reviews audit trail](https://linear.app/faworkshop/issue/FAW-52/admin-reviews-audit-trail-reviewer-notes-for-manual-decisions)

Copy and adapt for `mcp_linear_save_issue` `description` field.

---

## Required sections (all task issues)

Every **dev** and **test** issue MUST include these sections in order:

| # | Section | Purpose |
|---|---------|---------|
| 1 | **Context** | Why this work exists; links to plan, parent epic, blocker issues |
| 2 | **Scope In** | What is included (bullet list) |
| 3 | **Out of Scope** | What is explicitly excluded (prevents scope creep) |
| 4 | **Acceptance Criteria** | Verifiable checkboxes — the contract for "done" |
| 5 | **Technical Notes** | Touch points: Frontend / Backend / Database / Config |
| 6 | **Test Plan** | Frontend + Backend test cases (even if backend-only, note "N/A" for frontend) |
| 7 | **Verification Commands** | Copy-paste commands to validate locally |
| 8 | **Definition of Done** | Meta checklist (tests, auth, migrations, docs) |
| 9 | **Risks / Open Questions** | Unknowns for human/agent decision |

**Epic issues** use a shortened variant (Context, Scope In, Out of Scope, Acceptance Criteria, Dependencies, Children).

**Dependencies** (`blockedBy` / `blocks`) are set via MCP fields AND summarized under Context or a short **Dependencies** subsection.

### Linking related issues in Context

Use Linear issue references in markdown:

```markdown
* Builds on <issue id="PTD-3">PTD-3</issue> (Flyway schema).
* Blocks <issue id="PTD-8">PTD-8</issue> (integration tests).
```

Or plain: `Blocked by PTD-2 (scaffold).`

### Recommended labels (PTDashboard team)

| Label | Use |
|-------|-----|
| `Phase 1` … `Phase 7` | Implementation phase |
| `Backend` | Quarkus / Java |
| `Frontend` | React / Web Bundler |
| `Feature` | New capability |
| `AI-Ready` | Enough detail for agent to implement without guessing |
| `Testing` | Test-only issue |

---

## Epic (phase parent)

```markdown
## Context

* Phase N from `docs/implementation-phases.md` — <phase name>.
* Delivers <one-line outcome> required before Phase N+1.
* Parent epic for all Phase N child tasks and tests.

## Scope In

* All Phase N dev tasks listed as sub-issues
* Phase N test/verification issues
* Phase exit criteria from plan

## Out of Scope

* Work belonging to Phase N+1 and later
* Unrelated refactors or nice-to-haves not in plan

## Acceptance Criteria

- [ ] All child issues completed or explicitly deferred with comment
- [ ] Phase exit criteria from `docs/implementation-phases.md` met
- [ ] Integration tests for phase pass in CI/local `./mvnw test`
- [ ] No open `blockedBy` relations on phase-critical path

## Dependencies

* **Blocked by:** <prior phase epic id, e.g. PTD-1 complete> or none for Phase 1
* **Blocks:** <next phase epic or key downstream issues>

## Children

Sub-issues created under this epic with full FAW-52 section templates.
```

---

## Backend dev task

```markdown
## Context

* Phase N — <feature area> from `docs/implementation-phases.md`.
* <Why this matters for users / downstream phases>.
* Builds on <issue id="PTD-X">PTD-X</issue> (<short reason>).
* Related feature area: <e.g. Auth, Favorites, ETA Proxy>

## Scope In

* <Concrete deliverable 1 — class, endpoint, migration>
* <Concrete deliverable 2>
* Reactive handlers return `Uni<T>`; no blocking on Vert.x event loop
* New Flyway migration if schema changes (new file only — no edits to shipped migrations)

## Out of Scope

* Frontend UI for this feature (unless explicitly in scope)
* <Adjacent features deferred to other issues>
* Operator integrations not listed in this ticket

## Acceptance Criteria

- [ ] <Specific API behavior or service behavior — measurable>
- [ ] <Validation rule — e.g. route required for bus/GMB types>
- [ ] Invalid/missing auth returns `401`; invalid payload returns `400` with clear message
- [ ] Happy path covered by automated test(s)
- [ ] `./mvnw test` passes for module(s) touched

## Technical Notes

**Touch points (expected):**

* Backend:
  * `src/main/java/com/faworkshop/ptdashboard/<package>/`
  * <Resource / Service / Filter names>
* Database:
  * New migration `V<n>__<name>.sql` if schema change
* Config:
  * `application.properties` — document new env vars (no secrets in repo)

**Stack:**

* Package: `com.faworkshop.ptdashboard`
* Hibernate Reactive + Panache; Firebase Admin on worker pool for token verify

**Dependencies (MCP):**

* Blocked by: PTD-X, PTD-Y
* Priority: High | Medium

## Test Plan

**Frontend:**

* N/A (backend-only)

**Backend:**

* `@QuarkusTest` integration test: <happy path>
* `@QuarkusTest` integration test: <auth failure / validation failure>
* Unit test: <service/validator logic> if isolated

## Verification Commands

**Backend:**

```bash
docker compose up -d
./mvnw test
./mvnw quarkus:dev
# Manual: curl with Firebase test token
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/...
```

## Definition of Done

- [ ] All acceptance criteria met
- [ ] New/updated tests included and passing
- [ ] Auth enforced on protected endpoints (if applicable)
- [ ] Schema change via new Flyway migration only (if applicable)
- [ ] `docs/api.md` updated if contract changes
- [ ] No secrets committed

## Risks / Open Questions

* <e.g. Firebase emulator vs real project for local dev?>
```

---

## Frontend dev task

```markdown
## Context

* Phase N — <UI feature> from `docs/frontend.md`.
* Supports success criteria: <e.g. U1, U2, D1 from `docs/success-criteria.md`>.
* Builds on <issue id="PTD-X">PTD-X</issue> (backend API available).

## Scope In

* UI in `src/main/resources/web/` — <page/component>
* Mobile-first; 44px touch targets; icon + label (no icon-only critical actions)
* EN + zh-Hant strings (or i18n keys)
* API calls use Firebase `getIdToken()` via shared client

## Out of Scope

* Backend API changes (separate ticket unless listed here)
* <Other pages / polish deferred>

## Acceptance Criteria

- [ ] <User-visible behavior — specific screen/flow>
- [ ] Works on 320px viewport without horizontal scroll for core content
- [ ] Error/empty states use plain language (success criteria U8)
- [ ] No console errors on happy path
- [ ] Loading and error states visible (no blank screen)

## Technical Notes

**Touch points (expected):**

* Frontend:
  * `src/main/resources/web/<path>`
  * Components: <names>
* API dependencies:
  * `GET/POST ...` — must match `docs/api.md`

**Dependencies (MCP):**

* Blocked by: PTD-X (API), PTD-Y (auth)
* Priority: Medium

## Test Plan

**Frontend:**

* Component test: <critical interaction>
* Manual: complete flow on mobile viewport (Chrome DevTools)
* Manual: language toggle if i18n in scope

**Backend:**

* N/A unless paired API work in same ticket

## Verification Commands

```bash
docker compose up -d
./mvnw quarkus:dev
# Open http://localhost:8080
```

## Definition of Done

- [ ] All acceptance criteria met
- [ ] Manual test steps documented in PR or comment if no automated UI tests yet
- [ ] Accessible labels / contrast checked for touched UI
- [ ] No hardcoded secrets or API keys in frontend bundle

## Risks / Open Questions

* <e.g. PWA install flow browser differences?>
```

---

## Integration / E2E test task

```markdown
## Context

* Phase N verification for <feature area>.
* Validates acceptance criteria from <dev issue id="PTD-X">PTD-X</issue>.
* Maps to `docs/success-criteria.md`: <F2, F3, F4, etc.>.

## Scope In

* Automated tests for <flows>
* Test fixtures / dev seed data usage documented
* CI-runnable (or `@IfBuildProfile("test")` documented)

## Out of Scope

* Implementing missing product features (file separate dev ticket)
* Load/stress testing (unless explicitly requested)

## Acceptance Criteria

- [ ] Test suite covers: <list endpoints or UI flows>
- [ ] Negative cases: invalid token, invalid payload, missing route on bus favorite
- [ ] Tests pass locally: `./mvnw test`
- [ ] Test names and assertions map to acceptance criteria IDs

## Technical Notes

**Touch points (expected):**

* `src/test/java/com/faworkshop/ptdashboard/`
* Fixtures: `src/test/resources/fixtures/`
* May require: Docker Postgres, Firebase test user from dev seed

**Dependencies (MCP):**

* Blocked by: all dev issues under test
* Priority: High

## Test Plan

**Frontend:**

* <Playwright/RTL if applicable, else N/A>

**Backend:**

* `@QuarkusTest` + REST Assured: <test class scenarios>
* List each scenario as bullet with expected status code + body shape

## Verification Commands

```bash
docker compose up -d
./mvnw test -Dtest=<TestClass>
./mvnw test
```

## Definition of Done

- [ ] All acceptance criteria met
- [ ] Tests are deterministic (no flaky external API calls — use mocks/fixtures)
- [ ] README or test class comment explains how to run

## Risks / Open Questions

* <e.g. mock Firebase vs test project for CI?>
```

---

## Unit test task (focused)

```markdown
## Context

* Unit coverage for <class> introduced in <issue id="PTD-X">PTD-X</issue>.
* Ensures normalizer/validator logic is correct before integration tests.

## Scope In

* Unit tests with recorded JSON fixtures from `docs/upstream-apis.md` shapes
* Error paths: empty response, malformed payload, 429 handling

## Out of Scope

* Full integration tests (separate ticket)
* Live calls to HK government APIs in unit tests

## Acceptance Criteria

- [ ] ≥ 80% branch coverage on target class (or all public methods covered)
- [ ] Fixtures for: <operator list>
- [ ] `./mvnw test -Dtest=<Class>Test` passes

## Technical Notes

**Touch points:**

* `src/test/java/.../<Class>Test.java`
* `src/test/resources/fixtures/<operator>/`

## Test Plan

**Backend:**

* Parameterized or table-driven tests per operator fixture
* Assert normalized `EtaEntry` fields match expected values

**Frontend:** N/A

## Verification Commands

```bash
./mvnw test -Dtest=<Class>Test
```

## Definition of Done

- [ ] Tests pass in isolation and full suite
- [ ] Fixtures committed (no live network)

## Risks / Open Questions

* None / <question>
```

---

## Usability / a11y validation task

```markdown
## Context

* Pre-launch validation per `docs/success-criteria.md` (U1–U9, D1–D7).
* Phase 6 gate before public release.

## Scope In

* Lighthouse + axe audit on dashboard and add-favorite flow
* Usability session protocol (5 participants, include 1 elderly)

## Out of Scope

* Implementing fixes (file follow-up bugs per finding)
* zh-Hans / additional languages

## Acceptance Criteria

- [ ] Lighthouse accessibility score ≥ 90 on dashboard (mobile)
- [ ] axe: zero critical violations on core flows
- [ ] U2: ≥ 80% participants add favorite in ≤ 5 steps without help
- [ ] U9: 5-second comprehension of ETA on card (5/5 participants)
- [ ] Findings documented in Linear comment or linked doc

## Technical Notes

**Touch points:** staging URL or local `quarkus:dev`

## Test Plan

**Frontend:**

* Lighthouse (mobile, Fast 4G)
* axe DevTools on `/`, `/add`, `/login`
* VoiceOver walkthrough: sign-in → dashboard → read ETA

**Usability:**

* Script + score sheet for 5 participants

## Verification Commands

```bash
./mvnw quarkus:dev
# Run Lighthouse in Chrome DevTools → Mobile
```

## Definition of Done

- [ ] Report attached or commented on epic
- [ ] Critical issues filed as separate bugs with priority

## Risks / Open Questions

* Recruitment for elderly participant
```

---

## Bug / follow-up

```markdown
## Context

* Regression or gap found during <PTD-X / PR / QA>.
* Blocks: <issue or release> if severity high.

## Scope In

* Minimal fix for root cause only

## Out of Scope

* Refactoring surrounding code
* Unrelated improvements

## Acceptance Criteria

- [ ] Reproduction steps no longer fail
- [ ] Regression test added
- [ ] Related acceptance criteria still pass

## Technical Notes

**Touch points:** <files>

## Test Plan

**Backend:** regression test
**Frontend:** manual or component test if UI

## Verification Commands

```bash
./mvnw test
```

## Definition of Done

- [ ] Fix merged; test included
- [ ] Verified by reporter or QA

## Risks / Open Questions

* Root cause fully understood? Y/N
```
