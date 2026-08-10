# Codebase

What is actually implemented, where it lives, and how a request moves through it.
Updated whenever architecture or a major flow changes.

**Current state:** Milestone 1 — foundation, plus `ai-service` brought forward and fully
implemented (Groq client, prompt registry, schema validation, grounding validation). The
other eight business services are wired-up skeletons: they build, register with Eureka,
load config and expose health, but contain no domain logic yet. Sections below marked
*(skeleton — Mn)* describe the agreed design for a later milestone.

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
| resume-service | `/api/resumes/**` | resume-service | 3, 6 per user |
| assessment-service | `/api/assessment/**` | assessment-service | 10, 20 per user |
| document-service | `/api/documents/**` | document-service | 10, 20 per user |
| application-service | `/api/applications/**` | application-service | 20, 40 per user |

`ai-service` and `notification-service` are deliberately absent — ADR-012.

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

### auth-service *(skeleton — logic in M2)*

```text
auth-service
├── Purpose        Accounts, sign-in, JWT issuance, refresh rotation, Google OAuth
├── Port           8081
├── Responsibilities  registration and BCrypt password hashing; access-token minting;
│                  refresh-token rotation with reuse detection; OAuth code exchange;
│                  account lockout; security-event audit trail
├── Database       careerforge_auth
├── Collections    users · oauth_accounts · refresh_tokens (TTL) · security_events
├── APIs           /api/auth/**  (see docs/API_CATALOG.md)
├── Dependencies   MongoDB Atlas, Redis
├── External       Google OAuth 2.0
└── Key packages   api/ service/ domain/ repository/ config/
```

The JWT signing secret lives here and in the gateway only (ADR-007).

### profile-service *(skeleton — logic in M3)*

```text
profile-service
├── Purpose        The candidate's verified professional data and the evidence inventory
├── Port           8082
├── Responsibilities  CRUD per profile section; immutable profile versions;
│                  assign a stable evidenceId to every factual item;
│                  serve the ID-labelled inventory that grounds all generation
├── Database       careerforge_profile
├── Collections    profiles · profile_versions
├── Dependencies   MongoDB Atlas
├── External       none
└── Note           This service is the sole authority on what is true about the candidate.
                   If a fact is not here, no generated document may assert it.
```

### jd-service *(skeleton — logic in M4)*

```text
jd-service
├── Purpose        Ingest, fetch, extract, normalise, analyse and confirm job descriptions
├── Port           8083
├── Responsibilities  text/file/URL intake; SSRF-hardened fetching; Tika/jsoup extraction;
│                  normalisation; "is this actually a job posting?" detection;
│                  requirement extraction and classification; mandatory user confirmation
├── Database       careerforge_jd
├── Collections    job_descriptions · jd_versions · jd_analyses
├── Dependencies   MongoDB Atlas, Redis, ai-service
├── External       arbitrary websites — treated as hostile
└── Security       every fetch passes the SSRF predicate; JD text is data, never instruction
```

### resume-service *(skeleton — logic in M5)*

```text
resume-service
├── Purpose        Orchestrate generation and own resume version history
├── Port           8084
├── Responsibilities  assemble the generation job from confirmed JD + evidence;
│                  drive the two-stage AI pipeline; persist validated content;
│                  request rendering; expose job status; own the template catalogue
├── Database       careerforge_resume
├── Collections    resume_generations · resume_versions · templates
├── Dependencies   profile-service, jd-service, ai-service, document-service, Redis
└── Note           The only orchestrator. Nothing calls back into it except assessment reads.
```

### ai-service **— implemented**

```text
ai-service
├── Purpose        The single boundary to Groq
├── Port           8085 (internal only — ADR-012)
├── Responsibilities  versioned prompts; JD wrapped as untrusted data; JSON-schema-
│                  constrained output; schema validation; grounding validation;
│                  one retry then degrade; token/latency metrics
├── Database       none — Redis for idempotency keys and cached responses
├── APIs           GET  /internal/ai/status
│                  POST /internal/ai/jd-analysis
│                  POST /internal/ai/evidence-selection
│                  POST /internal/ai/resume-content
├── External       Groq API
└── Key classes    config/GroqProperties      env-bound; masks the key for diagnostics
                   config/GroqClientConfig    the one WebClient, key attached once
                   client/GroqClient          JSON mode, backoff, metrics, no body logging
                   prompt/PromptRegistry      classpath prompts/<name>/v<N>.txt
                   prompt/UntrustedContent    sanitise + fence third-party text
                   schema/SchemaValidator     networknt, per-operation JSON Schema
                   grounding/GroundingValidator   the anti-fabrication gate
                   service/JdAnalysisService, EvidenceSelectionService,
                           ResumeContentService, AiGenerationSupport
                   api/AiController
```

**Security posture.** This is the only process that ever holds `GROQ_API_KEY`. It never
returns HTML, a template, a command or PDF bytes. Request and response bodies are never
logged — only model, latency, token counts and status.

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

### assessment-service *(skeleton — logic in M7)*

