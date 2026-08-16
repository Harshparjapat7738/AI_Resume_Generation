# Frontend API Integration

How the React frontend talks to the backend. Full request/response contracts live in
[`API_CATALOG.md`](API_CATALOG.md); this document covers the frontend-specific wiring —
which file calls which endpoint, how auth/onboarding decide where a user lands, and the
screen-to-endpoint map.

---

## 1. Client layer

`frontend/src/services/apiClient.ts` is the single fetch wrapper. Every other `*Api.ts` file
calls `apiFetch<T>(path, init)` — no component ever calls `fetch` directly.

- **Base URL** — `VITE_API_BASE_URL`, empty by default so requests are relative and go
  through Vite's dev proxy (`vite.config.ts`) to the gateway at `:8080`.
- **Auth header** — if an access token is set (`setAccessToken`), every request carries
  `Authorization: Bearer <token>`.
- **Credentials** — `credentials: 'include'` on every request, so the HttpOnly refresh
  cookie travels automatically; no JS ever reads it.
- **Errors** — a non-2xx response is thrown as `ApiError` (`status`, `body.code`,
  `body.message`), never a raw exception string.

One `*Api.ts` module per service, matching its DTOs field-for-field:

| File | Service | Endpoints used |
|---|---|---|
| `authApi.ts` | auth-service | register, login, refresh, logout, me |
| `profileApi.ts` | profile-service | get/update personal info; CRUD for education/experience/skills/projects/certifications/achievements; evidence; `isProfileComplete()` and `computeProfileCompletion()` heuristics |
| `jdApi.ts` | jd-service | submit (paste), fetch-url (URL intake), get, confirm, analysis |
| `assessmentApi.ts` | assessment-service | assess (compute), get (cached read) |
| `applicationApi.ts` | application-service | create/get/list `Application`; generate/get email; generate/get cover letter; cover-letter document-generation fallback prompt |

## 2. Session state

`frontend/src/services/session.ts` owns the "am I logged in" question — see the previous
version of this document for the bootstrap/refresh mechanics (unchanged). What changed is
**what happens after** a session is established.

## 3. Authentication → onboarding → landing flow

This is the part that was wrong before and is the main thing this pass fixed: login/register
used to default straight into the old Evidence page. Now:

```
Register
  ↓ register() → login() → me()
  ↓ a brand-new account has no profile yet
  → ALWAYS /onboarding
        ↓ 8 steps: Personal, Education, Experience, Skills, Projects, Certifications,
        ↓ Achievements, Review — every step is a real profile-service call; every step
        ↓ except Personal is freely skippable ("Skip" vs "Continue" reflects real data,
        ↓ never just whether the step was visited)
        ↓ "Finish profile"
        → ALWAYS / (landing page) — never auto-starts generation

Login
  ↓ login() → me()
  ↓ getProfile() → isProfileComplete(profile)?
  ├─ NO  → /onboarding → (same as above) → /
  └─ YES → the location the user was originally trying to reach
           (router state `from`, set by ProtectedRoute), or / if there wasn't one
```

`isProfileComplete()` (`profileApi.ts`) is a **client-side heuristic** over real data —
profile-service has no "onboarding complete" flag — defined as: a non-blank `fullName` and
at least one experience entry, **unchanged** since the first pass. Education, skills,
projects, certifications and achievements are real, optional enrichment — none of them gate
this redirect, so an account that only ever had personal info + experience (from before this
section existed) keeps working exactly as it did.

`computeProfileCompletion()` (`profileApi.ts`) is a separate, more granular heuristic used
only for the progress bar/percentage shown in onboarding and on `/profile` — all seven
sections (personal + the six evidence sections) weighted equally, each counted complete only
if it holds real persisted data, never because the user merely visited that step.

