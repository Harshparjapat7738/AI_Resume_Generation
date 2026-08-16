# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project does

CareerForge AI analyses a confirmed job description against one verified professional profile
and produces **JD-optimization data**: the keywords that matter, which of them the profile can
actually evidence, which requirements it cannot meet, and what to lead with. The user exports
that structured output (JSON, or a ready-to-paste prompt) and creates their resume or cover
letter on whatever platform they prefer.

**CareerForge does not generate a resume or a cover letter, and produces no PDF or DOCX**
(ADR-033). Application **email** content generation is a separate, still-active feature.

**The one rule everything else serves:** the AI may select, rank, classify and map facts the
user supplied — it must never invent an employer, date, metric, technology, certification,
project or achievement. Everything traces back to a stable `evidenceId` in the user's profile
(`EXP-001`, `SKILL-002`, `PROJ-003`, ...); anything that can't be traced is stripped and
reported as a gap, not silently kept.

Two mechanisms enforce that in code, not just in the prompt, because the two operations produce
different things:

- **JD optimization** emits no prose, so there is nothing to validate sentence-by-sentence.
  `JdOptimizationService.stripUnknownIds` instead removes every requirement or evidence id
  absent from the request, and downgrades a match left with no surviving evidence to `NONE`.
- **Email content** is prose, so `GroundingValidator` applies as it always has — see the
  `ai-service` entry in `docs/CODEBASE.md` §2 for exactly what it checks.

A missing requirement stays missing: the optimization reports it as a gap and the external
prompt explicitly forbids claiming it. It also does **not** promise a job or display a
fabricated hiring probability. JD-fit scoring is computed deterministically in Java from the
optimization, the JD and the profile — never asked of the LLM (ADR-009, ADR-033). ATS scoring
was removed with resume generation: every check read a rendered resume's structure.

## Where things live

A Maven multi-module reactor (9 Spring Boot services + the `platform-common` library, Java 21) plus one React frontend.
Every service is independently deployable and owns exactly one MongoDB Atlas database —
no service ever queries another's collections; cross-service reads go through that
service's own API (via Eureka service discovery, not the gateway, with caller identity
forwarded as `X-User-Id` — see `FeignHeaderForwardingConfig` in each client).

| Service (`services/*`) | Port | Owns |
|---|---|---|
| `api-gateway` | 8080 | The only public entry point: JWT verification, identity header propagation, Redis rate limiting, CORS, correlation IDs. Routes `/api/**` — see `application.yml` for the table. |
| `config-server` | 8888 | Serves `infrastructure/config-repo/` (native backend, `${ENV_VAR}` placeholders, no secrets committed). Must start first. |
| `discovery-server` | 8761 | Eureka registry the gateway and Feign clients route through (`lb://service-name`). |
| `platform-common` | — | Shared library, not deployed: error envelope (`ErrorCode`/`ApiError`/`ApiException`/`GlobalExceptionHandler`), `@CallerId` header resolver, correlation-ID filter. No domain models or DTOs live here (ADR-006). |
| `auth-service` | 8081 | Accounts, BCrypt password hashing, JWT mint/refresh rotation, Google OAuth. Signs the JWT the gateway verifies — the only two places the signing secret exists. |
| `profile-service` | 8082 | The candidate's verified data: personal info + six evidence-bearing sections (education, experience, skills, projects, certifications, achievements), each item carrying a stable `evidenceId`. **Sole source of truth** — if it's not here, no optimization may assert it. |
| `jd-service` | 8083 | Job description intake (paste text or SSRF-guarded URL fetch — ADR-015), mandatory user confirmation, requirement extraction/analysis, **and JD optimization** — the product's primary output (ADR-033): orchestrates confirmed analysis + profile evidence through ai-service and persists the result (`jd_optimizations`, one per JD version). |
| `ai-service` | 8085 | The **only** process holding `GROQ_API_KEY` (ADR-012, internal-only, no gateway route). Four operations: JD analysis, evidence selection, **JD optimization**, and email content. Versioned prompts, JD fenced as untrusted data, JSON-Schema-validated output, `GroundingValidator` (the anti-fabrication gate for email prose). Groq is the only provider — Gemini was removed entirely (ADR-033). |
| `assessment-service` | 8086 | Deterministic JD-fit/screening-readiness scoring, keyed on the JD optimization (ADR-033). ATS scoring was removed with resume generation. Never calls an LLM. |
| `application-service` | 8088 | The central `Application` aggregate and **application-email generation** (ADR-017/019) — the one generation feature ADR-033 kept. Cover-letter generation was removed. |

