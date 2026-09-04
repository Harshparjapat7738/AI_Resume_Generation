# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project does

CareerForge AI analyses a confirmed job description against one verified professional profile
and produces **JD-optimization data**: the keywords that matter, which of them the profile can
actually evidence, which requirements it cannot meet, and what to lead with. The user exports
that structured output (JSON, or a ready-to-paste prompt) and creates their resume or cover
letter on whatever platform they prefer.

**CareerForge generates a Resume or Cover Letter PDF from the same grounded evidence JD
optimization already analysed** (ADR-036, reintroducing what ADR-033 removed, behind a versioned
document-model contract). `ai-service` produces a schema-validated, grounded document model;
`render-service` turns that model — and only that model — into a PDF via Thymeleaf + Open HTML to
PDF. No DOCX, no mail-merge, no custom-template support, and no AI call inside rendering itself —
`render-service` never holds `GROQ_API_KEY` and never talks to `ai-service`. Application **email**
content generation remains a separate, still-active feature. The one other document-adjacent
feature is **"My Templates" (ADR-034)**: a user uploads a Resume/Cover Letter file to their
profile once, and later *selects* — never re-uploads — it at JD-optimization handoff time; its
name/filename/type is referenced in the external AI prompt so the user knows which layout to apply
the drafted content to when they choose not to use CareerForge's own generated PDF. There is no
structural analysis, no mail-merge, and no AI involvement in that feature — the file is stored and
returned exactly as uploaded, and `document-service` is not reintroduced (ADR-036's
`render-service` is a smaller, single-purpose, PDF-only service, not a revival of it).

**The one rule everything else serves:** the AI may select, rank, classify and map facts the
user supplied — it must never invent an employer, date, metric, technology, certification,
project or achievement. Everything traces back to a stable `evidenceId` in the user's profile
(`EXP-001`, `SKILL-002`, `PROJ-003`, ...); anything that can't be traced is stripped and
reported as a gap, not silently kept.

Two mechanisms enforce that in code, not just in the prompt, because the two operations produce
different things:

