# API Catalog

Every implemented endpoint, documented before it is considered done. Updated in the same
pull request that adds, changes or removes an API.

**Current state:** a first vertical slice is implemented ahead of the milestone-by-milestone
plan — `auth-service` (register/login/refresh/logout/me, plus Google OAuth sign-in),
`profile-service` (personal info,
education, experience, skills, projects, certifications and achievements — all six
evidence-bearing sections), `jd-service` (text intake, SSRF-guarded URL intake, confirm,
analysis — see ADR-015), `resume-service` (synchronous generation + history, plus the
built-in template catalogue — see ADR-013, ADR-016), `document-service` (real Resume PDF
rendering against the selected built-in template — see ADR-018), `assessment-service` (ATS +
JD-fit scoring, scoped to structured content rather than a rendered document — see ADR-014)
and `application-service` (the central `Application` aggregate — create, attach a resume,
list, get, status lifecycle and history, all by reference, see ADR-017 — plus grounded email
generation, ADR-019, grounded cover-letter generation, ADR-020, and combined "Generate All"
generation, ADR-022) are real. Everything else in §3 remains a planned contract, moving into
§2 as it ships. JD file intake, resume version history, custom-upload and online templates,
profile versions/import, Gmail-draft generation, and DOCX rendering are not yet implemented.
(`notification-service`, which would have owned transactional/system email, was removed
before it grew past a skeleton — ADR-035; the candidate-facing application-email feature
above is unrelated and unaffected.)

**Base URL** — everything public goes through the gateway: `http://localhost:8080`.
Business services are not reachable from the host (ADR-007).

---

## 1. Conventions

**Authentication.** `Authorization: Bearer <accessToken>` on every route except those
listed as Public. The gateway verifies the token, strips any client-supplied
`X-User-Id`/`X-User-Roles`, and sets them itself from the verified claims.

**Authorization.** Identity from the gateway is not authorisation. Each service checks
that the target document's `userId` matches the caller. A resource owned by someone else
returns **404**, never 403 — 403 would confirm the ID exists and allow enumeration.

**Correlation.** Send `X-Correlation-Id` to join your logs to the server's; if omitted the
gateway generates one. It is echoed on every response, including errors.

**Error envelope.** Identical across every service:

```json
{
  "timestamp": "2026-08-09T10:15:30Z",
  "status": 400,
  "code": "JD_VALIDATION_ERROR",
  "message": "Unable to process the supplied job description.",
  "path": "/api/jd",
  "correlationId": "8f14e45f-ceea-467a-9f0b-2c2f0d1a3b44",
  "fieldErrors": [{ "field": "url", "message": "must be a valid URL" }]
}
```

`code` is the stable contract — switch on it, not on `message`. Values come from
`platform-common/error/ErrorCode.java`:

| Code | HTTP | Meaning |
|---|---|---|
| `VALIDATION_ERROR` | 400 | Request body or parameters failed validation; see `fieldErrors` |
| `MALFORMED_REQUEST` | 400 | Body could not be parsed |
| `JD_VALIDATION_ERROR` | 400 | Content is not a usable job description |
| `JD_URL_BLOCKED` | 400 | URL rejected by the SSRF guard |
| `FILE_REJECTED` | 400 | Upload failed MIME, signature, size or malware checks |
| `AUTH_REQUIRED` / `AUTH_TOKEN_MISSING` / `AUTH_TOKEN_INVALID` | 401 | Missing, malformed or expired token |
| `ACCESS_DENIED` | 403 | Authenticated but not permitted |
| `RESOURCE_NOT_FOUND` | 404 | Absent, or owned by another user |
| `ROUTE_NOT_FOUND` | 404 | No gateway route matches |
| `CONFLICT` / `JD_NOT_CONFIRMED` | 409 | Conflicts with current state |
| `AI_GROUNDING_FAILED` | 422 | Generated content could not be verified against the profile |
| `RATE_LIMIT_EXCEEDED` | 429 | Token bucket exhausted |
| `INTERNAL_ERROR` / `DOCUMENT_RENDER_FAILED` | 500 | Unexpected failure |
| `AI_GENERATION_FAILED` | 502 | Groq unavailable or exhausted retries |
| `UPSTREAM_UNAVAILABLE` / `SERVICE_UNAVAILABLE` | 503 | A dependency is down |

**Rate limits.** Redis token bucket at the gateway. Exceeding a bucket returns 429 with
the standard envelope. Limits per route are listed in `docs/CODEBASE.md` §2.

**Pagination.** List endpoints take `page` (0-based) and `size` (default 20, max 100) and
return `{ content, page, size, totalElements, totalPages }`.

---

## 2. Implemented endpoints

### Platform — all services

---

**GET** `/actuator/health`

**Service** every service · **Purpose** liveness and readiness for Docker, Compose and
orchestrators · **Authentication** none (internal network only; not routed through the
gateway for business services)

Response `200`:

```json
{ "status": "UP" }
```

`show-details: never`, so component detail, hostnames and connection strings are not
disclosed. `/actuator/health/liveness` and `/actuator/health/readiness` are also available;
Docker healthchecks use readiness.

