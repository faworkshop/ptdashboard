# Linear Issue Templates

Copy and adapt for `mcp_linear_save_issue` `description` field.

---

## Epic (phase)

```markdown
## Goal
<Phase goal from implementation-phases.md>

## Deliverables
- [ ] All child tasks completed
- [ ] Phase exit criteria met

## Exit criteria
<paste from docs/implementation-phases.md>

## Docs
- [Implementation phases](../../docs/implementation-phases.md)
- [Success criteria](../../docs/success-criteria.md)

## Children
Tasks and test issues linked as sub-issues.
```

---

## Backend dev task

```markdown
## Summary
Implement <feature> for pt_dashboard reactive backend.

## Scope
- [ ] <Specific class or endpoint>
- [ ] Returns `Uni<T>` — no blocking on event loop
- [ ] Flyway migration if schema change

## References
- `docs/api.md` — endpoint contract
- `docs/architecture.md` — reactive conventions
- Phase exit: `docs/implementation-phases.md` → Phase N

## Exit criteria
- [ ] <Verifiable behavior>
- [ ] `./mvnw test` passes for new tests

## Dependencies
- **Blocked by:** LIN-xxx (<reason>)
- **Blocks:** LIN-yyy (<reason>)
- **Priority:** Medium

## Agent notes
- Package: `com.faworkshop.ptdashboard`
- Match existing Panache reactive patterns
```

---

## Frontend dev task

```markdown
## Summary
Implement <UI feature> in `src/main/resources/web/`.

## Scope
- [ ] Component / page
- [ ] Mobile-first; 44px touch targets; icon + label
- [ ] EN + zh-Hant strings (or i18n keys)

## References
- `docs/frontend.md`
- Success: `docs/success-criteria.md` → U1, D1

## Exit criteria
- [ ] Flow works in browser against local Quarkus
- [ ] No console errors on happy path

## Agent notes
- Use Firebase `getIdToken()` for API calls
- React Query for `/eta/favorites`
```

---

## Unit test task

```markdown
## Summary
Add unit tests for <component>.

## Test plan
1. Load fixture JSON from `src/test/resources/fixtures/<operator>/`
2. Assert normalized `EtaEntry` fields for sample input
3. Cover error path: upstream 429 / empty response

## Success criteria
- [ ] ≥ 80% branch coverage on target class
- [ ] `./mvnw test` green

## References
- `docs/upstream-apis.md` — response shapes
```

---

## E2E / integration test task

```markdown
## Summary
End-to-end test: <user flow>.

## Preconditions
- Docker PostgreSQL running
- Firebase test user (dev seed)
- `quarkus:dev` on :8080

## Steps
1. Sign in (or mock Firebase token in test harness)
2. POST /auth/sync
3. <flow steps>
4. Assert <expected ETA / UI state>

## Success criteria
- [ ] Maps to `docs/success-criteria.md` → F1 / U1
- [ ] Runs in CI (document profile if `@IfBuildProfile`)

## Agent notes
- Prefer REST Assured for API; Playwright for UI if added later
```

---

## Usability / a11y test task

```markdown
## Summary
Validate <screen> against success criteria before launch.

## Test plan
### Accessibility
- [ ] Lighthouse accessibility ≥ 90
- [ ] axe: no critical violations
- [ ] VoiceOver: ETA minutes announced

### Usability (5 participants, include 1 elderly)
- [ ] Task: add favorite in ≤ 5 steps — ≥ 80% success (U2)
- [ ] Task: read ETA at a glance — 5-second comprehension (U9)

## References
- `docs/success-criteria.md` → U1–U9, D1–D7

## Deliverable
Short test report as Linear comment or linked doc — not a code change unless fixes required.
```

---

## Bug / agent follow-up

```markdown
## Summary
Fix <issue> found during agent implementation or review.

## Reproduction
1. ...
2. Expected: ...
3. Actual: ...

## Fix scope
- [ ] Minimal diff — root cause only

## Verification
- [ ] Regression test added
- [ ] Related success criterion still passes
```