- **JD optimization** emits no prose, so there is nothing to validate sentence-by-sentence.
  Since ADR-038 the LLM call itself (`ai-service`'s `JdOptimizationService`, now adjudication-only)
  only ever sees requirements plus evidence `jd-service`'s `EvidenceMatcher` already pre-selected
  for relevance — `stripUnknownIds` still removes any requirement/evidence id the model invents
  anyway. `missingRequirements` and `emphasis` are no longer asked of the model at all — jd-service's
  `OptimizationMerge` computes them deterministically (a missing requirement is a set difference,
  emphasis is a weighted sort), so there's nothing there to hallucinate in the first place. A match
  left with no surviving evidence is downgraded to `NONE` rather than allowed to stand.
- **Email content** is prose, so `GroundingValidator` applies as it always has — see the
  `ai-service` entry in `docs/CODEBASE.md` §2 for exactly what it checks.

A missing requirement stays missing: the optimization reports it as a gap and the external
prompt explicitly forbids claiming it. It also does **not** promise a job or display a
fabricated hiring probability. JD-fit scoring is computed deterministically in Java from the
optimization, the JD and the profile — never asked of the LLM (ADR-009, ADR-033). **ATS
*structural* scoring is back, scoped narrowly (ADR-040):** it scores the same
deterministically-assembled, cited-evidence resume content `ResumeRenderService` builds for
`render-service` — never a rendered PDF/DOCX — so it is computed independently of whether that
render call succeeds. Together with the JD-fit compatibility score and readiness band, the result
page always shows three real, non-fabricated numbers (ATS score, JD-match score, chances of
selection) even when PDF generation fails, so the user is never left with just an error.

## Where things live

A Maven multi-module reactor (10 Spring Boot services + the `platform-common` library, Java 21) plus one React frontend.
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
| `profile-service` | 8082 | The candidate's verified data: personal info + six evidence-bearing sections (education, experience, skills, projects, certifications, achievements), each item carrying a stable `evidenceId`. **Sole source of truth** — if it's not here, no optimization may assert it. Also owns **"My Templates"** (ADR-034): a `templates` collection of user-uploaded Resume/Cover Letter files (validated, never parsed) backed by the MinIO/S3 bucket `document-service` used to own — upload/list/rename/set-default/delete/download at `/api/profile/templates/**`. |
| `jd-service` | 8083 | Job description intake (paste text or SSRF-guarded URL fetch — ADR-015), requirement extraction/analysis — no confirm/review gate any more (ADR-037: `analyse`/`optimise` always read the JD's `currentVersion` directly) — **and JD optimization**, the product's primary output (ADR-033, restructured by ADR-038): deterministically pre-filters the profile's evidence (`EvidenceMatcher`) down to a handful of lexically-relevant candidates per requirement before it ever reaches `ai-service`, sends at most one adjudication call, deterministically assembles keywords/missing-requirements/emphasis (`OptimizationMerge`), and persists the result (`jd_optimizations`, one per JD version) behind a Redis-backed single-flight lock. Adding one skill from the Skill Gap screen (`POST /{id}/optimize/patch`) patches the cached result deterministically — zero Groq calls. |
| `render-service` | 8084 | Renders an already-grounded, schema-validated document model into a PDF (ADR-036): Thymeleaf template fill → jsoup/W3CDom strict-XHTML normalisation → Open HTML to PDF (PDFBox). PDF-only — no DOCX, no mail-merge, no custom-template support. Holds no AI credential and never calls `ai-service`; owns its own MinIO/S3 bucket, distinct from `profile-service`'s "My Templates" bucket (ADR-034). |
| `ai-service` | 8085 | The **only** process holding `GROQ_API_KEY` (ADR-012, internal-only, no gateway route). Six operations: JD analysis, evidence selection, **JD optimization**, email content, and (ADR-036) **resume content** + **cover-letter content** — the two new operations produce a versioned, schema-validated document model that `render-service` renders, never `ai-service` itself. Versioned prompts, JD fenced as untrusted data, JSON-Schema-validated output, `GroundingValidator` (the anti-fabrication gate, now applied to resume/cover-letter content exactly as it already was for email prose). Groq is the only provider — Gemini was removed entirely (ADR-033) and stays removed under ADR-036. |
| `assessment-service` | 8086 | Deterministic JD-fit/screening-readiness scoring, keyed on the JD optimization (ADR-033), plus the revived ATS *structural* score (ADR-040) — scored from the same pre-render, cited-evidence content `application-service`'s `ResumeRenderService` assembles, never a rendered document, so it exists whether or not the PDF render step later succeeds. Never calls an LLM. |
| `application-service` | 8088 | The central `Application` aggregate, **application-email generation** (ADR-017/019), and (ADR-036) **resume/cover-letter generation orchestration** — calls `ai-service` for the document model, then `render-service` for the PDF, and persists `ResumeVersion`/`CoverLetterVersion` scoped to the `Application`. Cover-letter generation, removed by ADR-033, is back under this orchestration. |

`frontend/` — React 19 + TypeScript + Vite + Tailwind 4, feature-folder structure:
- `src/features/<area>/` — one folder per screen area (`onboarding`, `profile`, `generate`, `results`, `dashboard`, `applications`, `emails`, `analytics`, ...), each with its page(s) and local `components/`. The generation flow (`generate/`) is **Job Description → Skill Gap → Output Type → Generate/Processing → Result** (ADR-037) — there is no Confirm JD step and no standalone Review page; `GenerationSkillGapPage` runs the real JD analysis/optimization call automatically and lets a missing skill be added and re-checked in place.
- `src/services/*Api.ts` — one client module per backend service (`profile-service` has two: `profileApi.ts` for the profile itself and `templateApi.ts` for "My Templates"), all going through the single fetch wrapper `apiClient.ts` (auth header, `credentials: 'include'` for the refresh cookie, `ApiError` on non-2xx). No component calls `fetch` directly.
- `src/components/ui/` — shared primitives (Button, Card, Select, TextField, ...) reused across features rather than redefined per screen.
- `src/routes/router.tsx` — the full route table; everything except `/`, `/login`, `/register` sits behind `ProtectedRoute`.

`docs/` — the authoritative deep-dive references, kept current as part of the same PR that changes what they describe:
| File | Read it for |
|---|---|
| `CODEBASE.md` | Per-service responsibilities, request flows through the whole stack, cross-cutting conventions |
| `API_CATALOG.md` | Every endpoint, request/response shape, error code — implemented vs. planned |
| `API_INTEGRATION.md` | Which frontend file calls which endpoint; session/auth/onboarding redirect logic |
| `DATABASE.md` | Per-service Mongo collections, indexes, retention |
| `ARCHITECTURE_DECISIONS.md` | 40 ADRs — every place implementation deviated from the original blueprint, and why. Many are marked "Superseded by ADR-033" (the resume/cover-letter/document decisions); read the index table's Status column before trusting an older one. ADR-038 (Groq rate-limit redesign), ADR-039 (Gemini reintroduced as fallback for JD analysis/adjudication only) and ADR-040 (ATS structural scoring revived, scoped to pre-render content only) are the most recent |
| `EXTERNAL_APIS.md` | Groq/Google OAuth/Atlas/Gmail/SMTP setup, scopes, rate limits |
| `ai-abstraction.md` | The `AiChatClient` contract and why it stayed after Gemini was removed |
| `IMPLEMENTATION_PLAN.md` | Milestones and what's left |

`infrastructure/config-repo/` — the Spring Cloud Config backend (secret-free `application.yml`, `${ENV_VAR}` refs only). `infrastructure/{grafana,prometheus,otel}/` — observability provisioning. `scripts/*.ps1` — Windows helpers for running the stack without Docker (see below). Root `tests/` currently holds only a README describing planned cross-service suites (`e2e`/`contract`/`ai-eval`/`security`) — the real, working e2e suite lives at `frontend/tests/e2e/` (Playwright, against the real backend, no mocking).

## AI providers: Groq primary, Gemini fallback for two operations (ADR-033/039)

**Groq is the primary provider for every operation.** Gemini was removed entirely by ADR-033
(its last consumer then was custom-PDF template analysis) and stayed gone until ADR-039
reintroduced it — deliberately in the *opposite* shape from what was removed: Groq-primary,
Gemini-fallback (not Gemini-primary/Groq-fallback), and only for **JD analysis** and
**JD-optimization adjudication**, the two operations ADR-038 found were actually hitting Groq's
rate limit in practice. Evidence selection and email content have no fallback — a Groq failure
there behaves exactly as it always has.

Six Groq operations: **JD analysis**, **evidence selection**, **JD optimization** (the product's
main output — since ADR-038 an adjudication-only call, not the whole targeting result), **email
content**, and (ADR-036) **resume content** + **cover-letter content**. Every one injects
`AiChatClient` — since ADR-039 its sole implementation is `AiProviderRouter`, not `GroqClient`
directly; `GroqClient` and `GeminiClient` are plain collaborators the router holds, and no
business service branches on provider. `AiChatClient#complete` takes an optional per-call
`maxCompletionTokensOverride` (ADR-038) — JD analysis and adjudication use it to reserve far
less than the old blanket default, honoured by whichever provider ends up serving the call; the
other operations still use it unset. Gemini is entirely optional: no `GEMINI_API_KEY` (or
`GEMINI_ENABLED=false`) means `ai-service` starts fine and behaves exactly Groq-only.

**No AI touches document rendering.** Resume/cover-letter generation returned in ADR-036, but the
document-model contract keeps the boundary exactly where it was: `ai-service` produces and
grounds the content; `render-service` turns an already-validated document model into a PDF — it
never holds `GROQ_API_KEY`, never injects `AiChatClient`, and never calls `ai-service` at all.

**Groq's rate limit is the usual cause of a failed generation**, and it is counter-intuitive:
Groq reserves a call's full `max_completion_tokens` against the per-minute token budget at
admission, *not* what the response actually uses. On an 8,000 tokens/minute account that leaves
little headroom, especially for two calls back-to-back. It is not an outage; `GroqClient` now
logs every response's `x-ratelimit-*`/`retry-after` headers (`logs/ai-service.log`,
`Groq response operation=...`) — check those before assuming one.

**ADR-038 redesigned JD optimization specifically to live inside that budget**, after a live
failure traced directly to it: JD analysis and JD-optimization adjudication both used to reserve
the old blanket `GROQ_MAX_OUTPUT_TOKENS` (4096) regardless of actual need, and the optimization
call used to see the candidate's *entire* evidence inventory. Now: `jd-analysis` reserves 1,200
completion tokens, adjudication reserves 1,000; jd-service's `EvidenceMatcher` deterministically
pre-filters evidence to a handful of lexically-relevant candidates per requirement (≤40 units,
≤~6,000 chars) before anything reaches `ai-service`; a requirement with zero candidates never
reaches Groq at all. `GroqClient` classifies a 429 before retrying — Groq's "this request alone
exceeds the limit" wording is never retried at any delay, a temporary one gets exactly one retry
honouring `retry-after`. A Redis-backed single-flight lock (`SingleFlightLock`, best-effort —
degrades to no coalescing if Redis is unreachable) coalesces a double-click into one computation.
Adding a skill from the Skill Gap screen no longer spends a Groq call at all — it patches the
cached result deterministically. See ADR-038 for the full design and its one known gap: the
optimization cache key is `jdVersionId` only, so a prompt/model/filter-logic change doesn't
auto-invalidate an already-cached result (only an explicit `refresh=true` does).

## Known loose ends

- **MinIO/`minio-init` in `docker-compose.yml` are active again, not orphaned.** `document-service`
  (their original consumer) is gone, but `profile-service` became their new, and only, consumer
  for **"My Templates"** (ADR-034) — `S3_*`/`MINIO_ROOT_*` are back in `.env.example`, scoped to
  profile-service. Don't delete the compose services or treat those env vars as dead.
- **Six legacy Mongo collections have zero reads and zero writes** (`resume_versions`,
  `resume_generations`, `cover_letter_versions`, `rendered_documents`,
  `custom_template_assets`, `ats_assessments`). Deliberately not dropped — `docs/DATABASE.md`
  has the backup-then-drop procedure. `custom_template_assets` is the old, dead `document-service`
  collection — don't confuse it with profile-service's live `templates` collection (ADR-034).
  `resume_versions`/`resume_generations`/`cover_letter_versions` are likewise dead — don't confuse
  them with the new `ResumeVersion`/`CoverLetterVersion` collections ADR-036 adds in
  `application-service`; they're deliberately different names precisely to avoid that confusion.
- **`render-service` owns its own MinIO/S3 bucket (ADR-036), separate from `profile-service`'s
  "My Templates" bucket (ADR-034).** The two are never the same bucket and never share a consumer —
  don't route render-service through profile-service's `ObjectStorageService` or vice versa.
- **ATS structural scoring is back (ADR-040), but only in the narrow shape that never depends on
  a rendered document.** It scores `ResumeRenderService`'s pre-render, cited-evidence content —
  the same content sent to `render-service` — persisted in a **new** collection,
  `ats_structural_assessments`. `ats_assessments` (old, `resumeVersionId`-keyed) is still one of
  the dead collections above and was **not** reused — don't confuse the two. The result page
  (`OptimizationResultPage.tsx`) fetches both this and the JD-fit assessment unconditionally on
  load and always shows a "Your scores" card (ATS score, JD-match score, readiness band); a failed
  `generateResumePdf` call shows `ResumeGenerationFailure` pointing back at those already-visible
  scores and the JSON/AI-prompt export, instead of only a toast.
- **`AssessmentController`'s base path was `/api/assessment/resume-versions`, a stale leftover
  from before ADR-033 rekeyed everything to `jobDescriptionId` — ADR-040 renamed it to
  `/api/assessment`.** The frontend's `assessmentApi.ts` was already calling the current shape,
  so this had been silently 404ing on every call (masked because every caller already treats a
  failed assessment fetch as "nothing computed yet" rather than an error) until this fix.
  `application-service`'s `AssessmentServiceClient` (a separate, already-dead,
  `resumeVersionId`-keyed lookup with no live caller in the current resume-render flow) was
  renamed to the same new base path for textual consistency only — it remains non-functional;
  fixing it for real would mean deciding what it should even look up now that resume-service is
  gone, which is out of scope here.
