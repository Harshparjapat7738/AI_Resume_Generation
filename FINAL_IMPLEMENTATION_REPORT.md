# CareerForge AI — Final Verification Report

**Date:** 2026-08-11
**Scope:** Full end-to-end verification of the product as it exists today (no new features added). Backend build, frontend build, full Docker stack, real user journeys (anonymous → register → onboarding → generation → result), all four generation modes, persistence, security, responsiveness, and automation (`mvn install`, frontend typecheck/build, Playwright E2E, Docker build/compose).

This report documents what was verified, what was found and fixed, and what remains a known limitation of the environment this verification ran in — stated plainly, without rounding failures up to passes.

---

## 1. Headline finding: a critical, silent auth bug — found and fixed

While reproducing the full user journey against a freshly-built Docker stack, backend calls that should have succeeded started failing with `401 AUTH_TOKEN_INVALID` immediately after a fresh login. Decoding the issued JWT showed:

```json
{ "sub": "...", "iat": 1786442494, "exp": 1786442494, "roles": ["ROLE_USER"] }
```

`iat == exp` — **the access token was already expired at the moment it was issued** (`expiresIn: 0` in the login response).

**Root cause:** `careerforge.jwt.access-expiration-seconds`, `refresh-expiration-seconds` and `issuer` are supplied to every service by config-server's shared `application.yml` (`infrastructure/config-repo/application.yml`) — auth-service's own local `application.yml` only ever set `secret`. The `spring.config.import: optional:configserver:...` import is deliberately *optional* (so services can boot without config-server for local "Mode 2" dev, per the compose file's own header comment). When config-server is unreachable at the exact moment a service starts, that import **fails silently**, and `JwtProperties` — a plain `@ConfigurationProperties` record with no validation — quietly bound `accessExpirationSeconds`/`refreshExpirationSeconds` to `0` and `issuer` to `null` instead of failing.

This is exactly what happened here: this dev machine's Docker Desktop crashed once under the sustained load of a long Playwright run (see §6), and when its containers auto-restarted via `restart: unless-stopped`, that restart bypassed `docker-compose.yml`'s `depends_on: config-server: condition: service_healthy` ordering (that ordering is enforced by `docker compose up`, not by the Docker engine's own container-restart policy). auth-service came up before config-server was reachable, its optional config import failed, and every access token it minted from then on was already-expired garbage. Any other outage or restart race in config-server would silently reproduce the same failure in production.

**Fix (`services/auth-service/.../config/JwtProperties.java`, `services/api-gateway/.../security/JwtVerifier.java`):**
- `JwtProperties` is now `@Validated` with `@NotBlank`/`@Positive` constraints on `secret`, `issuer`, `accessExpirationSeconds`, `refreshExpirationSeconds` — Spring now refuses to start auth-service at all if these are unbound, instead of quietly running with a broken auth system.
- The gateway's `JwtVerifier` now explicitly checks `issuer` at construction and throws immediately with a clear message ("is config-server reachable and healthy?") if it's missing, rather than silently rejecting every request one at a time with an unhelpful per-request debug log.

Verified after the fix: a clean, properly-ordered restart (`docker compose up`, which does respect `depends_on` health conditions) now issues tokens with `exp - iat = 900` (matching `JWT_ACCESS_EXPIRATION=900`) and a correct `issuer` claim, every time. This was re-confirmed with a fresh `mvn install` (JDK 21) and a fresh Docker rebuild of both services.

This bug was not introduced by any change in this session — it has existed since Auth Service was first built (Task 9) and is purely a matter of the local `application.yml` never mirroring config-server's `access-expiration-seconds`/`issuer`/`refresh-expiration-seconds` defaults. It had gone unnoticed because it only manifests when config-server is unreachable at exactly the wrong moment, and previous verification sessions never bounced containers hard enough, at the wrong time, to trigger the race.

---

## 2. What was verified, and how

### 2a. Backend build (`mvn install`)

The host machine only has **JDK 26** installed; this project targets **Java 21**, and the version gap breaks Mockito's inline mock-maker (ByteBuddy doesn't yet instrument JDK 26 bytecode), producing `MockitoException: Could not modify all classes` on several tests when run with the host JDK. This is a host-toolchain mismatch, not a product defect — confirmed by running the exact same reactor build inside `maven:3.9-eclipse-temurin-21` (Docker), which is also what the project's own `Dockerfile`s use to build production images:

- **Result: clean build, zero `[ERROR]` lines, full reactor** (`platform-common`, `config-server`, `discovery-server`, `api-gateway`, `auth-service`, `profile-service`, `jd-service`, `resume-service`, `ai-service`, `assessment-service`, `document-service`, `application-service`) — all modules compile, all unit/integration tests pass, including `GoogleOAuthServiceTest` (the suite that fails under JDK 26).
- Re-run after the `JwtProperties`/`JwtVerifier` fix (§1): still a clean build.

**Known limitation:** running `mvn install` directly on this machine (JDK 26 on `PATH`, no JDK 21 installed) will show Mockito failures in `auth-service`. That is an environment issue, not a code issue — install a JDK 21 locally or always build through Docker (which the `Dockerfile`s already pin to `eclipse-temurin:21`).

### 2b. Frontend build

```
npm run typecheck   → clean, 0 errors
npm run build       → clean (tsc -b && vite build), all 40 chunks emit
```

No frontend unit tests exist in this project (`npm run test` runs `vitest`, but there are no `*.test.ts(x)` files under `src/`) — this predates this session and is unrelated to it. **Known limitation**, not something fixed here (writing a full unit-test suite was out of scope for a verification pass).

### 2c. Docker build + compose startup

Full `--profile app` stack (13 services: config-server, discovery-server, api-gateway, auth/profile/jd/resume/ai/assessment/document/application-service, redis, minio) builds and reaches `healthy` on every service. Verified twice — once from a cold build, once after the JWT fix rebuild.

**Environment note, reported honestly:** during a sustained ~16-minute run of the full 39-test Playwright suite (real browsers + real backend + real external Groq calls, on top of a concurrently-running second local dev stack already occupying the standard ports on this same machine), **Docker Desktop itself crashed** (the daemon stopped responding; `docker ps` failed with "cannot connect to the Docker API"). It was relaunched and all containers came back up via their `restart: unless-stopped` policy — this is in fact how the auth bug in §1 was surfaced. This machine cannot reliably sustain the full containerized stack plus a real, uninterrupted multi-minute browser-automation run at the same time as another local dev session. This is a resource-capacity fact about this particular workstation, not a defect in the product or its Docker configuration. **Recommendation:** run the full E2E suite in a dedicated CI runner with adequate, unshared resources.

### 2d. Full user journey — verified via direct API calls (23 checks, comprehensive)

Given the environment constraints in §2c/§6, the full user journey was verified exhaustively at the HTTP/API level (fast, deterministic, no browser-resource contention) rather than relying solely on browser automation for signal. All 23 checks passed on the final, clean run:

- Register → login → **token has correct `expiresIn: 900` and decodes with `exp - iat = 900`** (the §1 fix, confirmed live)
- Profile write (experience) → JD create → confirm → analysis (real Groq call, real grounding/schema validation)
- **Resume only:** generate → PDF render → download (`%PDF-` magic bytes verified) → ATS assessment (score computed) → assessment re-fetchable after "refresh" (re-`GET`)
- **Generate All:** one `Application` created (not three) → resume attached → cover letter generated → email generated → application status becomes `COMPLETED` → application, cover letter and email all independently re-fetchable after "refresh" → the application appears **exactly once** in the dashboard list (`GET /api/applications`)
- **Persistence:** logout, login again, application history for that user is still present
- **Security:** a second, unrelated user cannot read the first user's application (`404`, not `403` — BOLA-hardened), cannot download their resume PDF (`404`), sees an empty profile (no data bleed); unauthenticated requests get `401`; a malformed application id gets `404` (never `200`); a forged/garbage bearer token gets `401`

### 2e. Playwright E2E — real-browser coverage, with an honest account of its limits

Two new full-suite runs (39 tests: the 6 pre-existing spec files plus 4 written this session — `generate-all.spec.ts`, `dashboard.spec.ts`, `anonymous-and-security.spec.ts`, `responsive.spec.ts`) were executed against the live Docker stack.

**A real regression was found and fixed:** the "Generate All" output-type card (built in an earlier task) added a second button whose accessible name also contains the word "Resume" ("Generate All — *Resume*, cover letter and email together…"). Every pre-existing spec that selected the Resume card with an unanchored `getByRole('button', { name: /Resume/ })` now matched two elements and threw a Playwright strict-mode violation. Fixed by anchoring the regex to `/^Resume/` in `email-generation.spec.ts`, `jd-url.spec.ts`, `profile.spec.ts`, `resume-pdf.spec.ts`, `template-selection.spec.ts` (a pattern already used correctly elsewhere in the same files). This alone fixed 17 of the 29 failures seen in the very first run.

A second, smaller fix: `profile.spec.ts` asserted a raw evidence id (`EXP-001`) is visible on `/profile` after a reload. The Profile page has since been redesigned (by other, concurrent work on this codebase — see below) to show formatted content ("Backend Engineer — Northwind Logistics") instead of raw ids; the assertion was updated to match the current, correct rendering rather than the old placeholder text.

A third fix: `playwright.config.ts` had `fullyParallel: false` (intended, per its own comment, to keep tests serial since each one registers a real account against a real backend) but no `workers` cap — Playwright still runs *separate spec files* in parallel workers by default. Set `workers: 1` so the documented intent is actually enforced.

**What this leaves unresolved, honestly:** after all three fixes, later full-suite and targeted-subset runs still showed a substantial number of failures in the onboarding-wizard steps (waiting on step headings/inputs that didn't appear, or fields detaching mid-fill). Investigating the failures individually traced them to two causes outside this task's control, not to a regression in Task 5's own changes or in the backend:

1. **Resource contention on this shared machine** (§2c) — slow renders under CPU pressure cause exactly this kind of "element detached, retrying" flakiness.
2. **Concurrent, uncommitted edits to the exact UI this session's tests exercise.** `git status` during this verification shows `frontend/src/features/onboarding/OnboardingPage.tsx`, `frontend/src/components/layout/AppHeader.tsx`, all seven `profile-shared/*Manager.tsx` files, `TextField.tsx`, `TextArea.tsx`, `Button.tsx`, and more, all modified in the working tree by activity outside this session (a new `UserMenu.tsx`/`icons.tsx` and a `profile/components/` directory appeared partway through this verification; `profile.spec.ts`'s logout step was itself updated mid-session, by that other activity, from a single "Log out" button to an "Account menu" → "Log out" menu-item pattern). Several onboarding-step assertions that were correct when this session started no longer matched the UI by the time the suite ran, because the UI kept changing underneath it.

Given both causes, running the full Playwright suite to a clean, stable green/red result was not achievable in this session's environment. **What is solid:** `responsive.spec.ts` (6 tests: mobile/tablet/desktop, no horizontal overflow, hamburger menu, nav) passed **every time it ran**, in every configuration — that flow doesn't depend on AI generation and isn't touched by the concurrent onboarding edits. The security-relevant assertions in `anonymous-and-security.spec.ts` that failed were traced to their own *setup* steps timing out waiting on JD-analysis (an AI call, §6), not to the security assertions themselves — and the same checks were independently, and successfully, proven at the API level in §2d.

**Recommendation:** re-run the full Playwright suite once the concurrent onboarding/profile UI work lands and this repository is otherwise idle, ideally in CI with dedicated resources.

### 2f. Responsive

Verified via Playwright across mobile (375×667), tablet (768×1024) and desktop (1440×900) viewports: no horizontal overflow on the landing page or dashboard, the mobile nav collapses to a hamburger menu, and the dashboard's filter chips/application cards reflow correctly. **6/6 passed, consistently, across every run this session.**

### 2g. Security

Verified twice, independently: once via the API-level script in §2d (23/23 including cross-user BOLA, unauthenticated 401, malformed-id 404, forged-token 401), and once via `anonymous-and-security.spec.ts` in earlier stable runs (protected-route redirect for anonymous visitors, landing→explore flow, unauthenticated 401). No security regression or gap was found.

---

## 3. Implemented / Partially implemented / Not implemented

**Implemented and verified this session:**
- Dashboard redesign around `Application` records (Task 5, prior to this verification pass) — company/job title/generation type/template/ATS/JD-match/status/created-date, `/applications/:id` detail route, type+status filters, legacy resume-history section — confirmed working end-to-end via §2d.
- Critical JWT expiration fail-fast fix (§1).
- E2E test-suite repairs (§2e).

**Partially implemented / verified:**
- Playwright E2E coverage: real, but not a clean full-suite green run in this environment, for the reasons in §2e (not a product defect).
- Frontend unit tests: none exist; not written in this pass (out of scope for a verification task without an explicit instruction to add a suite).

**Not implemented (pre-existing, out of scope for this task):**
- Nothing in the originally-scoped product features was found missing. This was a verification-and-fix pass, not a feature pass; per the user's instruction, no new features were added.

---

## 4. Known limitations

1. **Host JDK mismatch** — only JDK 26 is installed on this machine; the project targets JDK 21. `mvn install` run directly on the host fails a handful of Mockito-based tests for this reason alone. Always build/test through Docker or a JDK 21 toolchain.
2. **Machine resource capacity** — this workstation cannot reliably sustain the full 13-container Docker stack plus a long, real-browser Playwright run at the same time as another local dev session; Docker Desktop crashed once under that combined load during this session.
3. **No frontend unit tests exist** in the repository.
4. **Playwright full-suite result is not a clean pass in this environment**, for the reasons in §2e — concurrent live edits to the onboarding/profile UI during this verification window, compounded by #2.
5. **`optional:configserver:...`** is a deliberate design choice (documented in `docker-compose.yml`, needed for local "Mode 2" dev without config-server running) — it is not being removed. The fix in §1 makes its *failure mode* safe (loud startup failure) rather than removing the optionality itself.

---

## 5. Environment variables

No new environment variables were introduced. Confirmed present and correctly wired in `.env.example` / `infrastructure/config-repo/application.yml`: `JWT_SECRET`, `JWT_ISSUER`, `JWT_ACCESS_EXPIRATION`, `JWT_REFRESH_EXPIRATION`, `GROQ_API_KEY`, `GROQ_BASE_URL`, `GROQ_MODEL`, `MONGODB_URI`, `S3_*`, `REDIS_*`. `.env.example` contains only placeholders (previously flagged and fixed in an earlier task).

## 6. Services changed

| Service | Change |
|---|---|
| `auth-service` | `JwtProperties` — added `@Validated`/`@NotBlank`/`@Positive` so a broken config import fails startup loudly instead of silently issuing expired tokens (§1). |
| `api-gateway` | `JwtVerifier` — explicit constructor check for a missing `issuer`, same rationale. |
| `frontend` (tests only) | Fixed ambiguous `/Resume/` selectors (5 files), stale `EXP-001` assertion, `playwright.config.ts` `workers: 1`. No product code changed in the frontend during this verification pass. |

## 7. Database changes

None.

## 8. API changes

None (no request/response shape changed in this pass — the JWT fix only affects how *reliably* the already-documented `expiresIn` value is honored, not its contract).

## 9. Test results summary

| Layer | Result |
|---|---|
| `mvn install` (JDK 21, Docker) | ✅ Clean, full reactor, 0 errors |
| Frontend `typecheck` | ✅ Clean |
| Frontend `build` | ✅ Clean |
| Docker build (13 services) | ✅ All healthy |
| API-level full journey + security (23 checks) | ✅ 23/23 |
| Playwright `responsive.spec.ts` | ✅ 6/6, every run |
| Playwright `anonymous-and-security.spec.ts` | ✅ security assertions independently confirmed via §2d; suite-level pass rate affected by AI-call timing under load, not by a security defect |
| Playwright full suite (39 tests) | ⚠️ Not a clean pass — see §2e for root causes (resource contention, concurrent live UI edits), none attributable to this session's own changes |

## 10. Docker status

All 13 `--profile app` services currently running and `healthy`:
`config-server`, `discovery-server`, `api-gateway`, `auth-service`, `profile-service`, `jd-service`, `resume-service`, `ai-service`, `assessment-service`, `document-service`, `application-service`, `redis`, `minio`.

## 11. Remaining roadmap

- Re-run the full Playwright suite in a dedicated, idle CI environment once concurrent onboarding/profile UI work (visible as uncommitted changes during this session) lands, for a clean, stable signal.
- Add a frontend unit-test suite (none currently exists).
- Consider making config-server import non-optional in the Docker `app` profile specifically (Mode 1), while keeping it optional for local "Mode 2" dev — would turn the §1 failure class into an immediate container-startup failure instead of relying on `@Validated` to catch it after the fact. Not done here since it's a design change beyond a verification pass's scope; flagged for a deliberate decision.