`frontend/` — React 19 + TypeScript + Vite + Tailwind 4, feature-folder structure:
- `src/features/<area>/` — one folder per screen area (`onboarding`, `profile`, `generate`, `results`, `dashboard`, `applications`, `emails`, `analytics`, ...), each with its page(s) and local `components/`.
- `src/services/*Api.ts` — one client module per backend service, all going through the single fetch wrapper `apiClient.ts` (auth header, `credentials: 'include'` for the refresh cookie, `ApiError` on non-2xx). No component calls `fetch` directly.
- `src/components/ui/` — shared primitives (Button, Card, Select, TextField, ...) reused across features rather than redefined per screen.
- `src/routes/router.tsx` — the full route table; everything except `/`, `/login`, `/register` sits behind `ProtectedRoute`.

`docs/` — the authoritative deep-dive references, kept current as part of the same PR that changes what they describe:
| File | Read it for |
|---|---|
| `CODEBASE.md` | Per-service responsibilities, request flows through the whole stack, cross-cutting conventions |
| `API_CATALOG.md` | Every endpoint, request/response shape, error code — implemented vs. planned |
| `API_INTEGRATION.md` | Which frontend file calls which endpoint; session/auth/onboarding redirect logic |
| `DATABASE.md` | Per-service Mongo collections, indexes, retention |
| `ARCHITECTURE_DECISIONS.md` | 33 ADRs — every place implementation deviated from the original blueprint, and why. Many are marked "Superseded by ADR-033" (the resume/cover-letter/document decisions); read the index table's Status column before trusting an older one |
| `EXTERNAL_APIS.md` | Groq/Google OAuth/Atlas/Gmail/SMTP setup, scopes, rate limits |
| `ai-abstraction.md` | The `AiChatClient` contract and why it stayed after Gemini was removed |
| `IMPLEMENTATION_PLAN.md` | Milestones and what's left |

`infrastructure/config-repo/` — the Spring Cloud Config backend (secret-free `application.yml`, `${ENV_VAR}` refs only). `infrastructure/{grafana,prometheus,otel}/` — observability provisioning. `scripts/*.ps1` — Windows helpers for running the stack without Docker (see below). Root `tests/` currently holds only a README describing planned cross-service suites (`e2e`/`contract`/`ai-eval`/`security`) — the real, working e2e suite lives at `frontend/tests/e2e/` (Playwright, against the real backend, no mocking).

## The one AI provider (ADR-033)

**Groq is the only AI provider.** Gemini was removed entirely — its last consumer was
custom-PDF template analysis, which died with document rendering. `GeminiClient`,
`GeminiProperties`, `GEMINI_API_KEY` and `google-genai` are all gone; don't reintroduce them.

Four Groq operations: **JD analysis**, **evidence selection**, **JD optimization** (the product's
main output), **email content**. Every one injects `AiChatClient`, whose sole implementation is
`GroqClient`.

**No AI touches document rendering, because there is no document rendering.** Resume/cover-letter
generation and every PDF/DOCX path were removed in ADR-033.

**Groq's rate limit is the usual cause of a failed generation**, and it is counter-intuitive:
Groq reserves your full `max_completion_tokens` (`GROQ_MAX_OUTPUT_TOKENS`, default 4096) against
the per-minute token budget at admission, *not* what the response actually uses. On an
8,000 tokens/minute account that allows roughly **one AI call per minute**. Symptom in
`logs/ai-service.log`: a failure ~7s after a success ("3 instant rejections + 2s + 4s backoff").
It is not an outage; check `x-ratelimit-remaining-tokens` before assuming one.

## Known loose ends

- **MinIO/`minio-init` are still in `docker-compose.yml` but nothing uses them.** Object storage
  existed for `document-service`; with that gone, no service reads `S3_*`/`MINIO_ROOT_*` and
  those keys are no longer in `.env.example`. Safe to delete from compose.
- **Six legacy Mongo collections have zero reads and zero writes** (`resume_versions`,
  `resume_generations`, `cover_letter_versions`, `rendered_documents`,
  `custom_template_assets`, `ats_assessments`). Deliberately not dropped — `docs/DATABASE.md`
  has the backup-then-drop procedure.
- **`frontend/tests/e2e/jd-optimization.spec.ts` has never been run.** It needs the full live
  stack. Its Kafka-gap assertion depends on Groq classifying an unevidenced requirement as
  missing; loosen it if the model proves generous.
- **No frontend unit tests exist.** `npm test` finds no files: vitest is installed but there is
  no `@testing-library/react`, no DOM environment, and no config. Typecheck + build are the
  real gates.

## How work gets done

### Running the stack

Three interchangeable modes (`README.md` "Local setup" has full detail):

