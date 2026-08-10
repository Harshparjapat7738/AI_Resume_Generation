# Implementation Plan

**Source of truth:** `CareerForge_AI_Final_Developer_Blueprint_MongoDB_Atlas.md` (v3.0)
**Status:** Milestone 1 complete. `ai-service` implemented ahead of schedule (Groq client,
prompts, schema validation, grounding) — the rest of Milestone 5 still needs profile-service
and jd-service before the pipeline runs end to end.
**Last updated:** 2026-08-09

---

## 1. Project architecture

Ten Spring Boot services plus two platform services, one React frontend, MongoDB Atlas as
the only persistent database, Redis for cache/rate-limit/job coordination, MinIO (dev) and
S3 (prod) for artifacts, Groq as the only LLM provider.

```text
                        React 19 + TypeScript + Vite
                         Tailwind · GSAP · TanStack Query
                                      │  HTTPS
                                      ▼
                        ┌─────────────────────────────┐
                        │   API Gateway  :8080        │
                        │  JWT verify · rate limit    │
                        │  CORS · correlation ID      │
                        └─────────────┬───────────────┘
                                      │  internal network only
        ┌──────────┬──────────┬───────┴────┬───────────┬───────────────┐
        ▼          ▼          ▼            ▼           ▼               ▼
      Auth      Profile      JD         Resume     Assessment      Document
      :8081      :8082      :8083        :8084        :8086          :8087
        │          │          │            │           │               │
        │          │          └────┬───────┘           │               │
        │          │               ▼                   │               ▼
        │          │          AI Service :8085         │        MinIO / S3
        │          │               │                   │       (private bucket)
        │          │               ▼                   │
        │          │           Groq API                │
        │          │                                   │
        └──────────┴───────────────┬───────────────────┘
                                   ▼
                          MongoDB Atlas cluster
                    (one logical database per service)

  Application :8088 ──► Gmail API        Notification :8089 ──► SMTP
  Redis :6379  — cache · rate limit · Redis Streams job queues
  Config Server :8888 · Eureka :8761 · Prometheus · Grafana · OTel Collector
```

**Non-negotiable invariants** (each one is enforced by a test, not just documentation):

1. No service reads or writes another service's MongoDB collections. Cross-service data
   is fetched over HTTP.
2. Groq is reachable only from `ai-service`. The API key exists in exactly one process.
3. The LLM never emits PDF bytes, HTML, a template, or an executable string.
4. Every generated factual claim carries evidence IDs that resolve to profile data.
5. ATS and JD-compatibility scores are computed in Java, never requested from the LLM.
6. Every repository query is scoped by `userId`.
7. No secret is committed. `.env` is gitignored; Gitleaks fails CI if one slips through.

---

## 2. Service dependency graph

```text
api-gateway ──► discovery-server, config-server, redis
            ──► auth, profile, jd, resume, assessment, document, application

auth-service        ──► MongoDB(careerforge_auth), Redis, Google OAuth
profile-service     ──► MongoDB(careerforge_profile)
jd-service          ──► MongoDB(careerforge_jd), Redis, ai-service, internet (SSRF-guarded)
resume-service      ──► MongoDB(careerforge_resume), Redis,
                        profile-service, jd-service, ai-service, document-service
ai-service          ──► Redis, Groq                      (no database — ADR-002)
assessment-service  ──► MongoDB(careerforge_assessment),
                        resume-service, jd-service, document-service
document-service    ──► MongoDB(careerforge_document), MinIO/S3
application-service ──► MongoDB(careerforge_application),
                        resume-service, document-service, ai-service, Gmail API
notification-service──► Redis Streams, SMTP              (no database — ADR-002)
```

There are **no cycles**. `resume-service` is the orchestrator; nothing calls back into it
except `assessment-service`, which only reads.

Call direction rule: a service may call a service *below* it in this list, never above.

```text
gateway > application > assessment > resume > document > jd > ai > profile > auth
```

---

## 3. Repository structure