- **No frontend unit tests exist.** `npm test` finds no files: vitest is installed but there is
  no `@testing-library/react`, no DOM environment, and no config. Typecheck + build are the
  real gates.
- **The old Confirm JD step and standalone Review/"Generate application" page are gone (ADR-037),
  not hidden.** `JdService.confirm`, `POST /{id}/confirm`, `JD_NOT_CONFIRMED`,
  `GenerationReviewPage.tsx`, `ConfirmAnalysisModal.tsx` no longer exist. `JobDescriptionStatus`'s
  `CONFIRMED`/`REJECTED` enum values and `JobDescription`'s `confirmedAt`/`confirmedVersion` fields
  are deliberately still there (unused) so Spring Data doesn't choke deserialising an older
  persisted Atlas document — don't "clean them up" without re-checking that reasoning first.
- **The JD-optimization cache key is `jdVersionId` only (ADR-038's one known gap), not a
  composite of JD/evidence/prompt/model/filter versions.** A prompt, schema, model, or
  `EvidenceMatcher` constant change does **not** auto-invalidate an already-persisted
  `JdOptimization` — only an explicit `refresh=true` recomputes it. Building real content-addressed
  cache keys was deliberately deferred (out of scope for that change), not forgotten.

## How work gets done

### Running the stack

Three interchangeable modes (`README.md` "Local setup" has full detail):

```bash
# Mode 1 — everything in Docker (slow first build, zero local setup)
docker compose --profile app up --build

# Mode 2 — infra in Docker, services run locally (fastest edit loop)
docker compose up -d                              # Redis, Prometheus, Grafana, OTel, MinIO (profile-service's "My Templates" storage)
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
`openssl rand -base64 48`) to boot; add `GROQ_API_KEY` before touching any AI operation, and
`MINIO_ROOT_USER`/`MINIO_ROOT_PASSWORD`/`S3_*` before touching "My Templates" (profile-service).
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