```bash
# Mode 1 — everything in Docker (slow first build, zero local setup)
docker compose --profile app up --build

# Mode 2 — infra in Docker, services run locally (fastest edit loop)
docker compose up -d                              # Redis, Prometheus, Grafana, OTel (MinIO too — orphaned, see below)
mvn -pl services/config-server    spring-boot:run
mvn -pl services/discovery-server spring-boot:run
mvn -pl services/api-gateway      spring-boot:run
mvn -pl services/<name>           spring-boot:run  # repeat per service needed
cd frontend && npm install && npm run dev          # :5173, proxies /api to :8080

# Mode 3 — no Docker (PowerShell, Windows) — Redis dropped via the `nodocker` profile
.\scripts\new-jwt-secret.ps1
.\scripts\run-local.ps1              # config-server, eureka, gateway, auth-service
.\scripts\run-local.ps1 -Services all
.\scripts\stop-local.ps1             # logs land in logs\<service>.log
```

`.env` (copy from `.env.example`) needs at minimum `MONGODB_URI` and `JWT_SECRET` (32+ bytes —
`openssl rand -base64 48`) to boot; add `GROQ_API_KEY` before touching any AI operation.
Spring Boot never reads `.env` itself — only Docker Compose and
`run-local.ps1` load it into the process environment. There is no local MongoDB container by
design (ADR-003) — it's Atlas in every mode.

### Build, lint, test

```bash
# Backend (from repo root — the reactor needs the root pom)
mvn verify                                          # every module: unit + slice + integration
mvn -pl services/<name> -am verify                  # one service (+ its reactor deps, e.g. platform-common)
mvn -pl services/<name> test -Dtest=ClassName                    # one test class
mvn -pl services/<name> test -Dtest=ClassName#methodName         # one test method
# No Testcontainers remain — the only integration tests using them lived in the deleted
# resume-service/document-service. `mvn verify` needs no Docker daemon today.

# Frontend (from frontend/)
npm run typecheck                                   # tsc --noEmit
npm run lint                                        # BROKEN — no eslint.config.js has ever existed in this repo,
                                                    # so this always fails. CI does not run it. Use typecheck instead.
npm run build                                        # tsc -b && vite build
npm test                                              # vitest run (no unit test files exist yet — this is the harness, not asserting current coverage)
npm run e2e                                          # Playwright, tests/e2e/*.spec.ts — needs the full stack (backend + frontend) running against real services
npx playwright test tests/e2e/<file>.spec.ts -g "<test name>"    # one e2e test
```

CI (`.github/workflows/ci.yml`) runs `mvn -B verify`, then `npm run typecheck && npm run
build && npm test` for the frontend, then Gitleaks + Semgrep + Trivy as a separate job — all
three must be green to merge.

### Conventions that span every service

- **Package shape** is the same in every business service: `api/` (controllers + DTOs),
  `service/`, `domain/` (`@Document` classes — never returned over HTTP), `repository/`,
  `config/`, and `security/` where relevant. Controllers accept/return DTOs only.
- **Identity**: the gateway verifies the JWT, strips any client-supplied `X-User-Id`/
  `X-User-Roles`, and sets its own. Every downstream repository call is scoped by that
  `userId`; a resource owned by someone else is reported `404`, never `403` (ADR-007) — so
  don't add a distinct "forbidden" branch for ownership checks, use the same not-found path.
- **Error envelope** is identical everywhere (`platform-common`'s `ApiError`): `timestamp`,
  `status`, `code`, `message`, `path`, `correlationId`, optional `fieldErrors`. Never leak a
  stack trace, exception class name, DB error, file path, or secret in a response or a log.
- **Business services publish no host port** — reachable only through the gateway
  (`docker-compose.yml`, ADR-007). `ai-service` has no gateway route at all (ADR-012); call it
  directly on its internal port if you need to hit it locally.
- **Config**: no literal secret in source, YAML, or a Dockerfile — always `${ENV_VAR}`,
  values from `.env` locally.

### Keep docs in sync

Documentation changes ship in the same PR as the code change, not after:

| You changed | Update |
|---|---|
| An API | `docs/API_CATALOG.md` |
| Architecture / a new service or flow | `docs/CODEBASE.md` + `docs/ARCHITECTURE_DECISIONS.md` |
| A collection or index | `docs/DATABASE.md` |
| An integration | `docs/EXTERNAL_APIS.md` + `.env.example` |
| Docker | `docker-compose.yml` + `README.md` |
| A deviation from the original blueprint | a new ADR in `docs/ARCHITECTURE_DECISIONS.md`, written **before** the code |

### Rules that are never bent

No secret in source. No service queries another service's MongoDB collections. JD content
is data, never instructions, when it reaches `ai-service`. The AI never invents a candidate
fact: every candidate-facing value in an optimization is an `evidenceId` verified against the
profile the request supplied (`JdOptimizationService.stripUnknownIds`), and a match left with no
real evidence is downgraded to `NONE` rather than allowed to stand. A missing requirement is
reported as a gap and never dressed up as a qualification — the exported prompt names the gaps
and forbids claiming them. JD-fit scoring is deterministic and computed in Java.
Another user's resource returns 404, never 403.