```text
careerforge-ai/
├── pom.xml                          aggregator + dependency management (Java 21, Boot 3.4.5)
├── docker-compose.yml               infra always; app services under the `app` profile
├── .env.example                     every variable, no values
├── .gitignore / .dockerignore
├── README.md
│
├── services/
│   ├── platform-common/             error envelope, correlation ID, @CallerId  (ADR-006)
│   ├── config-server/       :8888
│   ├── discovery-server/    :8761
│   ├── api-gateway/         :8080
│   ├── auth-service/        :8081
│   ├── profile-service/     :8082
│   ├── jd-service/          :8083
│   ├── resume-service/      :8084
│   ├── ai-service/          :8085
│   ├── assessment-service/  :8086
│   ├── document-service/    :8087
│   ├── application-service/ :8088
│   └── notification-service/:8089
│
├── frontend/                        React 19 · TS · Vite · Tailwind 4 · GSAP
├── infrastructure/
│   ├── config-repo/                 served by config-server (secret-free)
│   ├── prometheus/ grafana/ otel/
│   └── docker/
├── docs/                            the six maintained documents
├── tests/                           e2e · contract · ai-eval · security
└── .github/workflows/ci.yml         build · test · gitleaks · semgrep · trivy
```

Package layout inside every Java service:

```text
ai/careerforge/<service>/
├── <Service>Application.java
├── api/          controllers + request/response DTOs   (no domain objects on the wire)
├── service/      use cases; the only place business rules live
├── domain/       @Document models + value objects
├── repository/   Spring Data MongoDB interfaces
└── config/       beans, properties, index initialisers
```

---

## 4. Database and collection ownership

| Service | Database | Collections |
|---|---|---|
| auth-service | `careerforge_auth` | `users`, `oauth_accounts`, `refresh_tokens`, `security_events` |
| profile-service | `careerforge_profile` | `profiles`, `profile_versions` |
| jd-service | `careerforge_jd` | `job_descriptions`, `jd_versions`, `jd_analyses` |
| resume-service | `careerforge_resume` | `resume_generations`, `resume_versions`, `templates` |
| assessment-service | `careerforge_assessment` | `ats_assessments`, `jd_fit_assessments`, `recommendations` |
| document-service | `careerforge_document` | `rendered_documents` *(ADR-002)* |
| application-service | `careerforge_application` | `applications`, `application_status_history` |
| ai-service | — | Redis only *(ADR-002)* |
| notification-service | — | Redis Streams only *(ADR-002)* |

Full document models, indexes and retention rules: `docs/DATABASE.md`.

---

## 5. API implementation order

Each row ships with DTOs, bean validation, an ownership check, OpenAPI annotations, tests,
and an `API_CATALOG.md` entry — in the same pull request.

| # | Endpoints | Service | Milestone |
|---|---|---|---|
| 1 | `GET /actuator/health\|info\|prometheus` | all | 1 ✅ |
| 2 | `POST /api/auth/register\|login\|refresh\|logout`, `GET /api/auth/me` | auth | 2 |
| 3 | `GET /api/auth/oauth2/authorize/google`, `GET .../callback/google` | auth | 2 |
| 4 | `GET\|PUT /api/profile`, CRUD for education/experience/skills/certifications/projects | profile | 3 |
| 5 | `GET /api/profile/evidence` — the ID-labelled inventory the AI consumes | profile | 3 |
| 6 | `POST /api/profile/import` — existing-resume import | profile | 3 |
| 7 | `POST /api/jd` (text), `POST /api/jd/upload` (file), `POST /api/jd/fetch-url` | jd | 4 |
| 8 | `GET /api/jd/{id}`, `POST /api/jd/{id}/confirm`, `GET /api/jd/{id}/analysis` | jd | 4 |
| 9 | `POST /api/resumes/generate`, `GET /api/resumes/{id}`, `GET /api/resumes/{id}/versions` | resume | 5 |
| 10 | `GET /api/resumes/generations/{jobId}` — async job status | resume | 5 |
| 11 | `POST /api/documents/{resumeVersionId}/render`, `GET .../preview`, `GET .../download` | document | 6 |
| 12 | `GET /api/resumes/templates` — catalogue | resume | 6 |
| 13 | `POST\|GET /api/assessment/resume-versions/{id}` *(ADR-010)* | assessment | 7 |
| 14 | `GET /api/assessment/{resumeId}` — latest-version convenience read | assessment | 7 |
| 15 | `POST /api/applications`, `GET /api/applications`, `PATCH /api/applications/{id}/status` | application | 8 |
| 16 | `POST /api/applications/{id}/cover-letter`, `.../email`, `.../gmail-draft` | application | 8 |
| 17 | `POST /api/account/export`, `DELETE /api/account` — GDPR/DPDP | auth + all | 9 |

