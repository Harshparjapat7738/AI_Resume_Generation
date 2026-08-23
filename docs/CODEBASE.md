# Codebase

What is actually implemented, where it lives, and how a request moves through it.
Updated whenever architecture or a major flow changes.

**Current state:** a first vertical slice is implemented ahead of the milestone plan —
`ai-service` (Groq client, prompt registry, schema validation, grounding validation),
`auth-service` (register/login/refresh/logout/me), `profile-service` (personal info,
education, experience, skills, projects, certifications, achievements — all six
evidence-bearing sections, via an 8-step onboarding wizard and an always-editable
`/profile` page), `jd-service` (text intake, SSRF-guarded URL intake — ADR-015 — analysis, no
confirm gate — removed by ADR-037),
(synchronous generation + history, plus the built-in template catalogue — ADR-013, ADR-016),
`assessment-service` (ATS + JD-fit scoring, scoped to structured content — ADR-014) and
`application-service` (the central `Application` aggregate — references only, generation
lifecycle — ADR-017; email generation — ADR-019; cover-letter generation — ADR-020) are real.
`notification-service` was removed (ADR-035) — it never grew past a wired-up skeleton (no
domain logic was ever implemented), and nothing in the platform depended on it.
Sections below marked *(skeleton — Mn)* describe the agreed design for a later milestone.

---

## 1. Architecture

```text
Frontend (React 19, Vite, Tailwind 4, GSAP, TanStack Query)
   ↓  HTTPS, JSON, Bearer access token + HttpOnly refresh cookie
API Gateway (Spring Cloud Gateway, :8080)
   ↓  internal Docker network only — business services publish no host port
Microservices (Spring Boot 3.4.5, Java 21)
   ↓  Spring Data MongoDB
MongoDB Atlas (one logical database per service)
   ↓
Redis (cache · rate limiting · Redis Streams job queues)
   ↓
External services (Groq · Google OAuth · Gmail · MinIO/S3 · SMTP)
```

Supporting plane: Config Server (:8888) → Eureka (:8761) → Prometheus/Grafana/OTel.

---

## 2. Service responsibilities

### api-gateway

```text
api-gateway
├── Purpose        Single public entry point: routing, JWT verification, identity
│                  propagation, rate limiting, CORS, correlation IDs
├── Port           8080  (the only business port published to the host)
├── Responsibilities
│                  · verify the access token and reject unauthenticated calls
│                  · strip client-supplied X-User-Id / X-User-Roles, then set them itself
│                  · Redis token-bucket rate limiting, per user or per IP
│                  · emit the standard error envelope for 401/403/404/429/503
│                  · assign X-Correlation-Id when the client did not
├── Database       none
├── Collections    none
├── APIs           none of its own; routes /api/** to services
├── Dependencies   discovery-server, config-server, Redis
├── External       none
└── Key classes    ApiGatewayApplication
                   filter/JwtAuthenticationFilter   token verification + header hygiene
                   filter/CorrelationIdFilter       correlation ID, highest precedence
                   security/JwtVerifier             signature/issuer/expiry checks
                   security/JwtProperties           secret, issuer, public path list
                   config/RateLimiterConfig         userKeyResolver, ipKeyResolver
                   error/GatewayErrorAttributes     standard envelope, no stack traces
```

Routing table (`services/api-gateway/src/main/resources/application.yml`):

| Route | Path | Target | Limit (req/s, burst) |
|---|---|---|---|
| auth-public | `/api/auth/register\|login\|refresh\|oauth2/**` | auth-service | 5, 10 per IP |
| auth-service | `/api/auth/**` | auth-service | 20, 40 per user |
| profile-service | `/api/profile/**` | profile-service | 20, 40 per user |
| jd-service | `/api/jd/**` | jd-service | 5, 10 per user |
| assessment-service | `/api/assessment/**` | assessment-service | 10, 20 per user |
| application-service | `/api/applications/**` | application-service | 20, 40 per user |

`ai-service` is deliberately absent — ADR-012.

---

### config-server

