# Success Criteria

PT Dashboard serves **all Hong Kong citizens who rely on public transport** — including children, the elderly, and people of diverse backgrounds and nationalities. Success means the app is **easy to use, fast, and visually clean**, not merely feature-complete.

Criteria are grouped by theme. Each item is **verifiable** before launch (Phase 6 exit) or within 90 days post-launch where noted.

---

## 1. Usability & accessibility

The app must be usable without training, including by users with limited tech experience or vision/motor challenges.

| ID | Criterion | Target | How to verify |
|----|-----------|--------|---------------|
| U1 | **Time to first ETA** — new user sees live ETA for a saved favorite | ≤ 3 steps after sign-in (dashboard load counts as 1) | Usability test with 5 participants (include 1 elderly, 1 low digital literacy) |
| U2 | **Add a favorite** — complete stop + route selection | ≤ 5 steps; no step skipped for bus/GMB/LRT/MTR Bus | Task completion test; ≥ 80% succeed without help |
| U3 | **Touch targets** — buttons, links, card actions | Minimum **44×44 px** (WCAG 2.5.5) | Design review + automated a11y scan |
| U4 | **Text legibility** — body text on dashboard cards | Minimum **16 px** base; supports browser/OS text scaling up to 200% without layout break | Visual test at 100%, 150%, 200% zoom |
| U5 | **Color contrast** — text and icons on backgrounds | **WCAG 2.1 AA** (4.5:1 normal text, 3:1 large text) | axe / Lighthouse accessibility audit |
| U6 | **Icon + label** — transport mode and actions | No critical action is icon-only; every icon has visible text or `aria-label` | Manual audit of all interactive elements |
| U7 | **Screen reader** — core flows | Sign-in, view dashboard, read ETA minutes and destination announced correctly | VoiceOver (iOS) or TalkBack (Android) walkthrough |
| U8 | **Error messages** — upstream failure or empty state | Plain language (no HTTP codes); suggest next action (“Tap refresh” / “Try again”) | Content review of all error/empty states |
| U9 | **Cognitive load** — dashboard at a glance | One primary number per card (**minutes until arrival**); secondary info visually subordinate | Design review; 5-second comprehension test |

---

## 2. Inclusivity & language

Users span ages, languages, and familiarity with HK transport naming.

| ID | Criterion | Target | How to verify |
|----|-----------|--------|---------------|
| L1 | **Bilingual UI** | English + **Traditional Chinese (zh-Hant)** for all user-facing strings | i18n coverage checklist; no untranslated keys in production |
| L2 | **Stop/route names** | Display upstream **EN and zh** names where API provides both; respect user language preference | Spot-check KMB, MTR, LRT favorites in both languages |
| L3 | **Language switch** | User can change language in settings; persists across sessions | Functional test |
| L4 | **Inclusive copy** | No jargon (e.g. “JWT”, “upstream”); transport terms match everyday HK usage (“巴士”, “港鐵”, “輕鐵”) | Copy review by native EN + zh-Hant speakers |
| L5 | **Children & elderly** | No account-destructive actions without confirmation; no tiny swipe-only gestures for core tasks | Task analysis; optional parental/guardian use case documented |
| L6 | **Cookie / analytics consent** | Clear, short consent text in both languages before GA4 loads | Legal/copy review |

---

## 3. Performance & responsiveness

The app must feel **instant** on typical HK mobile networks (4G/5G) and mid-range phones.

| ID | Criterion | Target | How to verify |
|----|-----------|--------|---------------|
| P1 | **Largest Contentful Paint (LCP)** — dashboard | **< 2.5 s** on simulated Fast 4G | Lighthouse mobile, 3-run median |
| P2 | **ETA API response** — `GET /eta/favorites` (warm cache) | **p95 < 800 ms** server-side | Backend metrics / load test |
| P3 | **ETA API response** — cold cache, 5 favorites mixed modes | **p95 < 3 s** | Integration test + staging metrics |
| P4 | **Dashboard auto-refresh** | No full-page flash; cards update in place | Visual regression / manual test |
| P5 | **First load on 3G** | Usable shell (skeleton + auth) **< 4 s**; ETAs populate progressively | Chrome DevTools Slow 4G throttle |
| P6 | **Backend reactivity** | No blocking calls on Vert.x event loop; no request thread starvation under 50 concurrent users | Stress test + code review |
| P7 | **PWA install** (Pro) | Add to home screen; launches to dashboard **< 3 s** | Device test on iOS Safari + Android Chrome |

---

## 4. Visual design & clarity

**Clean** means uncluttered, consistent, and calm — suitable for a glance while commuting.