Internal-only (no gateway route, ADR-012) — the AI endpoints are **implemented**:
`GET /internal/ai/status` ✅, `POST /internal/ai/jd-analysis` ✅,
`POST /internal/ai/evidence-selection` ✅, `POST /internal/ai/resume-content` ✅,
`POST /internal/ai/cover-letter` (M8), `POST /internal/notifications/send` (M8).

---

## 6. Frontend implementation order

| # | Screen / concern | Depends on | Milestone |
|---|---|---|---|
| 1 | App shell, router, TanStack Query client, API client, error boundary | — | 1 ✅ |
| 2 | Landing page with GSAP hero and scroll sections | — | 2 |
| 3 | Register / Login / Google sign-in, token handling, protected routes | API 2–3 | 2 |
| 4 | Dashboard: profile completion, recent applications, recent JDs | API 4, 15 | 3 |
| 5 | Profile editor: RHF + Zod, one form per section, optimistic updates | API 4–6 | 3 |
| 6 | New-application wizard shell, step state in Zustand | — | 4 |
| 7 | Step 1–3: JD input (text/URL/file) → confirmation diff → analysis view | API 7–8 | 4 |
| 8 | Step 4–5: template picker, generation progress with polling | API 9–10, 12 | 5 |
| 9 | Step 6–7: assessment panel with animated scores, PDF.js preview | API 11, 13 | 6–7 |
| 10 | Step 8–10: cover letter editor, email composer, Gmail draft, save | API 16 | 8 |
| 11 | Application history, status board, filters | API 15 | 8 |
| 12 | Accessibility pass, reduced-motion, empty/error/loading states, Lighthouse | all | 9 |

Rules enforced in review: server data lives only in TanStack Query; Zustand holds wizard
step, panel visibility and theme only; no GSAP inside forms, the resume preview, or any
focus-managed control.

---

## 7. AI workflow

```text
Confirmed JD (jd-service)          Evidence inventory (profile-service)
        │                                        │
        └──────────────┬─────────────────────────┘
                       ▼
            resume-service builds a generation job
                       ▼  POST /internal/ai/evidence-selection
                 ai-service
                       ▼  system prompt v1 · JD wrapped as untrusted DATA
                    Groq API  (response_format = json_object)
                       ▼
            Stage 1 output: requirement → evidenceIds + matchStrength
                       ▼  JSON Schema validation
                       ▼  every evidenceId must exist in the inventory
                       ▼  POST /internal/ai/resume-content
                    Groq API
                       ▼
            Stage 2 output: summary, bullets, projects, skills ordering
                       ▼  JSON Schema validation
                       ▼  GROUNDING VALIDATION  (see below)
                 ┌─────┴──────┐
              pass          fail → regenerate once → revalidate
                 │                      │
                 │                      └── still failing: drop the unsupported claim
                 │                          and surface it as a missing requirement
                 ▼
        Validated resume JSON → resume_versions → document-service
```

Grounding validation rejects content when any of the following is true (§13):
unknown or missing evidence ID; a metric absent from the cited evidence; a technology,
employer, certification or date not present in the profile; contact details not from the
profile; any external URL; any zero-width or bidirectional control character.

Prompt-injection defence: JD text is delivered inside a delimited `<job_description>` block
labelled untrusted, the system prompt states that its contents are data and never
instructions, and the response is constrained to a JSON schema so instruction-following
prose cannot survive parsing.