**Status codes** `200` UP · `503` DOWN

---

**GET** `/actuator/info` — build and version metadata. No authentication. `200`.

**GET** `/actuator/prometheus` — Micrometer metrics in Prometheus text format, scraped by
the Prometheus container. Not exposed to the internet. `200`.

---

### Gateway behaviour

The gateway implements no endpoint of its own, but its cross-cutting responses are part of
the public contract and are tested as such.

---

**ANY** `/api/**` — unauthenticated request to a protected route

**Service** api-gateway · **Authentication** required · **Purpose** reject before any
business service is contacted

Response `401`:

```json
{
  "timestamp": "2026-08-09T10:15:30Z",
  "status": 401,
  "code": "AUTH_TOKEN_MISSING",
  "message": "Authentication is required.",
  "path": "/api/profile"
}
```

`AUTH_TOKEN_INVALID` is returned instead when a token is present but fails signature,
issuer or expiry verification. The response never indicates *which* check failed.

---

**ANY** `/api/**` — rate limit exceeded → `429` with `RATE_LIMIT_EXCEEDED`.
**ANY** unmatched path → `404` with `ROUTE_NOT_FOUND`.
**ANY** route whose target has no healthy instance → `503` with `SERVICE_UNAVAILABLE`.

---

**GET** `/actuator/gateway/routes`

**Service** api-gateway · **Purpose** inspect the live routing table while debugging ·
**Authentication** none today because the gateway's actuator port is not published beyond
the Docker network. **This must be secured or disabled before production** — tracked in
Milestone 9.

---

### auth-service

---

**POST** `/api/auth/register` — Public

Request: `{ "email": "a@b.com", "password": "at-least-8-chars", "displayName": "Ada Lovelace" }`

Response `201`: `{ "userId": "...", "email": "a@b.com" }`

**Status codes** `201` · `400 VALIDATION_ERROR` · `409 CONFLICT` (email already registered)

---

**POST** `/api/auth/login` — Public

Request: `{ "email": "a@b.com", "password": "..." }`

Response `200`: `{ "accessToken": "<jwt>", "expiresIn": 900 }` and a `Set-Cookie: refreshToken=…`
(HttpOnly, `Path=/api/auth`, `SameSite=Lax`; `Secure` when `REFRESH_COOKIE_SECURE=true`).

**Status codes** `200` · `400 VALIDATION_ERROR` (wrong email/password — deliberately not
`401`, to avoid confirming an account exists)

---

**POST** `/api/auth/refresh` — Refresh cookie (no bearer token)

Reads the `refreshToken` cookie, rotates it (old token revoked, successor issued in the same
family), and returns a new access token in the same shape as login. Presenting an
already-rotated token revokes the whole family and returns `401`.

**Status codes** `200` · `401` (missing/invalid/reused token)

---

**POST** `/api/auth/logout` — Bearer

Revokes the refresh-token family tied to the presented cookie and clears it. `204` even if
no cookie was present.

**GET** `/api/auth/me` — Bearer

Response `200`: `{ "userId": "...", "email": "...", "displayName": "...", "roles": ["ROLE_USER"] }`

---

**GET** `/api/auth/oauth2/authorize/google` — Public

Begins Authorization Code + PKCE (docs/EXTERNAL_APIS.md "Google OAuth 2.0"). Not a JSON
endpoint — a full browser navigation that `302`s to Google's consent screen. `302` to a
frontend error page instead if Google sign-in isn't configured (`GOOGLE_CLIENT_ID`/
`GOOGLE_CLIENT_SECRET` unset — email/password keeps working either way).

**GET** `/api/auth/oauth2/callback/google` — Public

Google redirects here with `code`+`state` (or `error` if the user declined consent).
Validates `state` (single-use, Redis-backed — a replay or unknown value fails the same as an
expired one), exchanges `code` for tokens, verifies the ID token's signature/issuer/audience/
expiry, resolves or creates the account (matched first by linked `oauth_accounts` row, then
by a verified-email match against an existing password account, otherwise a new OAuth-only
account is created), sets the refresh cookie exactly as `/login` does, and `302`s to the
frontend. Every failure — expired state, unverified email, a Google error — ends in the same
kind of redirect (`/login?error=google_oauth`), never a JSON error a browser navigation can't
act on.

---

### profile-service

Personal information, education, experience, skills, projects, certifications and
achievements are all implemented — every section follows the same shape and evidence-id
pattern. Profile versions and resume import remain in §3.

---

**GET** `/api/profile` — Bearer. Creates an empty profile on first access. Returns
`{ personalInformation, education[], experiences[], skills[], projects[], certifications[], achievements[] }`.

**PUT** `/api/profile` — Bearer. Body: `{ fullName, headline?, email?, phone?, links[] }`.

Each of the six sections below follows the identical `POST /{section}` (create, assigns and
returns a stable `evidenceId`) / `PUT /{section}/{evidenceId}` (update; id is immutable,
`404` if not owned) / `DELETE /{section}/{evidenceId}` shape:

