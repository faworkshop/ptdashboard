---
name: create-linear-tickets
description: >-
  Creates and structures Linear issues for AI-agent development and testing.
  Use when the user asks to create Linear tickets, break a plan into issues,
  file agent tasks, set up a sprint backlog, set issue priority or blockers,
  or track implementation/testing work in Linear for pt_dashboard or similar projects.
---

# Create Linear Tickets (AI Agent Dev & Test)

Turn plans, phases, or ad-hoc work into **actionable Linear issues** sized for AI agents and human review. Optimized for [pt_dashboard](https://github.com/faworkshop/ptdashboard) but reusable for any repo with `docs/implementation-phases.md`.

## When to use

- User says: "create Linear tickets", "file issues in Linear", "break phase N into tasks"
- Starting agent implementation of a plan phase
- Adding **testing** issues (unit, integration, E2E, a11y, usability)
- Syncing docs (`docs/implementation-phases.md`, `docs/success-criteria.md`) → backlog

## Prerequisites

1. **Linear MCP** connected in Cursor
2. Resolve **team** and **project** before creating issues (see Discovery)
3. Read project docs when present:
   - `docs/implementation-phases.md` — phases, tasks, exit criteria
   - `docs/success-criteria.md` — acceptance / launch gates
   - Relevant spec (`docs/api.md`, `docs/auth.md`, etc.)

## Discovery (always first)

Run before creating issues:

```
mcp_linear_list_teams
mcp_linear_list_projects  (filter by team if needed)
mcp_linear_list_issue_labels  (team-scoped)
mcp_linear_list_issues  (query existing titles to avoid duplicates)
```

If team/project unknown, use **AskQuestion** or ask once:

- Team key/name (required for `mcp_linear_save_issue`)
- Project slug (optional but recommended: `ptdashboard`)
- Milestone or cycle (optional)

Store defaults in issue descriptions; do not invent team IDs.

## Issue hierarchy

| Level | Linear shape | Purpose |
|-------|--------------|---------|
| **Epic** | Parent issue per phase | "Phase 2 — Reactive ETA Proxy" |
| **Task** | Child issue per deliverable | One agent-sized unit (≤ 1 day) |
| **Test** | Child or linked issue | Verification explicit for agents |

**Parent linking:** `mcp_linear_save_issue` with `parentId` set to epic UUID.

Do **not** create one giant issue per phase unless user explicitly asks — split into tasks agents can complete independently.

## Priority

Set **`priority`** on every `mcp_linear_save_issue` call. Linear values:

| Value | Name | When to use |
|-------|------|-------------|
| `1` | Urgent | Production outage, security fix, launch blocker < 24h |
| `2` | High | Current sprint / active phase work, cross-team dependency |
| `3` | Medium | **Default** for phase dev tasks within the active phase |
| `4` | Low | Stretch goals, nice-to-have, future phases not yet started |
| `0` | None | Epics only (optional) — children carry real priority |

### pt_dashboard defaults

| Issue type | Priority | Notes |
|------------|----------|-------|
| Epic (phase parent) | `0` or `3` | Epic tracks phase; prioritize children |
| Active phase — dev task | `3` | Normal agent work |
| Active phase — test task | `3` | Same sprint as dev it verifies |
| Next phase tasks | `4` | Created early but not started |
| Launch / success-criteria gate | `2` | Phase 6 a11y, usability, perf before release |
| User-reported production bug | `1` | Only when user confirms urgency |

**Rule:** Earlier phases → higher priority than later phases when both exist. Within a phase, **foundation before dependents** (see Blockers).

Update priority on existing issues: `mcp_linear_save_issue` with `id` + `priority`.

## Blockers (dependencies)

Set explicit **blocking relations** so agents and humans know execution order. Use MCP fields on `mcp_linear_save_issue`:

| Field | Meaning |
|-------|---------|
| `blockedBy` | Array of issue IDs/identifiers **that must complete first** (this issue is blocked) |
| `blocks` | Array of issue IDs/identifiers **waiting on this issue** |
| `removeBlockedBy` | Remove stale blocker links on update |
| `removeBlocks` | Remove stale blocks links on update |

**Prefer `blockedBy`** on the dependent issue (clearer: "I am blocked by X").

### Two-pass creation (recommended)

```
Pass 1 — Create all issues (capture returned id / identifier per issue)
Pass 2 — Update each dependent with blockedBy: ["LIN-123", ...]
```

Or set `blockedBy` on create when blocker issues were created first in the same session.

### pt_dashboard dependency rules

Use `docs/implementation-phases.md` dependency graph:

| Issue | blockedBy (typical) |
|-------|---------------------|
| Phase 2 epic / tasks | Phase 1 exit (auth + favorites CRUD) |
| Phase 3 search endpoints | Phase 1 scaffold; Phase 2 optional for `/eta/preview` only |
| Phase 4 frontend — dashboard | Phase 2 `/eta/favorites` + Phase 3 search (add-favorite flow) |
| Phase 4 frontend — auth UI | Phase 1 auth API |
| Phase 5 ads UI | Phase 4 dashboard shell |
| Phase 6 GA4 / a11y / usability | Phase 4 frontend |
| Phase 7 Stripe / Pro | Phase 1 auth; Phase 5 ad gate for ad-free logic |
| Unit tests for normalizers | Corresponding client/normalizer dev task |
| E2E add-favorite | Phase 3 search + Phase 4 wizard |
| E2E dashboard ETA | Phase 2 ETA + Phase 4 dashboard |

**Within-phase examples (Phase 1):**

```
Flyway + entities     → (no blockers)
FirebaseAuthFilter    → blockedBy: [Flyway task]
POST /auth/sync       → blockedBy: [FirebaseAuthFilter]
Favorites CRUD        → blockedBy: [Flyway task, FirebaseAuthFilter]
Phase 1 API tests     → blockedBy: [Favorites CRUD, /auth/sync]
```

**Within-phase examples (Phase 2):**

```
Each REST client      → blockedBy: [Phase 1 scaffold] (parallel OK across clients)
EtaNormalizer         → blockedBy: [matching REST client]
GET /eta/favorites    → blockedBy: [all normalizers for in-scope operators]
Normalizer unit tests → blockedBy: [matching EtaNormalizer]
```

### Document blockers in description

Always duplicate blockers in the issue body for readability:

```markdown
## Dependencies
- **Blocked by:** LIN-123 (Flyway migration), LIN-124 (FirebaseAuthFilter)
- **Blocks:** LIN-130 (Favorites CRUD tests)
```

### Verify relations

After setting blockers:

```
mcp_linear_get_issue — id: LIN-xxx, includeRelations: true
```

Confirm `blockedBy` / `blocks` match intended graph. Fix with `removeBlockedBy` + updated `blockedBy` if wrong.

## Labels (recommended)

Create via `mcp_linear_create_issue_label` if missing:

| Label | Use |
|-------|-----|
| `Phase 1` … `Phase 7` | Implementation phase |
| `Backend` | Quarkus / Java |
| `Frontend` | React / Web Bundler |
| `Feature` | New user-facing or API capability |
| `AI-Ready` | All FAW-52 sections filled — agent can implement without guessing |
| `Testing` | Test-only issue |
| `Docs` | Documentation only |

## Issue title convention

```
[Phase N] <Component>: <imperative action>
```

Examples:

- `[Phase 1] Auth: Firebase ID token filter + POST /auth/sync`
- `[Phase 2] ETA: KmbClient + EtaNormalizer unit tests`
- `[Phase 4] Test: E2E add-favorite wizard (bus + route required)`
- `[Phase 6] A11y: Lighthouse WCAG 2.1 AA audit on dashboard`

## Description template (FAW-52 style)

**Reference:** [FAW-52](https://linear.app/faworkshop/issue/FAW-52/admin-reviews-audit-trail-reviewer-notes-for-manual-decisions) — use the same section order for every dev/test issue.

Required sections (in order):

1. **Context** — why, plan links, related Linear issues (`<issue id="PTD-X">PTD-X</issue>`)
2. **Scope In** — included work (bullets)
3. **Out of Scope** — explicit exclusions
4. **Acceptance Criteria** — verifiable `- [ ]` checkboxes
5. **Technical Notes** — Frontend / Backend / Database touch points
6. **Test Plan** — Frontend + Backend subsections
7. **Verification Commands** — `docker compose`, `./mvnw test`, curl examples
8. **Definition of Done** — tests, migrations, docs, auth
9. **Risks / Open Questions**

Set **`blockedBy` / `priority` via MCP** and mention under Context or Technical Notes.

Full copy-paste templates: [templates.md](templates.md).

**Do not create thin tickets** with only Summary + Scope — agents and reviewers need Acceptance Criteria and Verification Commands.

Epic issues use shortened template (see templates.md).

## Workflow

### A. Create backlog from full plan

```
1. Discovery → team, project, labels
2. For each phase in docs/implementation-phases.md:
   a. Create epic issue (title: "Phase N — <name>", priority: 0 or 3)
   b. For each table row / bullet in phase → create child task (priority: 3 if active phase, else 4)
   c. Add 1–3 testing children per phase (priority: 3, blockedBy: related dev tasks)
3. Pass 2: Set blockedBy on all dependents (phase graph + within-phase order)
4. Link repo: mcp_linear_save_issue links: [{ url: repo, title: "ptdashboard" }]
5. Verify: mcp_linear_get_issue with includeRelations on sample chain
6. Report created issue URLs, priority, and blocker summary to user
```

### B. Create tickets for one phase only

Same as A but scope to requested phase number.

### C. Create single ad-hoc ticket

Use template; ask for phase label and test requirements if unclear.

### D. Update existing ticket

Use `mcp_linear_save_issue` with `id` set — do not duplicate. Can update `priority`, `blockedBy`, `blocks`, `removeBlockedBy`, `removeBlocks` without changing title/body.

Before create, search: `mcp_linear_list_issues` with `query` matching title keywords.

### E. Set priority or blockers only

When user asks to reprioritize or wire dependencies on existing backlog:

```
1. mcp_linear_list_issues — filter by project/phase label
2. Build dependency graph from docs or user input
3. mcp_linear_save_issue per issue — priority and/or blockedBy
4. Summarize changes in a table (id, title, priority, blockedBy)
```

## Testing issues (required for agent quality)

Create explicit test issues — do not bury testing only in dev task checkboxes.

| Type | When | Example title |
|------|------|----------------|
| **Unit** | After normalizers, services, validators | `[Phase 2] Test: EtaNormalizer fixtures (KMB, LRT, MTR Bus)` |
| **API** | After REST resources | `[Phase 1] Test: Favorites CRUD + route validation (REST Assured)` |
| **E2E** | After Phase 4 frontend | `[Phase 4] Test: E2E sign-in → add favorite → dashboard ETA` |
| **A11y** | Phase 4/6 | `[Phase 6] Test: axe + Lighthouse a11y on dashboard (WCAG AA)` |
| **Usability** | Before launch | `[Phase 6] Test: Usability session (elderly + general public)` |
| **Perf** | Phase 6 | `[Phase 6] Test: LCP < 2.5s, ETA p95 < 800ms` |

Test issue body must include full FAW-52 sections; **Acceptance Criteria** must map to success criteria IDs (F1, U1, P1, etc.).

## Agent-development conventions

When filing issues for AI agents:

1. **One concern per issue** — e.g. separate clients from normalizers
2. **Include file paths** where known (`src/main/java/com/faworkshop/ptdashboard/...`)
3. **Set `blockedBy` via MCP** — not only prose in description; use Pass 2 updates
4. **Set `priority` on every issue** — follow Priority table above
5. Set `delegate` to Linear agent user only if user requests agent delegation
6. Never put secrets in issues (Firebase keys, Stripe keys)

## MCP quick reference

| Action | Tool / fields |
|--------|----------------|
| Create issue | `mcp_linear_save_issue` — `title`, `team`, `description`, `project`, `labels`, `parentId` |
| **Priority** | `priority`: `0` None, `1` Urgent, `2` High, `3` Medium, `4` Low |
| **Blockers** | `blockedBy`: `["LIN-123", "uuid"]` — issues that must finish first |
| **Blocks** | `blocks`: `["LIN-456"]` — issues waiting on this one |
| Remove relations | `removeBlockedBy`, `removeBlocks` on update |
| Verify relations | `mcp_linear_get_issue` — `includeRelations: true` |
| Link PR later | `links: [{ url, title }]` on save_issue |
| List to dedupe | `mcp_linear_list_issues` — `query`, `team`, `project` |
| Comments | `mcp_linear_save_comment` — `issueId`, `body` |

### Example: create with priority and blockers

```json
{
  "title": "[Phase 1] Auth: POST /auth/sync",
  "team": "FA Workshop",
  "project": "ptdashboard",
  "priority": 3,
  "parentId": "<phase-1-epic-uuid>",
  "labels": ["AI-Ready", "Backend", "Phase 1", "Feature"],
  "blockedBy": ["LIN-101", "LIN-102"],
  "description": "..."
}
```

### Example: pass-2 blocker update

```json
{
  "id": "LIN-105",
  "blockedBy": ["LIN-103", "LIN-104"]
}
```

## Phase → default task split (pt_dashboard)

Use when docs lack detail:

| Phase | Epic tasks (minimum) |
|-------|----------------------|
| 1 | Scaffold, Firebase auth, Flyway, Favorites CRUD, docker-compose |
| 2 | REST clients (per operator), EtaNormalizer, `/eta/favorites`, unit tests |
| 3 | Static cache, search endpoints, LRT/MTR Bus meta |
| 4 | Auth UI, dashboard cards, add-favorite wizard, a11y baseline |
| 5 | ad_slots, AdBanner, admin API, impression endpoint |
| 6 | i18n, GA4+consent, health, success-criteria validation |
| 7 | Stripe, FeatureGate, FCM alerts, `/upgrade` UI |

## Output to user

After creating issues, summarize:

```markdown
## Linear backlog created

| ID | Title | Priority | Blocked by |
|----|-------|----------|------------|
| LIN-101 | [Phase 1] Flyway migration | Medium | — |
| LIN-105 | [Phase 1] POST /auth/sync | Medium | LIN-101, LIN-102 |

**Phase epics:** LIN-100 (Phase 1), LIN-110 (Phase 2), …

Links:
- https://linear.app/.../issue/LIN-xxx

Next: start unblocked issues in Phase 1; agents should not pick issues with open blockers.
```

## Additional resources

- Issue body examples: [templates.md](templates.md)
- pt_dashboard phases: `docs/implementation-phases.md`
- Acceptance gates: `docs/success-criteria.md`