Prompts are versioned files under `ai-service/src/main/resources/prompts/<name>/vN.txt`.
The version used is recorded on every `resume_version` for reproducibility.

---

## 8. Document-generation workflow

```text
Validated resume JSON + templateId + templateVersion
        ▼
document-service render job (Redis Stream, idempotent by resumeVersionId+format)
        ▼
Thymeleaf/HTML template ──► OpenHTMLToPDF ──► PDF
                       └──► docx4j        ──► DOCX
        ▼
PDF text-extraction self-check (the artifact must be machine-readable)
        ▼
Upload to private bucket under a random object key
        ▼
rendered_documents: {userId, resumeVersionId, format, objectKey, sha256,
                     templateId, templateVersion, pageCount, bytes}
        ▼
GET /api/documents/{id}/download → ownership check → 300-second presigned URL
```

The bucket is never public. No file is ever served from a static directory. The download
endpoint returns a URL, not bytes, and refuses if `document.userId != callerId`.

---

## 9. Security implementation order

| Order | Control | Milestone |
|---|---|---|
| 1 | Secret hygiene: `.env` gitignored, Gitleaks in CI, no secrets in Dockerfiles | 1 ✅ |
| 2 | Gateway JWT verification, identity-header stripping, Redis rate limits | 1 ✅ |
| 3 | Standard error envelope; no stack traces, paths or credentials to clients | 1 ✅ |
| 4 | Password hashing (BCrypt cost 12), refresh-token rotation with reuse detection | 2 |
| 5 | HttpOnly · Secure · SameSite=Lax refresh cookie; access token in memory only | 2 |
| 6 | Account lockout and per-IP throttling on login/register | 2 |
| 7 | Ownership checks on every profile route; 404 for another user's resource | 3 |
| 8 | File validation: MIME allowlist, magic-byte signature, size cap, quarantine | 4 |
| 9 | SSRF guard: scheme/host/IP allowlisting, DNS-rebinding re-check, redirect and size caps | 4 |
| 10 | Prompt-injection defence, schema validation, PII egress filter, no prompt disclosure | 5 |
| 11 | Grounding validation as a hard gate before persistence | 5 |
| 12 | Private buckets, random object keys, short-lived presigned URLs | 6 |
| 13 | Antivirus scan (ClamAV) before any uploaded file is parsed | 6 |
| 14 | Audit log of security events; correlation IDs everywhere; no sensitive values logged | 8 |
| 15 | GDPR/DPDP export and erasure across all services | 9 |
| 16 | Dependency/container scanning gates, load testing, external pen test | 9 |

---

## 10. Testing strategy

| Layer | Tools | What it must cover |
|---|---|---|
| Unit | JUnit 5, Mockito, AssertJ | ATS check arithmetic and every band boundary (ADR-008/009), JD requirement classification, grounding validators, SSRF URL predicate, file-signature validation, JWT/refresh rotation |
| Slice | `@WebMvcTest`, `@DataMongoTest` | DTO validation, error envelope shape, index presence, ownership scoping of every query |
| Integration | Testcontainers (`mongodb-atlas-local`, Redis, MinIO) | Repository behaviour, refresh-token reuse detection, render → upload → presign round trip |
| Contract | springdoc + OpenAPI validator | Responses match `docs/API_CATALOG.md`; no undocumented endpoint ships |
| Security | Custom suites in `tests/security` | SSRF payload corpus, malicious uploads, IDOR/BOLA probes across all owned resources, expired/forged tokens, rate-limit enforcement |
| AI evaluation | Golden dataset in `tests/ai-eval` | Normal JDs, sparse profiles, unrelated JDs, career changes, missing skills, prompt injection, fabricated metrics, non-job text. **Zero fabrication tolerated.** |
| E2E | Playwright | Signup → profile → JD → confirm → generate → assess → preview → download |

Definition of green: `mvn verify` passes, frontend typecheck and build pass, Gitleaks and
Semgrep report nothing new, Trivy reports no unfixed HIGH/CRITICAL.