| Section | Path | Evidence prefix | Body fields |
|---|---|---|---|
| Experience | `/api/profile/experience[/{id}]` | `EXP` | `company, title, employmentType?, start?, end?, current, location?, bullets[], technologies[], metrics[]` |
| Education | `/api/profile/education[/{id}]` | `EDU` | `institution, degree, field?, start?, end?, grade?, description?` |
| Skills | `/api/profile/skills[/{id}]` | `SKILL` | `name, category?, proficiency?, yearsOfExperience?` — `category` is free text, not an enum |
| Projects | `/api/profile/projects[/{id}]` | `PROJ` | `name, description?, role?, technologies[], metrics[], githubUrl?, liveUrl?, start?, end?` |
| Certifications | `/api/profile/certifications[/{id}]` | `CERT` | `name, issuer, issuedOn?, expiresOn?, credentialId?, credentialUrl?` |
| Achievements | `/api/profile/achievements[/{id}]` | `ACH` | `title, description?, date?` |

All six return the full `ProfileResponse` (every section), same as `PUT /api/profile`.

**GET** `/api/profile/evidence` — Bearer. Returns the ID-labelled inventory ai-service
consumes, combined across **all six sections** (not just experience):
`[{ evidenceId, type, title, organisation, description, technologies[], metrics[], startDate, endDate }]`.
`type` is one of `EXPERIENCE · EDUCATION · SKILL · PROJECT · CERTIFICATION · ACHIEVEMENT`.

**"My Templates"** (ADR-034) — a user's own uploaded Resume/Cover Letter files, a separate
resource from the evidence sections above (`templates` collection, many rows per user, unlike
the one-per-user `profiles` document). Uploaded once, referenced (never re-uploaded) at
JD-optimization handoff time — see the frontend's `OptimizationResultPage`. No AI/structural
analysis of any kind is performed on an uploaded file; it is stored and returned exactly as
uploaded.

**GET** `/api/profile/templates` — Bearer. Returns every template the caller owns, newest
first: `[{ id, name, fileName, fileType, documentType, isDefault, byteSize, createdAt, updatedAt }]`.
`fileType` is `PDF | DOCX`; `documentType` is `RESUME | COVER_LETTER | BOTH`. Never includes the
internal storage key/bucket — there is no public file URL.

**GET** `/api/profile/templates/{id}` — Bearer. One template; `404` if not owned.

**POST** `/api/profile/templates` — Bearer, `multipart/form-data`: `file` (PDF or DOCX, ≤5 MB),
`name?` (falls back to the filename minus its extension), `documentType?` (default `RESUME`).
Rejects (`FILE_REJECTED`) an empty file, an unsupported extension, a file whose magic-byte
signature doesn't match its extension (mislabelling is never trusted), one over the size limit,
or an exact duplicate (same SHA-256) of a file the caller already saved. The very first template
a user ever saves becomes their default automatically.

**PUT** `/api/profile/templates/{id}` — Bearer. Body: `{ name }`. `404` if not owned.

**POST** `/api/profile/templates/{id}/default` — Bearer. Makes this the caller's one default
template, unsetting whichever template (if any) previously held it — a single default per user,
not scoped per `documentType` (ADR-034 — there is no separate resume-flow/cover-letter-flow to
pair a type-scoped default with any more). `404` if not owned.

**DELETE** `/api/profile/templates/{id}` — Bearer. Removes the stored file and its metadata row
together. `404` if not owned.

**GET** `/api/profile/templates/{id}/download` — Bearer. Streams the original bytes back
byte-for-byte, with `Content-Disposition: attachment` and the original filename. `404` if not
owned — the only way to ever reach a stored file.

---

### jd-service

`sourceType = TEXT` and `URL` are both implemented — file upload remains in §3.

---

**POST** `/api/jd` — Bearer. Body: `{ "jobDescriptionText": "..." }` (50–60,000 chars).
Response `201`: `{ id, status: "EXTRACTED", sourceType: "TEXT", title, company, createdAt }`.

**POST** `/api/jd/fetch-url` — Bearer. Body: `{ "url": "https://…" }`. Fetches the URL
server-side through the SSRF guard (`SsrfGuard` + `JdUrlFetcher` — scheme/port/private-IP
validation, every redirect hop re-validated, `text/html`-only, 3 MB cap, 5s/10s timeouts —
see ARCHITECTURE_DECISIONS.md ADR-015), extracts it (schema.org `JobPosting` JSON-LD when
the page provides it, generic readable text otherwise), and stores it exactly like a pasted
JD — same response shape (`201`, `sourceType: "URL"`), same confirm/analysis flow after.

**Status codes** `201` · `400 VALIDATION_ERROR` (malformed request body) ·
`400 JD_URL_BLOCKED` (scheme/port/private-network address rejected by the SSRF guard, or too
many redirects) · `400 JD_VALIDATION_ERROR` (fetch failed, non-200, non-HTML content-type,
oversized, or the extracted text is shorter than 50 characters) — the frontend shows the
same "Unable to extract this job description from this URL" message for both `JD_URL_BLOCKED`
and this case, since the distinction isn't actionable for the user.

