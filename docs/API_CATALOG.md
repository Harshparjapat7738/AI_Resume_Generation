# API Catalog

Every implemented endpoint, documented before it is considered done. Updated in the same
pull request that adds, changes or removes an API.

**Current state:** Milestone 1. Only platform endpoints are implemented. Business
endpoints appear in §3 as a planned contract and move into §2 as they ship.

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

## 3. Planned endpoints

Contract agreed; implemented in the milestone shown. Each moves to §2 with full request,
response, validation and error documentation when it ships.

### Milestone 2 — auth-service

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/auth/register` | Public | Create an account. `409 CONFLICT` if the email exists. |
| POST | `/api/auth/login` | Public | Authenticate. Returns access token; sets HttpOnly refresh cookie. |
| POST | `/api/auth/refresh` | Refresh cookie | Rotate the refresh token, issue a new access token. Reuse of a rotated token revokes the family. |
| POST | `/api/auth/logout` | Bearer | Revoke the current refresh family, clear the cookie. |
| GET | `/api/auth/me` | Bearer | Current user summary. |
| GET | `/api/auth/oauth2/authorize/google` | Public | Begin Authorization Code + PKCE. |
| GET | `/api/auth/oauth2/callback/google` | Public | Exchange code, link or create account, issue tokens. |

### Milestone 3 — profile-service

| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET / PUT | `/api/profile` | Bearer | Read / replace personal information and address. |
| POST / PUT / DELETE | `/api/profile/experience[/{id}]` | Bearer | Manage experience; each item gets a stable `evidenceId`. |
| POST / PUT / DELETE | `/api/profile/education[/{id}]` | Bearer | Manage education. |
| POST / PUT / DELETE | `/api/profile/skills[/{id}]` | Bearer | Manage skills. |
| POST / PUT / DELETE | `/api/profile/certifications[/{id}]` | Bearer | Manage certifications. |
| POST / PUT / DELETE | `/api/profile/projects[/{id}]` | Bearer | Manage projects. |
| GET | `/api/profile/evidence` | Bearer | The ID-labelled inventory consumed by ai-service. |
| GET | `/api/profile/versions` | Bearer | Profile version history. |
| POST | `/api/profile/import` | Bearer | Import an existing resume; returns parsed items for review — nothing is saved unconfirmed. |

### Milestone 4 — jd-service

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/jd` | Bearer | Submit JD text. |
| POST | `/api/jd/upload` | Bearer | Submit a PDF/DOCX/TXT file (multipart, ≤5 MB). |
| POST | `/api/jd/fetch-url` | Bearer | Fetch a JD by URL through the SSRF guard. |
| GET | `/api/jd/{id}` | Bearer | Retrieve the JD and its extracted text for review. |
| POST | `/api/jd/{id}/confirm` | Bearer | **Mandatory.** Confirm a specific version before any generation. |
| GET | `/api/jd/{id}/analysis` | Bearer | Requirements, classifications and keywords. `409 JD_NOT_CONFIRMED` if not yet confirmed. |
| GET | `/api/jd` | Bearer | Paged list of the caller's JDs. |

### Milestone 5 — resume-service

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/resumes/generate` | Bearer | Start async generation from a confirmed JD + template. `202` with a job ID. |
| GET | `/api/resumes/generations/{jobId}` | Bearer | Job status and failure code. |
| GET | `/api/resumes/{id}` | Bearer | Latest version content plus its evidence map. |
| GET | `/api/resumes/{id}/versions` | Bearer | Version history. |
| GET | `/api/resumes/templates` | Bearer | Template catalogue (M6). |

### Milestone 6 — document-service

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/documents/{resumeVersionId}/render` | Bearer | Render PDF and/or DOCX. `202`. |
| GET | `/api/documents/{id}/preview` | Bearer | Preview payload or presigned preview URL. |
| GET | `/api/documents/{id}/download` | Bearer | 300-second presigned URL. `404` if not owned. |

### Milestone 7 — assessment-service

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/assessment/resume-versions/{resumeVersionId}` | Bearer | Compute ATS + JD compatibility (ADR-010). |
| GET | `/api/assessment/resume-versions/{resumeVersionId}` | Bearer | Full explainable breakdown. |
| GET | `/api/assessment/{resumeId}` | Bearer | Convenience read: latest version's assessment. |

### Milestone 8 — application-service

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/applications` | Bearer | Save an application version with JD, resume, scores and template. |
| GET | `/api/applications` | Bearer | Paged history; filter by status. |
| GET | `/api/applications/{id}` | Bearer | Full detail. |
| PATCH | `/api/applications/{id}/status` | Bearer | Update status; appends to history. |
| POST | `/api/applications/{id}/cover-letter` | Bearer | Generate a grounded cover letter. |
| POST | `/api/applications/{id}/email` | Bearer | Generate subject, body and filenames. |
| POST | `/api/applications/{id}/gmail-draft` | Bearer | Create a Gmail **draft**. Never sends. |

### Milestone 9 — privacy

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/account/export` | Bearer | Request a full data export (async). |
| DELETE | `/api/account` | Bearer | Request erasure across all services. |

---

## 4. Internal APIs

Not routed through the gateway and not reachable from a browser (ADR-012). Called only
service-to-service on the internal network. Adding a public route to either service
requires a new ADR.

| Method | Path | Service | Purpose | Status |
|---|---|---|---|---|
| GET | `/internal/ai/status` | ai-service | Configuration and connectivity diagnostic | ✅ Implemented |
| POST | `/internal/ai/jd-analysis` | ai-service | Semantic JD understanding | ✅ Implemented |
| POST | `/internal/ai/evidence-selection` | ai-service | Stage 1: requirement → evidence IDs | ✅ Implemented |
| POST | `/internal/ai/resume-content` | ai-service | Stage 2: grounded content generation | ✅ Implemented |
| POST | `/internal/ai/cover-letter` | ai-service | Grounded cover-letter content | Milestone 8 |
| POST | `/internal/notifications/send` | notification-service | Queue a transactional email | Milestone 8 |

---

**GET** `/internal/ai/status` — port 8085

Reports whether Groq is configured and reachable. Useful when bringing the stack up: a
missing or wrong API key otherwise only surfaces on a user's first generation.

Response `200`:

```json
{
  "groqConfigured": true,
  "maskedApiKey": "gsk_…9f2a",
  "model": "llama-3.3-70b-versatile",
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
  "provenance": { "promptVersion": "jd-analysis@v1", "model": "llama-3.3-70b-versatile",
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

## 5. OpenAPI

Each service publishes springdoc at `/v3/api-docs` and Swagger UI at `/swagger-ui.html`,
reachable on the internal network. Contract tests in `tests/contract` assert that the
generated specification matches this document, so an undocumented endpoint fails CI.