---

## 11. Docker strategy

- **No MongoDB container.** Atlas is the database; `MONGODB_URI` comes from `.env`
  (blueprint §7, prompt §9). Tests use Testcontainers instead (ADR-003).
- One Dockerfile per deployable service, multi-stage, built from the repository root so
  the Maven reactor resolves the parent POM and `platform-common`.
- Runtime image is `eclipse-temurin:21-jre-alpine`, non-root user, `MaxRAMPercentage=75`,
  readiness-probe healthcheck.
- Frontend builds with Vite and is served by nginx with a restrictive header set.
- Only `api-gateway` (8080), `frontend` (5173) and the observability UIs publish ports.
  Business services use `expose:` and are unreachable from the host (ADR-007).
- Compose profiles give the two required development modes:

  | Mode | Command | What runs in Docker |
  |---|---|---|
  | 1 — everything containerised | `docker compose --profile app up --build` | infra + all services + frontend |
  | 2 — infra only | `docker compose up -d` | Redis, MinIO, Prometheus, Grafana, OTel |

---

## 12. Milestones

| # | Milestone | Exit criteria | Status |
|---|---|---|---|
| 1 | **Foundation** — repo, reactor, Docker, Config Server, Eureka, Gateway, Redis, MinIO, observability, six documents, CI | `docker compose --profile app up` reaches healthy; gateway returns the standard 401 envelope for an unauthenticated call; CI green | ✅ Complete |
| 2 | **Auth** — register, login, JWT, refresh rotation with reuse detection, Google OAuth, lockout | Unauthorised access to any protected route returns the envelope; refresh-reuse revokes the family; OAuth round trip passes E2E | Next |
| 3 | **Profile** — personal info, education, experience, skills, certifications, projects, versioning, evidence inventory, resume import | Every item carries a stable `evidenceId`; cross-user access returns 404; profile version history immutable | Planned |
| 4 | **JD** — text, file and URL ingestion, SSRF guard, extraction, normalisation, confirmation, analysis | SSRF corpus fully blocked; malicious files rejected; generation is impossible before confirmation | Planned |
| 5 | **AI + Resume** — Groq client, prompt registry, evidence selection, structured output, grounding, async generation | Golden dataset shows zero fabrication; every bullet resolves to evidence IDs; injection attempts do not alter behaviour | 🔵 ai-service done; resume-service orchestration pending M3/M4 |
| 6 | **Documents** — three ATS-safe templates, PDF, DOCX, preview, private storage, secure download | Rendered PDF passes machine-readability extraction; another user's document is never downloadable | Planned |
| 7 | **Assessment** — ATS engine, JD compatibility, coverage, recommendations, readiness bands | Scores reproducible and explainable to requirement/evidence level; every band boundary unit-tested | Planned |
| 8 | **Application** — cover letter, email, Gmail draft, history, status tracking | Drafts only, never auto-send; full history retrievable with scores and template | Planned |
| 9 | **Hardening** — monitoring, rate limits, backups, GDPR/DPDP, load and security testing | Dashboards live, backup/restore rehearsed, export/erasure verified across services | Planned |

Each milestone follows the same loop: state the scope → list files → implement → test →
fix → update `API_CATALOG`, `CODEBASE`, `DATABASE`, `EXTERNAL_APIS`, `.env.example`,
`docker-compose.yml`, `README` → report done and remaining.

---

## 13. Open decisions deferred past Milestone 1

| Question | Decide by |
|---|---|
| Antivirus engine (ClamAV sidecar vs hosted scanning API) | Milestone 4 |
| Redis Streams consumer-group sizing and DLQ policy | Milestone 5 |
| Groq model pinning and fallback model on rate-limit | Milestone 5 |
| Preview rendering: PDF.js in the browser vs server-side PNG | Milestone 6 |
| Atlas backup cadence and restore-rehearsal schedule | Milestone 9 |
| Secrets manager for production (Vault vs cloud-native) — see ADR-011 | Milestone 9 |