**GET** `/api/jd/{id}` — Bearer. Adds `rawText`, `currentVersion`, `sourceUrl`, and —
only ever non-null for a `URL`-sourced JD whose page had `JobPosting` JSON-LD —
`location`, `skillsSummary`, `experienceSummary`. `404` if not owned.

**POST** `/api/jd/{id}/confirm` — Bearer. Idempotent; sets `status = CONFIRMED`.

**GET** `/api/jd/{id}/analysis` — Bearer. `409 JD_NOT_CONFIRMED` unless confirmed. On first
call, calls `ai-service` (`POST /internal/ai/jd-analysis`) and caches the result on the JD
version; subsequent calls return the cached analysis. Response:
`{ jobDescriptionId, title, company, seniority, keywords[], requirements[] }`.

**POST** `/api/jd/{id}/optimize` — Bearer. `?refresh=false`. The JD-optimization operation
(ADR-033) that replaced resume/cover-letter generation. Requires a confirmed JD; loads its
cached analysis plus the caller's verified evidence from profile-service, calls `ai-service`
(`POST /internal/ai/jd-optimization`), and persists the validated result. Returns targeting
data, never a document. Re-reads an existing result for the same JD version unless
`refresh=true` (use after a profile edit). `409 JD_NOT_CONFIRMED` unless confirmed;
`422 VALIDATION_ERROR` when the JD has no requirements or the profile has no evidence;
`502 AI_GENERATION_FAILED` / `429 RATE_LIMIT_EXCEEDED` on provider failure. Response:
`{ id, jobDescriptionId, optimisation{ targetRole, targetCompany, keywords[], requirementMatches[],
missingRequirements[], emphasis[] }, citedEvidenceIds[], createdAt }`.

**GET** `/api/jd/{id}/optimization` — Bearer. The current optimization for this JD, `404` when
none has been computed. Same response shape.

**GET** `/api/jd` — Bearer. `?page&size` (defaults 0/20, size capped at 100). Returns
`{ content[], page, size, totalElements, totalPages }`.

---

### assessment-service

**Scope deviation from `docs/DATABASE.md` §3 — see ARCHITECTURE_DECISIONS.md ADR-014.** The
ATS checks score the resume's structured content, not a rendered PDF/DOCX (no
document-service exists to produce one yet). The JD-fit compatibility formula matches the
documented blueprint exactly.

---

**POST** `/api/assessment/resume-versions/{resumeVersionId}` — Bearer

Computes ATS + JD-fit scoring for an already-generated resume version. Idempotent — the
first call computes and persists; every subsequent call (including a second `POST`) returns
the cached result. Internally fetches the resume (`resume-service`), its JD analysis
(`jd-service`) and the caller's profile (`profile-service`) via Eureka, not the gateway.

Response `200`:

```json
{
  "resumeVersionId": "...",
  "atsScore": 76.4,
  "atsChecks": [
    { "checkId": "CONTACT_INFO_PRESENT", "label": "Contact information", "weight": 15.0,
      "passRatio": 1.0, "detail": "Name and contact details are present.", "earned": 15.0 }
  ],
  "engineVersion": "content-v1",
  "compatibilityScore": 0.57,
  "coverage": 0.67,
  "keywordMatch": 0.43,
  "seniorityMatch": 0.57,
  "recency": 0.4,
  "requirementMatches": [
    { "requirementId": "REQ-001", "text": "...", "type": "HARD_REQUIRED",
      "matchStrength": "STRONG", "evidenceIds": ["EXP-001"] }
  ],
  "unmetHardRequirements": [],
  "matchedKeywords": ["Java", "Spring Boot"],
  "missingKeywords": ["PostgreSQL", "AWS"],
  "readinessBand": "STRETCH",
  "bandRule": "compatibilityScore >= 0.40",
  "recommendations": [
    { "type": "KEYWORD", "severity": "MEDIUM",
      "message": "Consider highlighting genuine experience with: PostgreSQL, AWS — only if you actually have it.",
      "relatedRequirementId": null }
  ],
  "assessedAt": "..."
}
```

The seven ATS checks (weights sum to 100): `CONTACT_INFO_PRESENT` (15), `SUMMARY_PRESENT`
(10), `EXPERIENCE_SECTION_PRESENT` (15), `DATE_CONSISTENCY` (15),
`BULLET_LENGTH_SUITABILITY` (15), `KEYWORD_PRESENCE` (15), `GROUNDING_INTEGRITY` (15) —
the last one reuses the grounding report `ai-service` already computed during generation.

`compatibilityScore = 0.50·coverage + 0.20·keywordMatch + 0.20·seniorityMatch + 0.10·recency`.
`readinessBand` is `STRONG` (≥0.85 and no unmet hard requirements) · `COMPETITIVE` (≥0.65) ·
`STRETCH` (≥0.40) · `WEAK_FIT` (below). `recommendations` never instructs adding a skill or
experience the candidate doesn't have — every message is qualified ("only if you actually
have it") or reports a gap.

**Status codes** `200` · `404` (resume not owned) · `409 JD_NOT_CONFIRMED` (shouldn't occur —
generation itself requires confirmation) · `503 UPSTREAM_UNAVAILABLE`