| ID | Criterion | Target | How to verify |
|----|-----------|--------|---------------|
| D1 | **Visual hierarchy** | ETA minutes are the **largest** element on each card; route/stop name secondary | Design spec + screenshot review |
| D2 | **Information density** | Max **3 ETAs** shown per favorite card; no horizontal scroll on 320 px width | iPhone SE / small Android viewport test |
| D3 | **Consistent spacing** | 4 px grid; uniform card padding and gaps | Design system checklist |
| D4 | **Color discipline** | ≤ 1 accent color + neutrals + semantic colors (error/stale only) | Design review |
| D5 | **Ads vs content** | Ads visually distinct from ETA cards; never mimic ETA layout (avoid confusion for elderly users) | UX review; ad placement screenshot |
| D6 | **Dark / light** | Readable in both modes if supported; minimum AA contrast in default theme | Contrast audit |
| D7 | **Motion** | Respect `prefers-reduced-motion`; no essential info conveyed by animation alone | CSS / a11y test |

---

## 5. Functional completeness

Core product promises from the plan must work reliably for all in-scope transport modes.

| ID | Criterion | Target | How to verify |
|----|-----------|--------|---------------|
| F1 | **Transport coverage** | KMB, CTB, NLB, GMB, MTR, LRT, MTR Bus — each has ≥ 1 passing E2E favorite | Automated or manual E2E suite |
| F2 | **Route-required favorites** | Bus/GMB/LRT/MTR Bus reject save without route; MTR uses line+station | API + UI tests |
| F3 | **Auth** | Firebase sign-in + sync; session survives browser refresh | Auth E2E test |
| F4 | **Favorites sync** | Same favorites on second device after login | Two-device test |
| F5 | **Stale/error handling** | Upstream 429/5xx shows badge or message, not blank screen | Fault injection test |
| F6 | **Free tier limits** | 5-favorite cap enforced with clear upgrade path | Functional test |
| F7 | **Pro tier** (Phase 7) | Ad-free, unlimited favorites, push alert fires once in test | Stripe test mode + FCM test |

---

## 6. Reliability & trust

Citizens depend on accurate times for daily commuting.

| ID | Criterion | Target | How to verify |
|----|-----------|--------|---------------|
| R1 | **ETA accuracy** | Displayed ETAs match upstream API within cache TTL (no stale data without badge) | Compare API vs UI in staging |
| R2 | **Uptime** | **99.5%** monthly availability (API + web) post-launch | Monitoring / status page |
| R3 | **Health checks** | `/q/health` green when DB + critical upstream reachable | CI + uptime monitor |
| R4 | **Data privacy** | No passwords stored; GA4 consent-gated; no PII in analytics events | Security/privacy checklist |
| R5 | **Rate limits** | No user-visible cascade failures when upstream returns 429 | Load test with cache |

---

## 7. Business & growth (post-launch, 90 days)

Optional but useful for a sustainable product.

| ID | Criterion | Target | How to verify |
|----|-----------|--------|---------------|
| B1 | **Activation** | ≥ **40%** of registered users add ≥ 1 favorite within 7 days | GA4 funnel |
| B2 | **Retention** | ≥ **25%** of users return weekly (WAU/MAU) | GA4 retention report |
| B3 | **Pro conversion** | ≥ **2%** of MAU start Pro checkout (stretch) | Stripe + GA4 |
| B4 | **Support burden** | < 5% of users need help to complete first favorite (usability survey) | In-app feedback or survey |

---

## Launch readiness checklist

All **must pass** before public launch:

- [ ] U1–U3, U5, U8 — usability & accessibility baseline
- [ ] L1, L2, L4, L6 — language & consent
- [ ] P1, P2, P4 — performance baseline
- [ ] D1–D3, D5 — clean, unambiguous UI
- [ ] F1–F5 — core features per transport mode
- [ ] R1–R4 — trust & privacy

**Phase 7 (Pro)** and **B*** metrics are validated after monetization ships.

---

## Out of scope for v1 success

These are explicitly **not** required for initial launch success:

- Red minibus ETA
- Simplified Chinese (zh-Hans) — stretch after zh-Hant
- WCAG AAA (AA is the target)
- Native iOS/Android apps (PWA is sufficient for v1)
- Offline ETA (requires cached last-known; stretch goal)

---

## Relationship to implementation phases

| Phase | Success criteria primarily addressed |
|-------|--------------------------------------|
| Phase 4 — Frontend | U1–U9, D1–D7, L3–L5 |
| Phase 5 — Ads | D5, D1 (ad/ETA distinction) |
| Phase 6 — Polish | L1–L2, L6, P1, P5, R3–R4, launch checklist |
| Phase 7 — Monetization | F6–F7, B3 |

Usability testing (U1, U2, U9) should run **after Phase 4** with real participants before launch.