```text
config-server
├── Purpose        Serve environment-aware, secret-free configuration to every service
├── Port           8888
├── Responsibilities  native backend over infrastructure/config-repo; ${ENV_VAR} placeholders
├── Database       none
├── Dependencies   none (must start first)
├── External       none
└── Key classes    ConfigServerApplication
```

### discovery-server

```text
discovery-server
├── Purpose        Eureka registry so the gateway can route by `lb://service-name`
├── Port           8761
├── Responsibilities  registration, heartbeat, eviction (self-preservation off locally)
├── Database       none
└── Key classes    DiscoveryServerApplication
```

### platform-common (library, not deployed)

```text
platform-common
├── Purpose        The cross-cutting web concerns that must be byte-identical everywhere
├── Contains       error/ErrorCode            stable machine-readable codes
│                  error/ApiError             the one response envelope
│                  error/ApiException         base for expected failures
│                  error/GlobalExceptionHandler
│                  web/CorrelationIdFilter    puts the ID into the SLF4J MDC
│                  security/@CallerId + CallerIdArgumentResolver
│                  web/PlatformWebAutoConfiguration
├── Contains NOT   domain models, DTOs, repositories, Feign clients  (ADR-006)
└── Wiring         registered via META-INF/spring/...AutoConfiguration.imports
```

---

### auth-service **— core implemented**

```text
auth-service
├── Purpose        Accounts, sign-in, JWT issuance, refresh rotation, Google OAuth
├── Port           8081
├── Responsibilities  registration and BCrypt (cost 12) password hashing; access-token
│                  minting; refresh-token rotation with reuse detection
├── Not yet done   Google OAuth code exchange; account lockout; security-event audit trail
│                  (M2 remainder)
├── Database       careerforge_auth
├── Collections    users · refresh_tokens (TTL)   — oauth_accounts, security_events pending
├── APIs           /api/auth/**  (see docs/API_CATALOG.md)
├── Dependencies   MongoDB Atlas
├── External       none yet (Google OAuth pending)
└── Key packages   api/ service/ domain/ repository/ config/ security/
```

The JWT signing secret lives here and in the gateway only (ADR-007). Token claims
(`sub`=userId, `roles`) and the HMAC key derivation match the gateway's `JwtVerifier` exactly
— minted here, verified there, no shared code between the two.

### profile-service **— core implemented**

```text
profile-service
├── Purpose        The candidate's verified professional data, the evidence inventory, and
│                  "My Templates" — the user's own saved Resume/Cover Letter files (ADR-034)
├── Port           8082
├── Responsibilities  personal information CRUD; six evidence-bearing sections (education,
│                  experience, skills, projects, certifications, achievements), each with
│                  create/update/delete and a stable evidenceId per item (EDU/EXP/SKILL/
│                  PROJ/CERT/ACH); the combined ID-labelled evidence inventory that grounds
│                  all generation. Separately (ADR-034): upload/list/rename/set-default/
│                  delete/download a user's own template files — validated (extension +
│                  magic-byte signature + size), stored, and returned byte-for-byte as
│                  uploaded; no structural analysis, mail-merge or AI involvement of any kind
├── Not yet done   immutable profile versions; resume import (M3 remainder)
├── Database       careerforge_profile
├── Collections    profiles — profile_versions pending; templates (ADR-034, implemented)
├── Dependencies   MongoDB Atlas; MinIO/S3 (ADR-034 — the same bucket the now-deleted
│                  document-service used to own; this is its only remaining consumer)
├── External       none
└── Note           This service is the sole authority on what is true about the candidate.
                   If a fact is not here, no generated document may assert it. Template files
                   are a separate concern from evidence — a saved template is never itself
                   evidence, and holds no AI-provider credential of any kind.
```

**Backward compatibility.** Profiles created before a section existed simply have an empty
list for it — Spring Data leaves a missing document field at its Java default rather than
erroring, so pre-existing profiles with only personal info and experience load unchanged.
Verified directly against a real profile document written before this section existed.

### jd-service **— core implemented; owns JD optimization (ADR-033)**

```text
jd-service
├── Purpose        Ingest, normalise and analyse job descriptions
├── Port           8083
├── Responsibilities  text and URL intake; whitespace/blank-line normalisation; requirement
│                  extraction via ai-service, cached per JD version — no confirm gate any
│                  more (ADR-037 removed it; analysis/optimization read currentVersion
│                  directly)
├── URL intake     SsrfGuard + JdUrlFetcher (scheme/port/private-address validation, every
│                  redirect hop re-validated, text/html-only, 3MB cap, 5s/10s timeouts) →
│                  JobPostingExtractor (schema.org JobPosting JSON-LD when present, generic
│                  readable text otherwise) — see ARCHITECTURE_DECISIONS.md ADR-015
├── Not yet done   file upload (Tika) intake — only sourceType=TEXT/URL exist today
├── Database       careerforge_jd
├── Collections    job_descriptions · jd_versions · jd_analyses
├── Dependencies   MongoDB Atlas, ai-service, and now arbitrary public web servers (URL
│                  intake) — treated as hostile, never trusted, guarded per ADR-015
├── External       arbitrary websites, for URL intake only — treated as hostile
└── Security       JD text is fenced as data, never instruction, before reaching ai-service;
                   every fetched URL passes the SSRF guard first (ADR-015)
```

### ai-service **— implemented**

```text
ai-service
├── Purpose        The single boundary to Groq — the only process that holds GROQ_API_KEY.
│                  Groq is the only AI provider; Gemini was removed entirely (ADR-033)
├── Port           8085 (internal only — ADR-012)
├── Responsibilities  versioned prompts; JD wrapped as untrusted data; JSON-schema-
│                  constrained output; schema validation; grounding validation (email);
│                  hallucinated-id stripping (optimization); token/latency metrics
├── Database       none — Redis for idempotency keys and cached responses
├── APIs           GET  /internal/ai/status
│                  POST /internal/ai/jd-analysis
│                  POST /internal/ai/evidence-selection
│                  POST /internal/ai/jd-optimization      (ADR-033 — the product's main output)
│                  POST /internal/ai/email-content
├── External       Groq API only
└── Key classes    config/GroqProperties      env-bound; masks the key for diagnostics
                   config/GroqClientConfig    the one WebClient, key attached once
                   client/AiChatClient        provider-agnostic contract (ADR-024) — system
                                              prompt/user content in, JSON content/model/
                                              token-count out
                   client/GroqClient          the sole implementation; JSON mode, backoff,
                                              metrics, no body logging. Distinguishes a 429
                                              (quota) from a 5xx (outage) so a rate limit is
                                              never reported as an outage
                   prompt/PromptRegistry      classpath prompts/<name>/v<N>.txt
                   prompt/UntrustedContent    sanitise + fence third-party text
                   schema/SchemaValidator     networknt, per-operation JSON Schema
                   grounding/GroundingValidator   the anti-fabrication gate for email prose
                   service/JdAnalysisService, EvidenceSelectionService,
                           JdOptimizationService, EmailContentService, AiGenerationSupport
                   api/AiController
```

**JD optimization's grounding guarantee** differs from email's by necessity: it generates no
prose, so there is no sentence to validate. Instead `JdOptimizationService.stripUnknownIds`
removes every requirement or evidence id absent from the request, and downgrades a match left
with no surviving evidence to `NONE`. Every candidate-facing value in a stored optimization is
therefore an `evidenceId` that provably exists in the user's own profile.

**Security posture.** This is the only process that ever holds `GROQ_API_KEY` — bound from
an environment variable only (`GroqProperties`), never a literal in source, YAML or a Docker
image, and exposed only in masked form (`maskedKey()`) for diagnostics. It never returns HTML, a template, a command
or PDF bytes. Request and response bodies — including prompts and generated content — are
never logged for either provider; metrics and logs carry only the operation tag, provider,
retryable/success flag, model id, latency and token counts.

**Three-layer injection defence**, none sufficient alone:

1. `UntrustedContent.fence()` strips zero-width, bidirectional and control characters,
   collapses blank-line padding, truncates, and wraps the text in a labelled block.
2. The versioned system prompt states that the block is data and never instructions.
3. `response_format: json_object` plus JSON Schema validation means a compliant-with-the-
   injection response is prose, and prose fails validation and is discarded.

**Grounding rules** (`GroundingValidator`) — a statement is rejected when it cites an
unknown evidence ID, cites nothing, states a number absent from the cited evidence, names
a proper noun absent from the whole profile, states a date absent from the cited evidence,
emits contact details or any URL, or contains hidden characters.

Proper nouns are checked against the entire profile rather than just the cited items:
naming a technology the candidate genuinely lists elsewhere is a relevance mistake, not a
fabrication, and should not block a generation. Numbers are compared digits-only, so
evidence saying `1500 users` supports `1,500 users` while `improved performance` can never
become `improved performance by 40%`.

The class is deliberately biased towards rejection: a false positive costs one
regeneration, a false negative puts an invented claim in a document a real person sends to
an employer under their name.

### assessment-service **— JD-fit only, keyed on the optimization (ADR-033)**

```text
assessment-service
├── Purpose        Deterministic scoring and explainability
├── Port           8086
├── Responsibilities  seven weighted ATS checks against structured resume content
│                  (fractional sub-checks, ADR-008); JD compatibility =
│                  0.50·coverage + 0.20·keyword + 0.20·seniority + 0.10·recency;
│                  screening-readiness band (ADR-009); recommendations
├── Scope deviation   the blueprint's ten checks assume a rendered PDF/DOCX to inspect
│                  (fonts, layout, header/footer) — this engine deliberately scores what's
│                  measurable from the JD optimization instead (ADR-033); a rendered
│                  renders a real PDF (ADR-018), but this scope was never solely "there's no
│                  renderer yet" — revisiting it is unrelated follow-up work, not a gap here
├── Database       careerforge_assessment
├── Collections    ats_assessments · jd_fit_assessments (recommendations embedded — ADR-014)
├── Dependencies   jd-service, profile-service — all reached directly via
│                  Eureka, not through the gateway, identity forwarded via X-User-Id
└── Rule           No score is ever requested from the LLM. Every number is reproducible
                   from stored inputs and traceable to a requirement and an evidence ID.
```

### application-service **— core implemented (ADR-017); email generation implemented (ADR-019)**

```text
application-service
├── Purpose        Own the central Application aggregate: job + generation type + template +
│                  references to the resume, cover letter, email and both assessments
├── Port           8088
├── Responsibilities  verify + denormalise from jd-service on create; verify a supplied
│                  derive status per
│                  generationType (never accepted from the caller); enforce legal status
│                  transitions; append-only status history; generate application-email
│                  content (subject/greeting/closing/signature assembled deterministically
│                  from already-verified data — job title/company, the candidate's real
│                  name — ai-service supplies only the grounded highlight paragraph(s),
│                  ADR-019); generate cover-letter content (evidence selection then grounded
│                  content (ADR-019)
├── Not yet done   Gmail drafts, combined (ALL) generation (GenerationType represents them;
│                  nothing produces them yet — M8 remainder)
├── Database       careerforge_application
├── Collections    applications · application_status_history · emails · cover_letter_versions
├── APIs           /api/applications/**  (see docs/API_CATALOG.md)
├── Dependencies   jd-service, profile-service, ai-service
│                  — all reached directly via Eureka, not through the gateway, identity
│                  forwarded via X-User-Id
├── External       Gmail API (drafts scope only — not yet integrated)
└── Rule           References only, never a copy of what another service owns. Every AI call
                   it makes (email, cover letter) is grounded exactly like every other
                   generated statement in this product — never asked to restate a fact
                   that's already known with certainty, and always checked against the
                   candidate's real evidence for everything else.
```

**`notification-service` was removed (ADR-035).** It never grew past a wired-up skeleton (no
controller, no domain logic, no Redis Stream consumer was ever implemented) and had zero
active callers anywhere in the platform. Transactional/system email is not currently
implemented by anything; the candidate-facing application-email feature lives entirely in
`application-service` and is unaffected.

---

## 3. Request flows

### Authentication **— implemented**

```text
React
 ↓  POST /api/auth/login {email, password}
Gateway            path is public → no token required, 5 req/s per IP
 ↓
Auth Service       BCrypt verify → mint tokens
 ↓
MongoDB Atlas      users, refresh_tokens (rotating family, TTL-expired)
 ↓
JWT                access token (15 min) in the body;
                   refresh token in an HttpOnly · SameSite=Lax cookie
                   (Secure when REFRESH_COOKIE_SECURE=true — plain HTTP locally)
 ↓
React              access token held in memory only — never localStorage; a fresh page
                   load silently calls /api/auth/refresh before rendering anything that
                   needs auth (services/session.ts)
```

On refresh, the presented token is invalidated and a successor issued. Presenting an
already-rotated token revokes the entire family. Account lockout and the
`security_events` audit trail are not yet implemented (M2 remainder).

### JD processing — text and URL intake implemented; file intake pending

```text
React
 ↓  POST /api/jd                                              — implemented (TEXT)
 ↓  POST /api/jd/fetch-url                                     — implemented (URL, ADR-015)
 ↓  POST /api/jd/upload                                        — not yet implemented
Gateway            authenticated, 5 req/s per user
 ↓
JD Service
 ↓  URL  → SsrfGuard (scheme/port/private-address, every redirect hop) → JdUrlFetcher
 ↓         (text/html-only, 3MB cap, 5s/10s timeouts) → JobPostingExtractor (JSON-LD          ✅
 ↓         JobPosting if present, else generic readable text)
 ↓  File → MIME allowlist → magic-byte check → size cap → quarantine → Tika                     (pending)
 ↓  Text→ length validation, whitespace/blank-line normalisation                                 ✅
 ↓
JD analysis        No confirm gate any more (ADR-037 removed it — the frontend's Skill Gap
                   step triggers analysis/optimization directly on JD entry). title ·
                   company · seniority · keywords · requirements, each classified
                   HARD_REQUIRED | PREFERRED | RESPONSIBILITY | SKILL | TECHNOLOGY |
                   EDUCATION | CERTIFICATION. Computed on first read, cached on the JD
                   version thereafter.
```

### JD optimization **— implemented (ADR-033), the product's primary flow**

```text
POST /api/jd/{id}/optimize            (gateway → jd-service)
  ├── JdService.analyse                 currentVersion + cached analysis (ownership enforced here)
  ├── profile-service GET /api/profile/evidence     verified evidence inventory
  ├── ai-service POST /internal/ai/jd-optimization  one Groq call
  │     ├── JSON-Schema validation
  │     └── stripUnknownIds — any requirement/evidence id not in the request is removed;
  │         a match left with no evidence is downgraded to NONE
  └── persist JdOptimization (jd_optimizations, one per jdVersionId)

GET  /api/jd/{id}/optimization        the persisted result, no AI call
POST /api/assessment/{jobDescriptionId}   deterministic JD-fit score over that optimization
```

Re-reads an existing result for the same JD version rather than spending another AI request;
`?refresh=true` recomputes, which is what a profile edit warrants. No document is produced at
any point — the frontend exports JSON or a ready-to-paste external prompt.

### JD-fit assessment **— implemented, scored from the optimization (ADR-033)**

```text
Resume version  +  its JD analysis  +  caller's profile   (fetched via Eureka, not the gateway)
              ↓
      Assessment Service
              ↓
        ATS Engine                deterministic, seven weighted checks, fractional
                                  sub-scores — content-based, not a rendered document
              ↓
     JD Compatibility             coverage · keyword · seniority · recency
              ↓
   Screening Readiness            STRONG | COMPETITIVE | STRETCH | WEAK_FIT, ordered rules
              ↓
   Explanation payload            per-requirement matches, gaps, matched/missing keywords,
                                  recommendations that never invent a skill or experience
```

Triggered automatically by the frontend right after generation (`ProcessingPage`), and
re-runnable on demand from the result page if it's ever missing (`POST` is idempotent — see
`docs/API_CATALOG.md` §2).

### Application creation **— implemented (ADR-017)**

```text
POST /api/applications  { jobDescriptionId, generationType, templateId?, resumeVersionId? }
              ↓
      Application Service
              ↓
   verify jobDescriptionId          jd-service, GET /api/jd/{id}      — 404 if not owned
   verify templateId (if any)       resume-service, GET .../templates/{id}
   verify resumeVersionId (if any)  resume-service, GET /api/resumes/{id}
                                    + must belong to the same jobDescriptionId
   look up assessed (best effort)   assessment-service, GET .../resume-versions/{id}
              ↓
        derive status               DRAFT (no artifact yet) · COMPLETED (every artifact the
                                    generationType requires now exists) · PROCESSING (some,
                                    but not all, exist yet)
              ↓
              save
```

`ProcessingPage` calls this directly for `EMAIL_ONLY` (see below); the `RESUME_ONLY` path
still calls `resume-service`/`assessment-service` directly and is unmodified — `POST
/api/applications` is not yet in that path, since resume-service already owns generation
there (ADR-013) and nothing requires routing it through the aggregate to exist.

### Email generation **— implemented (ADR-019)**

```text
POST /api/applications/{id}/email   (no body — everything needed is already known)
              ↓
      Email Generation Service
              ↓
   require generationType = EMAIL_ONLY, and a confirmed jobTitle on the application
   fetch evidence                    profile-service, GET /api/profile/evidence
                                    — 400 VALIDATION_ERROR if empty, same guard resume uses
   fetch candidate name (best effort) profile-service, GET /api/profile
              ↓
   generate ONE grounded highlight   ai-service, POST /internal/ai/email-content
   paragraph, citing evidence        (schema validate → grounding validate → regenerate
                                    once → drop-and-report; mirrors CoverLetterContentService)
              ↓
   assemble deterministically        subject + greeting/closing/sign-off frame, from
                                    Application.jobTitle/company + the candidate's real name
                                    — never model output
              ↓
   persist EmailContent (versioned)  attach emailId to the Application, recompute status
```

Wired into the frontend wizard: `OutputTypePage`'s "Email Content" card carries
`?type=EMAIL_ONLY` as a query param through `JobDescriptionPage` → `ReviewPage` (skips the
resume-only template step) → `ProcessingPage`, which creates the `Application` then calls
this endpoint, landing on `/results/email/:applicationId` (`EmailResultPage`: copy subject,
copy email, download as text, regenerate). The `RESUME_ONLY` path is unchanged throughout.

## 4. Cross-cutting conventions

**Error envelope** — every service, every failure:

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

Never returned to a client: stack traces, exception class names, database or connection
errors, internal file paths, API keys, or any part of a prompt.

**Logging** — structured, one correlation ID per user action, propagated as
`X-Correlation-Id` and held in the MDC. Never logged: passwords, access or refresh tokens,
API keys, MongoDB URIs, full resumes or cover letters, or any JD body. Log identifiers.

**Authorisation** — identity comes from the gateway header; authorisation is always a
local check. Every repository method is scoped by `userId`, and a document owned by another
user is reported as not found.

**DTOs** — controllers accept and return DTOs only. A `@Document` class never crosses the
HTTP boundary, so a storage-shape change cannot silently alter the public API.

**Configuration** — no literal secret in any source file, YAML file or Dockerfile. Config
references `${ENV_VAR}`; values come from `.env` locally and from a secrets manager in
production.

---

## 5. Where to change what

| Change | Files | Docs to update |
|---|---|---|
| New endpoint | `<service>/api/`, DTOs, tests | `API_CATALOG.md` |
| New collection or index | `<service>/domain/`, `repository/`, index initialiser | `DATABASE.md` |
| New third-party integration | `<service>/config/`, client, `.env.example` | `EXTERNAL_APIS.md`, `.env.example` |
| New service or flow | `services/`, `docker-compose.yml`, gateway routes | `CODEBASE.md`, `ARCHITECTURE_DECISIONS.md`, `README.md` |
| Deviation from the blueprint | anywhere | `ARCHITECTURE_DECISIONS.md` — **before** the code |
| New error code | `platform-common/error/ErrorCode.java` | `API_CATALOG.md` |