---

**GET** `/api/assessment/resume-versions/{resumeVersionId}` — Bearer. Same response shape.
`404` if no assessment has been computed yet (the frontend offers a "Run ATS analysis"
button in that case rather than auto-triggering it silently).

---

### application-service

**The central `Application` aggregate — see ARCHITECTURE_DECISIONS.md ADR-017.** Stores
references only (job, generation type, template, resume, cover letter, email, assessment),
never a copy of what another service owns. `RESUME_ONLY`, `EMAIL_ONLY` (ADR-019),
`COVER_LETTER_ONLY` (ADR-020) and `ALL` ("Generate All", ADR-022) all reach `COMPLETED`
today — `ALL` once all three artifacts have attached. Each of the three outputs `ALL`
requires is generated independently (the same generate/attach calls the single-output types
use — see `POST .../resume`, `POST .../email`, `POST .../cover-letter` below), so one failing
never blocks or hides the other two; see `resumeError`/`coverLetterError`/`emailError` and
`POST .../outputs/{output}/failed` below for how a per-output failure is recorded and
retried.

---

**POST** `/api/applications` — Bearer

```json
{
  "jobDescriptionId": "...",
  "generationType": "RESUME_ONLY",
  "templateId": "modern-ats",
  "resumeVersionId": "..."
}
```

`generationType` is required; `templateId` and `resumeVersionId` are optional. Verifies
`jobDescriptionId` (jd-service), `templateId` if supplied (resume-service's template
catalogue, ADR-016) and `resumeVersionId` if supplied (resume-service, and it must belong to
the same `jobDescriptionId`) before saving. `status` is always derived, never accepted from
the caller — see the response shape below.

Response `201`:

```json
{
  "id": "...",
  "jobDescriptionId": "...",
  "jobTitle": "Backend Engineer",
  "company": "Acme",
  "generationType": "RESUME_ONLY",
  "templateId": "modern-ats",
  "resumeVersionId": "...",
  "coverLetterVersionId": null,
  "emailId": null,
  "assessed": true,
  "status": "COMPLETED",
  "failureCode": null,
  "resumeError": null,
  "coverLetterError": null,
  "emailError": null,
  "createdAt": "...",
  "updatedAt": "..."
}
```

`status` is derived per `generationType` (`Application.deriveStatus()`): `DRAFT` until that
type's required artifact(s) exist, `COMPLETED` once they do (`RESUME_ONLY` → resume,
`EMAIL_ONLY` → email, `COVER_LETTER_ONLY` → cover letter, `ALL` → all three), otherwise
`PROCESSING`. `resumeError`/`coverLetterError`/`emailError` are a second, independent axis on
top of `status` — set when that specific output's own generate call failed, cleared the
moment it next succeeds, never forcing `status` itself to `FAILED` (see ADR-022): two outputs
can be `COMPLETED`-worthy while a third is mid-retry, and `status` only reflects what's
missing, not what broke. `assessed` is
best-effort — a missing assessment never blocks creating or completing the application, the
same way the frontend's own assessment call is non-fatal.

**Status codes** `201` · `400 VALIDATION_ERROR` (resume belongs to a different JD) ·
`404` (JD, template or resume not owned) · `503 UPSTREAM_UNAVAILABLE`

---

**POST** `/api/applications/{id}/resume` — Bearer. `{ "resumeVersionId": "..." }`. Attaches or
replaces the resume reference on an existing application and recomputes `status`. Same checks
and error codes as the `resumeVersionId` path of `POST /api/applications`. This is the call
the "Generate All" flow makes after generating the resume (unchanged) via resume-service,
mirroring how it already generated a resume for `RESUME_ONLY`.

---

**POST** `/api/applications/{id}/outputs/{output}/failed` — Bearer. `{ "reason": "..." }`.
`{output}` is one of `resume`, `coverLetter`, `email`. Records that one output's own
generate call failed — see ADR-022. Never changes `status`, only the matching
`resumeError`/`coverLetterError`/`emailError`; a subsequent successful `POST .../resume`,
`.../email` or `.../cover-letter` clears it. Used by the frontend's "Generate All"
orchestration (never by the single-output flows, which surface their own call's error
directly and have nothing else to attach a per-output reason to).