**Why onboarding always ends at `/` and not the originally-intended page:** this was an
explicit, repeated product decision (a new user should land somewhere they can look around,
not be dropped mid-workflow) even though it means clicking "Generate" from the landing page
again after finishing onboarding. An *existing* user with a complete profile who hits a
protected route while logged out does return to that exact page after login — only the
onboarding path always resolves to the landing page.

## 4. Protected routes

`components/layout/ProtectedRoute.tsx` reads `useSession()`. No session → redirect to
`/login` with `state: { from: location }` so the destination can be restored after login (for
users who don't need onboarding). Routes nested under it: `/onboarding`, `/profile`,
`/dashboard`, `/generate`, `/generate/job`, `/generate/review/:jdId`,
`/generate/processing/:jdId`, `/results/:resumeId`. `/`, `/login`, `/register` are public —
the landing page is never a different page for authenticated users, only its nav changes
(`SiteHeader.tsx`, `AppHeader.tsx` both read `useSession()` directly).

## 5. Logging out from a protected page

`AppHeader.tsx` and `SiteHeader.tsx`'s `handleLogout` use `window.location.assign('/')`, a
real page navigation — **not** react-router's `navigate('/')`. This was a genuine bug found
while testing the profile feature end-to-end: logging out from a protected page (e.g.
`/profile`) with `navigate()` sometimes landed on `/login` instead of the intended `/`.
`ProtectedRoute` reacts to the session clearing and issues its own redirect to `/login`; that
redirect and the explicit `navigate('/')` both originate from the same `handleLogout` call
and React 18 batches every state update inside one event handler into a single render, so
call order between `clearSession()` and `navigate()` doesn't control which one wins — it's a
genuine race, not a code-order bug. A hard navigation sidesteps React Router entirely for
this one transition and always lands on the public landing page.

## 6. Screen → endpoint map

| Screen | File | Calls |
|---|---|---|
| Register | `features/auth/RegisterPage.tsx` | `POST /api/auth/register` → `POST /api/auth/login` → `GET /api/auth/me` → always `/onboarding` |
| Login | `features/auth/LoginPage.tsx` | `POST /api/auth/login` → `GET /api/auth/me` → `GET /api/profile` (completeness check) → `/onboarding` or intended destination |
| Onboarding | `features/onboarding/OnboardingPage.tsx` | `GET /api/profile`, then the same calls as Profile (below), one section per step |
| Profile (anytime) | `features/profile/ProfilePage.tsx` | `GET /api/profile`, `PUT /api/profile`, `POST/PUT/DELETE /api/profile/{education,experience,skills,projects,certifications,achievements}[/{id}]` — via `features/profile-shared/*Manager.tsx`, one component per section, shared by both onboarding and this page |
| Output type (`/generate`) | `features/generate/OutputTypePage.tsx` | none — pure client-side card selector. Resume, Cover Letter and Email each navigate to `/generate/job?type=<GenerationType>`, carried as a query param through every step below; "Generate All" stays disabled (🔒 Coming Soon) |
| JD input (`/generate/job`) | `features/generate/JobDescriptionPage.tsx` | Two modes, one submit button each: **Paste** → `POST /api/jd`; **Job URL** → `POST /api/jd/fetch-url` (server-side fetch with SSRF guard — see §6a). On `JD_URL_BLOCKED`/`JD_VALIDATION_ERROR` the UI shows "Unable to extract this job description from this URL." with a "Paste Job Description Instead" button that switches tabs without losing the typed URL |
| Review (`/generate/review/:jdId`) | `features/generate/ReviewPage.tsx` | `GET /api/jd/{id}`, `POST /api/jd/{id}/confirm`, `GET /api/jd/{id}/analysis`. For URL-sourced JDs, also renders a "Fetched from URL" preview card (source link + Job Title/Company/Location/Experience/Required Skills, each shown only if the page actually published it) using the extra fields `GET /api/jd/{id}` now returns. The confirm button reads "Choose a template" (`RESUME_ONLY`), "Generate my email" (`EMAIL_ONLY`) or "Generate my cover letter" (`COVER_LETTER_ONLY`) — the latter two skip straight to Processing, no template step |
| Processing (`/generate/processing/:jdId`) | `features/generate/ProcessingPage.tsx` | `RESUME_ONLY`: `POST /api/resumes/generate`, then `POST /api/assessment/resume-versions/{id}` (best-effort). `EMAIL_ONLY`/`COVER_LETTER_ONLY`: `POST /api/applications` (creates the `Application`), then `POST /api/applications/{id}/email` or `.../cover-letter` — see §7 |
| Result (`/results/:resumeId`) | `features/results/ResultPage.tsx` | `GET /api/resumes/{id}`, `GET /api/assessment/resume-versions/{id}` (falls back to a "Run ATS analysis" button that calls `POST` if none exists yet), `POST /api/resumes/generate` again for "Regenerate" |
| Email result (`/results/email/:applicationId`) | `features/results/EmailResultPage.tsx` | `GET /api/applications/{id}`, `GET /api/applications/{id}/email`; "Regenerate" calls `POST` again |
| Cover letter result (`/results/cover-letter/:applicationId`) | `features/results/CoverLetterResultPage.tsx` | `GET /api/applications/{id}`, `GET /api/applications/{id}/cover-letter`; "Regenerate" calls `POST` again |
| Dashboard (`/dashboard`) | `features/dashboard/DashboardPage.tsx` | `GET /api/resumes?page=&size=` for the resume list; `GET /api/applications?status=COMPLETED&page=&size=` (filtered client-side to `COVER_LETTER_ONLY`) for a second, independent "Cover letters" list — applications and resumes are separate aggregates (ADR-017), so this is two queries, not one |

The **Evidence** architecture from the original build didn't move or get renamed at the API
level — `profileApi.ts`'s experience functions are unchanged. What changed is where the UI
puts it: `features/profile-shared/ExperienceManager.tsx` is now a shared component used by
both the onboarding wizard and the anytime-editable profile page, instead of its own forced
post-login destination.

## 6a. Job URL intake (fetch-then-extract, never browser-side scraping)

The browser never fetches a job posting directly — it only ever calls
`jd-service`, which does the fetch itself. Flow: user submits a URL →
`jd-service` validates it (`SsrfGuard`: http/https only, default ports only,
DNS-resolves, and every resolved address is rejected if it's loopback,
link-local, site-local/private, multicast, wildcard, carrier-grade NAT, or
IPv6 unique-local) → `JdUrlFetcher` fetches with redirects disabled at the
HTTP-client level and manually follows up to 5 hops, **re-validating each
redirect target through `SsrfGuard` before following it** → response must be
`text/html` and is read with a 3 MB cap → `JobPostingExtractor` looks for a
schema.org `JobPosting` in a `<script type="application/ld+json">` block
(including `@graph`-wrapped and array-wrapped forms) and falls back to
readable visible body text when none is present → the extracted preview
fields are stored on the `JobDescription` and returned to the frontend, which
never sees the raw HTML or performs any parsing itself. See
`docs/ARCHITECTURE_DECISIONS.md` ADR-015 for the full design and the accepted
DNS-rebinding limitation.

## 7. Output-type selection (Resume / Cover Letter / Email / All)

`OutputTypePage.tsx` renders all four cards from the product vision. **Resume**, **Cover
Letter** and **Email Content** are each wired to a real backend capability; only **Generate
All** renders as a visibly disabled "🔒 Coming Soon" card (`GenerationType.ALL` is a real,
storable value with no combined pipeline behind it yet — ADR-017). Whether a card is
clickable is enforced in one place (the `cards` array's `available` flag), so this list is
literally the one place that gates what the product currently supports.

Cover Letter and Email both follow an entirely separate path from Resume through the same
wizard shell: `OutputTypePage` sets `?type=COVER_LETTER_ONLY` / `?type=EMAIL_ONLY` as a query
param (the same survives-a-reload technique `TemplatePage` uses for `templateId`), carried
through `JobDescriptionPage` → `ReviewPage` → `ProcessingPage`, which creates an `Application`
(`POST /api/applications`, application-service) and then calls the matching generate
endpoint. Neither has a template step (`GenerateLayout`'s `COVER_LETTER_STEPS`/`EMAIL_STEPS`
render a 4-step, not 5-step, progress bar). `RESUME_ONLY` is unchanged throughout — every
branch added to `JobDescriptionPage`/`ReviewPage`/`ProcessingPage` defaults to it. See
ARCHITECTURE_DECISIONS.md ADR-019 (email) and ADR-020 (cover letter).

## 8. Assessment triggering

`ProcessingPage.tsx` calls `assessResume()` immediately after `generateResume()` succeeds,
so by the time the user reaches the result page the ATS/JD-fit data is normally already
there. This call is **best-effort**: if it fails, the user still reaches `/results/:id` with
the real resume content, and the result page shows a "Run ATS analysis" button (backed by
the same idempotent `POST`) instead of silently showing nothing or fake data. `assessResume`
is safe to call more than once — assessment-service computes once and caches (see
`docs/API_CATALOG.md` §2).

## 9. Loading / error / success states

Unchanged in pattern from before: TanStack Query's `isLoading`/`isPending` for loading,
`ErrorBanner` (rendering the backend's own safe `message`, never a stack trace) for errors,
cache updates via `queryClient.setQueryData` for success. `GeneratingPage`'s replacement,
`ProcessingPage`, is still an honest indeterminate spinner — two backend calls, neither with
real per-stage progress to report, so no fake staged checklist.

## 10. What's not wired up yet

- File JD intake (uploading a `.pdf`/`.docx` job posting) — `jd-service` only
  implements `TEXT` (paste) and `URL` source types so far; `FILE` is planned.
- Combined (`ALL`) generation — `GenerationType.ALL` is a real, storable value with no
  pipeline behind it (ADR-017).
- Template selection/upload/browse — no template backend of any kind exists; the review
  step shows a static "default format, template selection coming soon" line instead of a
  picker.
  the real generated text content as a `.txt` file client-side (`Blob` + object URL) and
  says so explicitly; it never claims to produce a PDF.
- Google OAuth — no "Sign in with Google" button.
- The ten-check, rendered-document ATS engine from the blueprint — see ADR-014 for the
  seven-check, content-based engine implemented instead.
- Profile version history and resume import — `/api/profile/versions` and
  `/api/profile/import` remain planned (`docs/API_CATALOG.md` §3); every profile edit is a
  live, immediate mutation with no snapshot/undo.

## JD optimization (ADR-033)

| Frontend file | Calls |
|---|---|
| `services/jdApi.ts` → `optimizeForJd(id, refresh)` | `POST /api/jd/{id}/optimize?refresh=` |
| `services/jdApi.ts` → `getJdOptimization(id)` | `GET /api/jd/{id}/optimization` |
| `features/generate/ProcessingPage.tsx` | calls `optimizeForJd`, then redirects to `/results/optimization/{jobDescriptionId}` |
| `features/results/OptimizationResultPage.tsx` | `getJdOptimization`, plus `getAnalysis` (requirement text) and `getProfile` (evidence labels) purely for display |
| `services/assessmentApi.ts` → `getAssessment(jobDescriptionId)` | `GET /api/assessment/{jobDescriptionId}` |

Errors follow the platform envelope: `409 JD_NOT_CONFIRMED`, `422 VALIDATION_ERROR` (no
requirements, or an empty profile), `429 RATE_LIMIT_EXCEEDED`, `502 AI_GENERATION_FAILED`,
`404` for anything not owned by the caller.

Email endpoints and their frontend callers are unchanged.