```text
assessment-service
├── Purpose        Deterministic scoring and explainability
├── Port           8086
├── Responsibilities  ten weighted ATS checks with fractional sub-checks (ADR-008);
│                  JD compatibility = 0.50·coverage + 0.20·keyword + 0.20·seniority
│                  + 0.10·recency; screening-readiness band (ADR-009); recommendations
├── Database       careerforge_assessment
├── Collections    ats_assessments · jd_fit_assessments · recommendations
├── Dependencies   resume-service, jd-service, document-service
└── Rule           No score is ever requested from the LLM. Every number is reproducible
                   from stored inputs and traceable to a requirement and an evidence ID.
```

### document-service *(skeleton — logic in M6)*

```text
document-service
├── Purpose        Deterministic rendering and private artifact custody
├── Port           8087
├── Responsibilities  render validated JSON through versioned templates;
│                  OpenHTMLToPDF for PDF, docx4j for DOCX; machine-readability self-check;
│                  upload under a random object key; issue short-lived presigned URLs
├── Database       careerforge_document   (ADR-002)
├── Collections    rendered_documents
├── Dependencies   MinIO (dev) / S3 (prod)
└── Security       private bucket, no static directory, ownership check before every URL
```

### application-service *(skeleton — logic in M8)*

```text
application-service
├── Purpose        Cover letter, email, Gmail drafts, history and status tracking
├── Port           8088
├── Database       careerforge_application
├── Collections    applications · application_status_history
├── External       Gmail API (drafts scope only)
└── Rule           Creates drafts. Never sends an application on the user's behalf.
```

### notification-service *(skeleton — logic in M8)*

```text
notification-service
├── Purpose        Transactional email
├── Port           8089 (internal only — ADR-012)
├── Database       none — consumes a Redis Stream
├── External       SMTP provider
└── Rule           Platform notifications only; never touches the user's job applications.
```

---

## 3. Request flows

### Authentication *(M2)*

```text
React
 ↓  POST /api/auth/login {email, password}
Gateway            path is public → no token required, 5 req/s per IP
 ↓
Auth Service       BCrypt verify → record security_event → mint tokens
 ↓
MongoDB Atlas      users, refresh_tokens (rotating family, TTL-expired)
 ↓
JWT                access token (15 min) in the body;
                   refresh token in an HttpOnly · Secure · SameSite=Lax cookie
 ↓
React              access token held in memory only — never localStorage
```

On refresh, the presented token is invalidated and a successor issued. Presenting an
already-rotated token revokes the entire family and writes a `REFRESH_REUSE` security event.

### JD processing *(M4)*

```text
React
 ↓  POST /api/jd | /api/jd/upload | /api/jd/fetch-url
Gateway            authenticated, 5 req/s per user
 ↓
JD Service
 ↓  URL  → SSRF predicate → safe fetcher (redirect, timeout, size, content-type caps) → jsoup
 ↓  File → MIME allowlist → magic-byte check → size cap → quarantine → Tika
 ↓  Text→ length and character-class validation
 ↓
JD normalisation   whitespace, ligatures, bullet glyphs, zero-width character removal
 ↓
"is this a job posting?"   → JD_VALIDATION_ERROR when it is not
 ↓
JD analysis        title · company · seniority · responsibilities · required vs preferred
                   skills · education · certifications · years · technologies · keywords,
                   each classified HARD_REQUIRED | PREFERRED | RESPONSIBILITY | SKILL |
                   TECHNOLOGY | EDUCATION | CERTIFICATION
 ↓
User confirmation  MANDATORY. Nothing downstream may run against an unconfirmed JD.
```

### Resume generation *(M5–M6)*

```text
Confirmed JD  +  Candidate profile  +  Selected evidence
                          ↓
                     AI Service
                          ↓
                        Groq
                          ↓
                  Structured JSON            ← JSON Schema validated
                          ↓
              Grounding validation           ← evidence IDs must resolve; no invented
                          ↓                     metrics, employers, dates, URLs
                    Resume Service            ← persists resume_versions
                          ↓
                   Document Service
                          ↓
                      PDF / DOCX              ← private bucket, presigned download
```

Failure path: schema or grounding failure triggers exactly one regeneration. If it fails
again, the unsupported claim is removed and reported to the user as a missing requirement —
it is never silently kept.

### ATS assessment *(M7)*

```text
Resume version  +  Confirmed JD
              ↓
      Assessment Service
              ↓
        ATS Engine                deterministic, ten weighted checks, fractional sub-scores
              ↓
     JD Compatibility             coverage · keyword · seniority · recency
              ↓
   Screening Readiness            STRONG | COMPETITIVE | STRETCH | WEAK_FIT, ordered rules
              ↓
   Explanation payload            per-requirement matches, gaps, and the rule that fired
```

### Document download *(M6)*

```text
React → GET /api/documents/{id}/download
Gateway  verifies JWT, sets X-User-Id
Document Service
  ├── load rendered_documents by id
  ├── if document.userId != callerId → 404 (not 403: IDs must not be enumerable)
  └── issue a 300-second presigned URL for a random object key in a private bucket
React follows the URL directly to storage; bytes never transit the service.
```

---

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