**Status codes** `200` · `400 VALIDATION_ERROR` (`{output}` isn't one of the three) · `404`
(not owned)

---

**GET** `/api/applications/{id}` — Bearer. Full detail, same shape as the `POST` response.
`404` if not owned.

---

**GET** `/api/applications` — Bearer. Paged history for the dashboard.
`?status=DRAFT|PROCESSING|COMPLETED|FAILED` filters; `?page=&size=` (`size` capped at 100).
Rows omit `content` but, unlike the plain single-output types, still carry
`resumeVersionId`/`coverLetterVersionId`/`emailId`/`resumeError`/`coverLetterError`/
`emailError` (null for the other generation types) so the dashboard can render a `GenerationType.ALL`
row's per-output status without an extra request per row.

---

**PATCH** `/api/applications/{id}/status` — Bearer. `{ "status": "FAILED", "note": "..." }`.
Enforces a legal-transition table (`DRAFT`/`FAILED → PROCESSING`,
`PROCESSING → COMPLETED`/`FAILED`, `FAILED → DRAFT`) and appends a row to
`application_status_history`; `COMPLETED` is terminal. `note` becomes `failureCode` when
transitioning to `FAILED`.

**Status codes** `200` · `404` (not owned) · `409 CONFLICT` (illegal transition, e.g. any move
out of `COMPLETED`)

---

**GET** `/api/applications/{id}/status-history` — Bearer. Full transition timeline, newest
first: `[{ "fromStatus", "toStatus", "note", "changedAt" }]`.

---

**POST** `/api/applications/{id}/email` — Bearer. See ARCHITECTURE_DECISIONS.md ADR-019.

No request body — every input is already on the `Application` (job title, company) or fetched
from profile-service (evidence, candidate name). Requires `generationType` to be
`EMAIL_ONLY` or `ALL`, and a
confirmed job title on the application. Calling this again **regenerates**: a new version is
persisted and `Application.emailId` repoints at it, the same "call generate again" pattern
`POST /api/resumes/generate` uses.

Response `200`:

```json
{
  "id": "...",
  "applicationId": "...",
  "subject": "Application for Backend Engineer at Acme - Jane Doe",
  "body": "Dear Hiring Manager,\n\nI am writing to express my interest in the Backend Engineer role at Acme. I have led backend systems handling significant production traffic.\n\nMy resume is attached for your review, and I would welcome the opportunity to discuss this role further.\n\nSincerely,\nJane Doe",
  "highlights": [
    { "text": "I have led backend systems handling significant production traffic.", "evidenceIds": ["EXP-004"] }
  ],
  "grounding": { "passed": true, "violations": [], "checkedStatements": 2 },
  "removedParagraphs": [],
  "version": 1,
  "createdAt": "..."
}
```

`subject` and the greeting/closing/sign-off frame of `body` are assembled deterministically —
never generated — from the application's own verified `jobTitle`/`company` and the candidate's
real name; only the sentence(s) inside `body` that came from `highlights` are model-written,
and only after passing the same grounding check as every other generated statement in this
product. A paragraph the grounding degrade path removes is listed in `removedParagraphs` and
replaced in `body` with one deterministic, trusted fallback sentence — the email is never left
with a gap.

**Status codes** `200` · `400 VALIDATION_ERROR` (wrong `generationType`, no confirmed job
title, or no evidence in the profile yet) · `404` (not owned) · `502 AI_GENERATION_FAILED`

---

**GET** `/api/applications/{id}/email` — Bearer. Same response shape as the `POST` above —
the latest version. `404` if no email has been generated for this application yet.

---

**POST** `/api/applications/{id}/cover-letter` — Bearer. See ARCHITECTURE_DECISIONS.md
ADR-020.

No request body. Requires `generationType` to be `COVER_LETTER_ONLY` or `ALL`, the job description
confirmed and analysed (reuses `JD_NOT_CONFIRMED` if not), and at least one evidence item on
the profile. Runs the same two-stage pipeline resume-service uses (evidence selection, then
content) against ai-service, orchestrated from application-service. Calling this again
**regenerates**: a new version is persisted and `Application.coverLetterVersionId` repoints
at it, the same "call generate again" pattern `POST /api/resumes/generate` and
`POST .../email` both use.

Response `200`:

```json
{
  "id": "...",
  "applicationId": "...",
  "jobDescriptionId": "...",
  "jobTitle": "Backend Engineer",
  "company": "Acme",
  "version": 1,
  "content": {
    "greeting": "Dear Hiring Manager,",
    "openingParagraph": { "text": "I am excited to apply for the Backend Engineer role at Acme...", "evidenceIds": ["EXP-004"] },
    "bodyParagraphs": [
      { "text": "With experience building order-processing services in Java and Spring Boot...", "evidenceIds": ["EXP-004"] }
    ],
    "closingParagraph": { "text": "I look forward to the opportunity to discuss my application...", "evidenceIds": ["EXP-004"] },
    "signOff": "Sincerely,"
  },
  "grounding": { "passed": true, "violations": [], "checkedStatements": 3 },
  "removedParagraphs": [],
  "createdAt": "..."
}
```

`greeting`/`signOff` are plain strings, never grounding-checked (boilerplate, not a factual
claim). `openingParagraph`/each entry of `bodyParagraphs`/`closingParagraph` are each checked
the same way a resume bullet is — every statement must cite the evidence it draws on, and the
target job title/company may be named without a citation (`GroundingValidator`'s 3-arg
overload, ADR-020) since they're real, user-confirmed facts, not evidence. A paragraph that
fails grounding twice is dropped entirely (listed in `removedParagraphs`) rather than shown
unverified — unlike email's deterministic-fallback approach, since a cover letter has no safe
generic sentence to fall back to for an arbitrary paragraph.

**Status codes** `200` · `400 VALIDATION_ERROR` (wrong `generationType`, no evidence, or no
extracted requirements) · `404` (not owned) · `409 JD_NOT_CONFIRMED` · `502
AI_GENERATION_FAILED`

---

**GET** `/api/applications/{id}/cover-letter` — Bearer. Same response shape as the `POST`
above — the latest version. `404` if no cover letter has been generated for this application
yet.

**GET** `/api/applications/cover-letter-versions/{id}` — Bearer. A specific cover-letter
version by its own id (not "the latest for some application") — reached by document-service
(Feign, internal network) to render PDF/DOCX from (ADR-028). `404` if not owned.

**GET** `/api/applications/cover-letter-versions/{id}/fallback-prompt?format=PDF|DOCX`
(default `PDF`) — Bearer. The document-generation fallback: a complete, ready-to-paste
external-AI prompt built deterministically from this cover-letter version's already-validated
content, for when document-service could not produce a PDF/DOCX after its allowed retry —
mirrors resume-service's `GET /api/resumes/{id}/fallback-prompt` exactly. Never calls
ai-service; always available regardless of whether a render actually failed (stateless).
Response: `{ coverLetterVersionId, outputFormat, prompt }`. `404` if not owned.

---

## 3. Planned endpoints

Contract agreed; implemented in the milestone shown. Each moves to §2 with full request,
response, validation and error documentation when it ships.

### Milestone 2 — auth-service

Register/login/refresh/logout/me and Google OAuth are all implemented — see §2. Nothing
remains planned for this milestone.

### Milestone 3 — profile-service

Personal info, education, experience, skills, projects, certifications and achievements are
all implemented — see §2. Still planned:

| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/api/profile/versions` | Bearer | Profile version history (immutable snapshots). |
| POST | `/api/profile/import` | Bearer | Import an existing resume; returns parsed items for review — nothing is saved unconfirmed. |

### Milestone 4 — jd-service

Text intake, URL intake, confirm and analysis are implemented — see §2. Still planned:

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/jd/upload` | Bearer | Submit a PDF/DOCX/TXT file (multipart, ≤5 MB). |

### Milestone 7 — assessment-service

Compute and read are implemented, scoped to structured content (ADR-014) — see §2. Still planned:

| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/api/assessment/{resumeId}` | Bearer | Convenience read: latest version's assessment, without knowing the version id. |

### Milestone 8 — application-service

The `Application` aggregate itself — create, attach a resume, list, get, status transitions
and history — is implemented; see §2 and ARCHITECTURE_DECISIONS.md ADR-017. Email generation
(`POST`/`GET /api/applications/{id}/email`) is implemented; see §2 and ADR-019. Cover-letter
generation (`POST`/`GET /api/applications/{id}/cover-letter`) is implemented; see §2 and
ADR-020. Combined "Generate All" generation (`GenerationType.ALL`, one `Application`, all
three outputs, independent per-output failure tracking and retry via
`POST .../outputs/{output}/failed`) is implemented; see §2 and ADR-022. Still planned:

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/applications/{id}/gmail-draft` | Bearer | Create a Gmail **draft**. Never sends. |

### Milestone 9 — privacy

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/account/export` | Bearer | Request a full data export (async). |
| DELETE | `/api/account` | Bearer | Request erasure across all services. |

---

## 4. Internal APIs

Not routed through the gateway and not reachable from a browser (ADR-012). Called only
service-to-service on the internal network. Adding a public route requires a new ADR.

| Method | Path | Service | Purpose | Status |
|---|---|---|---|---|
| GET | `/internal/ai/status` | ai-service | Configuration and connectivity diagnostic | ✅ Implemented |
| POST | `/internal/ai/jd-analysis` | ai-service | Semantic JD understanding | ✅ Implemented |
| POST | `/internal/ai/evidence-selection` | ai-service | Stage 1: requirement → evidence IDs | ✅ Implemented |
| POST | `/internal/ai/resume-content` | ai-service | Stage 2: grounded content generation | ✅ Implemented |
| POST | `/internal/ai/cover-letter` | ai-service | Grounded cover-letter content — see ADR-020 | ✅ Implemented |
| POST | `/internal/ai/email-content` | ai-service | Grounded application-email highlight paragraph(s) — see ADR-019 | ✅ Implemented |

---

**GET** `/internal/ai/status` — port 8085

Reports whether Groq is configured and reachable. Useful when bringing the stack up: a
missing or wrong API key otherwise only surfaces on a user's first generation.

Response `200`:

```json
{
  "groqConfigured": true,
  "maskedApiKey": "gsk_…9f2a",
  "model": "openai/gpt-oss-120b",
  "baseUrl": "https://api.groq.com/openai/v1",
  "promptVersions": { "jd-analysis": 1, "evidence-selection": 1, "resume-content": 1 },
  "reachable": true,
  "detail": "Groq responded successfully."
}
```

The key is always masked. `reachable: false` with a `detail` string is returned rather
than an error status, so the diagnostic still answers when Groq is down.

---

**POST** `/internal/ai/jd-analysis`

Extracts structured requirements from untrusted job-description text.

Request:

```json
{ "jobDescriptionText": "Senior Java Developer...", "promptVersion": null }
```

`jobDescriptionText` is 50–60,000 characters. `promptVersion` pins a prompt version for
reproducibility; null uses the latest.

Response `200` — `analysis` matches `schemas/jd-analysis.schema.json`:

```json
{
  "analysis": {
    "isJobPosting": true,
    "jobTitle": "Senior Java Developer",
    "company": "Acme",
    "seniority": "Senior",
    "requirements": [
      { "requirementId": "REQ-001", "text": "5+ years Java",
        "type": "HARD_REQUIRED", "weight": 5, "normalisedTerms": ["java"] }
    ],
    "keywords": ["java", "spring boot"]
  },
  "provenance": { "promptVersion": "jd-analysis@v1", "model": "openai/gpt-oss-120b",
                  "totalTokens": 1834, "regenerated": false }
}
```

When the text is not a job posting, `isJobPosting` is false with a `notReason` — the caller
turns that into `JD_VALIDATION_ERROR`.

**Status codes** `200` · `400 VALIDATION_ERROR` · `502 AI_GENERATION_FAILED` (Groq
unavailable, or output failed schema validation after retries)

**Security** The JD is sanitised of invisible characters, truncated, and fenced in a
labelled block. The system prompt states the block is data. Output is schema-constrained,
so a successful injection produces prose that fails validation and is discarded.

---

**POST** `/internal/ai/evidence-selection`

Stage 1. Maps each requirement to the evidence supporting it.

Request:

```json
{
  "requirements": [{ "requirementId": "REQ-001", "text": "5+ years Java",
                     "type": "HARD_REQUIRED", "weight": 5 }],
  "evidence": [{ "evidenceId": "EXP-004", "type": "EXPERIENCE",
                 "title": "Backend Engineer", "organisation": "Northwind Logistics",
                 "description": "Built order-processing services.",
                 "technologies": ["Java", "Spring Boot"],
                 "metrics": ["reduced response time from 800ms to 300ms"],
                 "startDate": "2021-03", "endDate": "2024-01" }]
}
```

Response `200`:

```json
{
  "selection": {
    "matches": [{ "requirementId": "REQ-001", "evidenceIds": ["EXP-004"],
                  "matchStrength": "STRONG", "reason": "..." }]
  },
  "provenance": { "promptVersion": "evidence-selection@v1", "...": "..." }
}
```

Any evidence ID the model invents is stripped before the response is returned; if a match
loses all of its citations, `matchStrength` is downgraded to `NONE`. A `NONE` match is a
correct answer — the requirement is reported as a gap, never manufactured.

---

**POST** `/internal/ai/resume-content`

Stage 2. Writes resume content, then verifies every statement against the cited evidence.

Request adds `jobTitle`, `seniority` and `selectedEvidenceIds` to the stage-1 inputs.

Response `200`:

```json
{
  "content": {
    "summary": { "text": "...", "evidenceIds": ["EXP-004"] },
    "experienceBullets": [{ "evidenceId": "EXP-004",
                            "bullets": [{ "text": "...", "evidenceIds": ["EXP-004"] }] }],
    "projectDescriptions": [],
    "skillsOrdering": ["SKILL-001"]
  },
  "grounding": { "passed": true, "violations": [], "checkedStatements": 6 },
  "removedSections": [],
  "provenance": { "promptVersion": "resume-content@v1", "regenerated": false, "...": "..." }
}
```

**Failure policy (blueprint §13).** Generate → schema check → grounding check. On failure,
regenerate **once** and revalidate. If it still fails, the offending statements are removed
and listed in `removedSections`; the caller surfaces them to the user as unmet
requirements. Unverified content is never returned.

`grounding.violations[].rule` is one of `UNKNOWN_EVIDENCE_ID`, `MISSING_EVIDENCE_ID`,
`INVENTED_METRIC`, `UNSUPPORTED_ENTITY`, `UNSUPPORTED_DATE`, `UNSUPPORTED_CONTACT`,
`EXTERNAL_URL`, `HIDDEN_CHARACTERS`.

**Status codes** `200` · `400 VALIDATION_ERROR` · `502 AI_GENERATION_FAILED`

---

**POST** `/internal/ai/cover-letter` — see ARCHITECTURE_DECISIONS.md ADR-020.

Structurally the same failure policy and grounding-violation vocabulary as
`/internal/ai/resume-content` above, called by application-service instead of resume-service.
Request adds `jobTitle`, `company` (nullable — not every JD names one), `seniority` and
`selectedEvidenceIds` to the stage-1 inputs. `jobTitle`/`company` are allowed to appear in the
output without an evidence citation (`GroundingValidator`'s 3-arg overload) — every other rule
is unchanged. Response shape is `{ content: { greeting, openingParagraph, bodyParagraphs[],
closingParagraph, signOff }, grounding, removedParagraphs, provenance }`.

**Status codes** `200` · `400 VALIDATION_ERROR` · `502 AI_GENERATION_FAILED`

---

## 5. OpenAPI

Each service publishes springdoc at `/v3/api-docs` and Swagger UI at `/swagger-ui.html`,
reachable on the internal network. Contract tests in `tests/contract` assert that the
generated specification matches this document, so an undocumented endpoint fails CI.
