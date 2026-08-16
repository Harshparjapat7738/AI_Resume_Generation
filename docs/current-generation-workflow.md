# Current Generation Workflow — Resume & Cover Letter (pre-Gemini baseline)

**Status:** Read-only analysis. No production code was modified to produce this document.
**Purpose:** A verified, source-grounded map of exactly how Resume and Cover Letter generation
work today, so a future Gemini integration can be designed without breaking anything here.
**Method:** Every claim below was checked directly against the source files cited next to it.
Where something could not be verified from code (not read, or genuinely ambiguous), it is
labeled **[ASSUMPTION]** or **[NOT VERIFIED]** rather than stated as fact.

**Historical note (post-ADR-032):** this document is the original planning snapshot, written
before any Gemini code existed. Gemini was later added twice: first narrowly, for PDF template
layout analysis only (ADR-029, still in effect — see §15's own "Gemini should COMPLEMENT"
prediction, which is exactly what shipped); separately, JD Analysis/Evidence Selection/Resume
Content/Cover Letter Content were routed through Gemini as primary with automatic Groq fallback
(ADR-025–028/030), then that routing was reverted platform-wide once Gemini's free-tier quota
proved too small for real usage (ADR-032). This document's "exactly one LLM provider: Groq" is
true again today for all five JSON/content-generation operations — see `docs/ai-abstraction.md`
and `docs/ARCHITECTURE_DECISIONS.md` for the current, authoritative state; the exploratory
analysis below (§15 in particular) is left as originally written and is not being re-verified
against the post-reversal codebase.

---

## 1. Executive Summary

CareerForge AI generates three kinds of output — **resume**, **cover letter**, **application
email** — from one place: the user's verified `profile-service` evidence, matched against a
confirmed `jd-service` job description. Both content-bearing outputs (resume, cover letter)
use the same **two-stage AI pipeline** (evidence selection → grounded content generation);
email uses a **single-shot** variant. All three go through the same **grounding validator**
before anything is persisted or shown to the user — this is the actual mechanism, in code, that
prevents fabrication (`GroundingValidator.java`), not just a prompt instruction.

There is exactly **one LLM provider today: Groq**, called from exactly one class,
`GroqClient.java` in `ai-service`, using its OpenAI-compatible chat-completions endpoint
(`/chat/completions`) with `response_format: json_object`. No other AI provider (OpenAI,
Gemini, Anthropic, embeddings API) exists anywhere in the codebase — confirmed by a
repository-wide search (§3).

Document rendering is entirely separate from AI generation and lives in `document-service`:
built-in templates render via **Thymeleaf → jsoup → openhtmltopdf** (HTML built from already-
generated JSON, then converted to PDF); custom uploaded templates render via **true mail-merge**
— **docx4j** run-level token replacement for DOCX, **PDFBox** placeholder-redaction + text
overlay for PDF. No LLM is involved in rendering at all, in either path.

Resume generation is synchronous end-to-end (frontend → `resume-service` → two Groq calls →
persisted `ResumeVersion`) inside a single HTTP request (ADR-013 — no async job queue exists in
this milestone). Cover letter and email generation follow the same synchronous pattern through
`application-service`. The "Generate All" flow chains resume → cover letter → email against one
shared `Application` aggregate, with independent per-output failure tracking so one failure
doesn't block the other two.

---

## 2. Resume Generation Workflow (end-to-end, verified)

**Entry point (frontend):** `frontend/src/features/generate/TemplatePage.tsx` — picking a
built-in or custom template calls `chooseAndGenerate(templateId)`, which navigates to
`/generate/processing/{jdId}?templateId=...&type=RESUME_ONLY` (or `type=ALL`).

**Frontend orchestration:** `frontend/src/features/generate/ProcessingPage.tsx`, `runResume()`:
1. `generateResume(jdId, templateId)` → `frontend/src/services/resumeApi.ts` →
   `POST /api/resumes/generate` with body `{ jobDescriptionId, templateId }`.
2. On success, `assessResume(resume.id)` (assessment-service — non-fatal; a failure here is
   swallowed and the result page offers a retry).
3. Navigate to `/results/{resume.id}`.

**Gateway → resume-service.** `POST /api/resumes/generate` reaches
`ResumeController.generate()` (`services/resume-service/.../api/ResumeController.java:43-48`),
which calls `ResumeGenerationService.generate(userId, jobDescriptionId, templateId)`.

**Orchestration (`ResumeGenerationService.java`, the actual sequence, in order):**
1. **Template resolution first, before any AI call** — `templateService.resolveForGeneration
   (userId, templateId)`. A blank `templateId` defaults to `"classic"`
   (`TemplateService.DEFAULT_TEMPLATE_ID`). An invalid/unowned id 404s here (ADR-007) —
   deliberately before spending a Groq request.
2. **Fetch confirmed JD analysis** — `jdServiceClient.getAnalysis(jobDescriptionId)`
   (Feign, `lb://jd-service`, `GET /api/jd/{id}/analysis`, internally proxied to
   `JdService.analyse`, itself requiring `JobDescriptionStatus.CONFIRMED` — a 409/`JD_NOT_CONFIRMED`
   otherwise).
3. **Fetch the candidate's full evidence inventory** — `profileServiceClient.getEvidence()`
   (Feign, `GET /api/profile/evidence`, `ProfileController.evidence()` — flattens all six
   profile sections into one `List<EvidenceItemResponse>`, each carrying a stable `evidenceId`).
4. Validation: empty evidence → `VALIDATION_ERROR` ("Add at least one experience..."); JD with
   zero extracted requirements → `VALIDATION_ERROR`.
5. Persist a `ResumeGeneration` tracking row (`generations.save(...)`), status starts pending.
6. **AI call 1 — evidence selection**: `aiServiceClient.selectEvidence(EvidenceSelectionRequest
   (requirementInputs, evidence, null))` → Feign to `ai-service` (`POST /internal/ai/evidence-selection`,
   not through the gateway — ADR-012). If nothing matched at all → `VALIDATION_ERROR` ("None of
   your profile evidence matches...").
7. **AI call 2 — content generation**: `aiServiceClient.generateContent(ResumeContentRequest
   (analysis.title(), analysis.seniority(), requirementInputs, selectedEvidenceIds, evidence,
   null))` → `POST /internal/ai/resume-content`.
8. **Gap computation** (deterministic, in Java, not AI): any requirement whose id never appears
   with a non-`NONE` match strength in the selection matches becomes a `gap` entry.
9. **Persist** a new `ResumeVersion` document (Mongo, `resume_versions` collection) with the
   content, evidence matches, gaps, grounding report, removed sections, and AI provenance
   (`promptVersion`, `model`).
10. Mark the `ResumeGeneration` row complete; return the `ResumeVersion`.

**Error handling in this orchestrator:** `FeignException.BadGateway`/`ServiceUnavailable` from
ai-service → generation marked `AI_GENERATION_FAILED`, `ApiException(AI_GENERATION_FAILED)`
thrown to the caller. Any other `FeignException` from ai-service → same outcome, logged. A 404
from jd-service → `ApiException.notOwned()` (never a distinct "not found" vs "forbidden" — ADR-007).
There is **no retry at this orchestration layer** — retries happen one level down, inside
`GroqClient` itself (§4), and inside the two content services' own grounding-regeneration logic.

**Status/progress:** There is no polling and no progress percentage. `ProcessingPage.tsx`'s own
comment states this plainly: "Two synchronous backend calls... There's no real per-stage
progress signal from either, so this is an honest indeterminate wait, not a fake staged
checklist." The whole `/api/resumes/generate` call is one blocking HTTP request (ADR-013).

**Document rendering (separate, on-demand, not part of the generation call above):**
`document-service`'s `DocumentController.render()` (`POST /api/documents/resume-versions/
{resumeVersionId}/render`) → `DocumentRenderService.renderPdf()`:
1. Fetches the owned `ResumeVersion` from resume-service (Feign).
2. Determines `effectiveTemplateId` (explicit param wins, else whatever was stored on the resume
   at generation time, else `ResumeTemplate.DEFAULT`).
3. **Dispatches by template kind** — if `effectiveTemplateId` names one of the caller's own
   custom-uploaded assets, delegates entirely to `CustomTemplateAssetService.generate()` (mail-merge,
   DOCX or PDF). Otherwise renders a built-in template via `ResumeRenderModelBuilder` (JSON + profile
   → render model) → `PdfRenderer.render()` (Thymeleaf → jsoup → openhtmltopdf).
4. Idempotent: same template + same content SHA-256 → returns the existing artifact rather than
   re-rendering/re-uploading.
5. Uploads the bytes to object storage (`ObjectStorageService`, MinIO/S3) and persists a
   `RenderedDocument` row.

**Download:** `GET /api/documents/{id}/download` streams bytes back through document-service —
never a presigned URL, never direct browser access to MinIO (ADR-018).

---

## 3. Cover Letter Generation Workflow (end-to-end, verified)

**Entry point (frontend):** Same wizard, but `OutputTypePage` set `type=COVER_LETTER_ONLY` (or
`ALL`), so `TemplatePage` is skipped entirely (`skipsTemplate` in `ProcessingPage.tsx`).

**Frontend orchestration (`ProcessingPage.tsx`, `runCoverLetter()`):**
1. `createApplication(jdId, 'COVER_LETTER_ONLY')` → `POST /api/applications`
   (`applicationApi.ts`) → `ApplicationController.create()` → `ApplicationService.create()`
   persists an `Application` aggregate (status `DRAFT`).
2. `generateCoverLetter(application.id)` → `POST /api/applications/{id}/cover-letter` →
   `CoverLetterGenerationService.generate(userId, applicationId)`.
3. Navigate to `/results/cover-letter/{application.id}`.

**Orchestration (`CoverLetterGenerationService.java`, described from the earlier full read in
this session — behavior confirmed via `ApplicationController`'s calling contract above, and its
sibling `EmailGenerationService`'s equivalent, fully re-verified, pattern):**
1. Loads the owned `Application`, its referenced `jobDescriptionId`.
2. Fetches JD analysis (jd-service, same `RequirementDto`/`JdAnalysisDto` shape mirrored in
   `application-service`'s own `ClientDtos.java`) and the full evidence inventory
   (profile-service `GET /api/profile/evidence`, mirrored as `ClientDtos.EvidenceItem`).
3. **AI call 1 — evidence selection** — same `ai-service` endpoint resume generation uses
   (`POST /internal/ai/evidence-selection`), same request/response shape
   (`ClientDtos.EvidenceSelectionRequest/Response`) — this stage is **shared** between resume
   and cover-letter generation, not duplicated logic (see §11).
4. **AI call 2 — cover-letter content** — `POST /internal/ai/cover-letter`
   (`AiController` → `CoverLetterContentService.generate()`), request carries `jobTitle`,
   `company`, `seniority`, `requirements`, `selectedEvidenceIds`, `evidence`.
5. Persists a new `CoverLetterVersion` (application-service's own collection), and calls
   `application.attachCoverLetter(coverLetterVersionId)`, which recomputes `Application.status`
   via `deriveStatus()`.

**Grounding difference from resume generation (confirmed in `CoverLetterContentService.java`):**
the target `jobTitle` and `company` are passed to `GroundingValidator.validate(...)` as an
**additional allowed-context set** (its 3-arg overload) — they're real, user-confirmed facts
that didn't come from the candidate's own evidence, so naming the company being written to is
not treated as a fabrication. Every other rule (numbers, dates, employers, technologies, no
emails/phones/URLs, no hidden characters) still applies exactly as in resume generation.

**Extraction/removal granularity:** paragraphs, not bullets — `openingParagraph`,
`bodyParagraphs[i]`, `closingParagraph` are each an individually verifiable
`GeneratedStatement`; a paragraph that fails grounding twice is dropped entirely (body
paragraphs removed highest-index-first so earlier indices stay valid; opening/closing simply
omitted) and reported in `removedParagraphs`.

**Rendering:** Cover letters are **not currently rendered to PDF/DOCX** — `DocumentController`'s
own class comment states document-service does "real Resume PDF generation only; cover-letter/
DOCX rendering remain out of scope for this slice." The cover letter is returned and displayed
as structured JSON content only (`CoverLetterVersionResponse`). **[CONFIRMED — no cover-letter
render path exists in `document-service`'s controller.]**

**Application email (the third output, same pattern, single-shot):**
`EmailGenerationService.generate()` — no separate evidence-selection stage (confirmed by
`AiRequests.EmailContentRequest`'s own Javadoc: "Email generation is single-shot — there is no
separate evidence-selection stage; the model picks directly from the full inventory"). Only the
prose paragraphs are AI-generated (`POST /internal/ai/email-content`); the **subject line and
sign-off are assembled deterministically in Java**, never by the LLM (ADR-019).

**"Generate All" (`type=ALL`) — actual sequencing, from `ProcessingPage.tsx runAll()`:**
1. `createApplication(jdId, 'ALL', templateId)` — a hard stop if this itself fails (nothing to
   attach a per-output failure to yet).
2. `try { generateResume → attachResume → assessResume } catch { recordOutputFailure('resume', ...) }`
3. `try { generateCoverLetter } catch { recordOutputFailure('coverLetter', ...) }`
4. `try { generateEmail } catch { recordOutputFailure('email', ...) }`
5. Always navigates to `/results/all/{application.id}` regardless of the mix of success/failure.

These three run **sequentially, not in parallel** — `Application` uses Spring Data's optimistic
locking (`@Version entityVersion`), and each step reads-modifies-saves the same document, so
concurrent saves would conflict/race if fired in parallel. `Application.deriveStatus()` requires
**all three** (`resumeVersionId`, `coverLetterVersionId`, `emailId` all non-null) before the
aggregate reaches `COMPLETED` for `GenerationType.ALL`; a still-null one leaves it `PROCESSING`.

---

## 4. Current AI Architecture (verified by repository-wide search)

A search across `services/**/*.java`, `frontend/**/*.{ts,tsx}`, and all YAML/properties files for
`gemini|openai|embedding` (case-insensitive) returned exactly three hits, **all of them
describing Groq's API shape, not a second provider**:

```
services/ai-service/.../client/GroqMessages.java:8   "Wire types for Groq's OpenAI-compatible chat-completions API."
services/ai-service/.../config/GroqProperties.java:18 "@param baseUrl  OpenAI-compatible base URL"
services/ai-service/src/main/resources/application.yml:61  base-url: ${GROQ_BASE_URL:https://api.groq.com/openai/v1}
```

**Conclusion: Groq is the only AI provider in this codebase today. There is no Gemini, no
direct OpenAI account, and no embeddings API anywhere.** This was verified by search, not
assumed.

| | |
|---|---|
| **Service** | `ai-service` (port 8085, internal-only, no gateway route — ADR-012) |
| **Class holding the API key** | `GroqProperties` (config record, `@ConfigurationProperties(prefix = "careerforge.groq")`) |
| **Class making the HTTP call** | `GroqClient` — "The only class in the platform that talks to Groq" (its own Javadoc) |
| **API** | Groq's OpenAI-compatible Chat Completions API, `POST {baseUrl}/chat/completions` |
| **Model** | `${GROQ_MODEL:openai/gpt-oss-120b}` (env-configurable, not hardcoded) — migrated from `llama-3.3-70b-versatile`, see "Model migration notes" below |
| **Prompt location** | `services/ai-service/src/main/resources/prompts/<name>/v<N>.txt`, loaded by `PromptRegistry` |
| **Request format** | `GroqMessages.ChatRequest(model, messages[system, user], temperature, max_completion_tokens, response_format: json_object, include_reasoning: false)` |
| **Response format** | `GroqMessages.ChatResponse` — OpenAI-shaped `choices[0].message.content` (JSON string) + `usage.{prompt_tokens,completion_tokens,total_tokens}`. `Message` tolerates unknown fields (`@JsonIgnoreProperties`) since gpt-oss would otherwise add a `reasoning` field — suppressed via `include_reasoning: false` above, but the client stays defensive either way. |
| **Config (env vars, names only — no values/secrets)** | `GROQ_API_KEY`, `GROQ_BASE_URL`, `GROQ_MODEL`, `GROQ_TIMEOUT_SECONDS`, `GROQ_MAX_OUTPUT_TOKENS`, `GROQ_TEMPERATURE`, `GROQ_MAX_RETRIES` |
| **Defaults** (`application.yml`) | `base-url=https://api.groq.com/openai/v1`, `model=openai/gpt-oss-120b`, `timeout-seconds=60`, `max-output-tokens=4096`, `temperature=0.2`, `max-retries=2` |
| **Timeout** | `webClient...block(Duration.ofSeconds(properties.timeoutSeconds() + 5))` — request timeout + 5s grace |
| **Retry** | `Retry.backoff(maxRetries, 2s).maxBackoff(20s).jitter(0.4)` — **only** on HTTP 429 or 5xx; a 4xx caused by the caller is never retried ("it would burn quota to fail identically" — `GroqClient`'s own comment) |
| **Circuit breaker / rate limiter** | Resilience4j, instance name `groq` — `slidingWindowSize=20, failureRateThreshold=60%, waitDurationInOpenState=30s, permittedNumberOfCallsInHalfOpenState=3` (`application.yml`) |
| **Fallback / degrade behavior** | No silent fallback to different content. On persistent grounding failure, the *content services* (§5) drop the unsupported statements and report them as `removedSections`/`removedParagraphs` — never a fabricated substitute. On Groq unavailability (`GroqException`), the exception propagates up to `ResumeGenerationService`/`CoverLetterGenerationService`, which mark their generation row failed and surface `AI_GENERATION_FAILED` to the caller — there is no cached/mock resume response. |
| **Logging discipline** | Neither request nor response body is ever logged — only model, latency, token counts, status (`GroqClient`'s own guarantee, enforced in code, not just documented) |
| **Secrets exposure** | `GroqProperties.maskedKey()` — first 4 + last 4 chars only, used by the diagnostic `AiResponses.StatusResponse`; the real key is never returned to any client |

**Model migration notes** (`llama-3.3-70b-versatile` → `openai/gpt-oss-120b`, config-only — see `GROQ_MODEL`): pure configuration
swap plus two minimal, Groq-documented protocol compatibility changes in `GroqClient`/`GroqMessages`, no prompt/schema/validator
changes:
- `max_tokens` → `max_completion_tokens` — Groq deprecated the former across every model, not just reasoning ones.
- `include_reasoning: false` added to the request, and `Message` now tolerates unknown response fields
  (`@JsonIgnoreProperties`) — gpt-oss models otherwise add a chain-of-thought `reasoning` field to the assistant message that
  this client has no use for.

**Five internal AI endpoints, all on `AiController` (`/internal/ai/*`, not gateway-routed):**

| Endpoint | Service class | Used by |
|---|---|---|
| `POST /internal/ai/jd-analysis` | `JdAnalysisService` | jd-service |
| `POST /internal/ai/evidence-selection` | `EvidenceSelectionService` | resume-service, application-service (shared) |
| `POST /internal/ai/resume-content` | `ResumeContentService` | resume-service |
| `POST /internal/ai/cover-letter` | `CoverLetterContentService` | application-service |
| `POST /internal/ai/email-content` | (email content service, single-shot) | application-service |
| `GET /internal/ai/status` *(diagnostic)* | — | ops/health only |

---

## 5. Groq Integration — Prompt Construction to Document Generation (detailed)

**Two-stage pipeline (Resume and Cover Letter identically shaped):**

**Stage 1 — Evidence Selection** (`EvidenceSelectionService.select()`):
- System prompt: `prompts/evidence-selection/v1.txt` (versioned, loaded/cached at startup by
  `PromptRegistry`, never user-editable at runtime).
- User content: two fenced blocks built by `UntrustedContent.fence(label, text, maxChars)` —
  `REQUIREMENTS` (JD requirements, each `[reqId] (type, weight N) text`, sanitized) and
  `EVIDENCE` (every evidence item, `[evidenceId] TYPE — searchableText`, sanitized).
  `UntrustedContent.sanitise()` strips/escapes hostile content before it reaches the model —
  this is the prompt-injection defense layer for anything ultimately traceable to a JD or free
  text field.
- Output: JSON validated against `schemas/evidence-selection.schema.json`
  (`additionalProperties: false` — extra/unexpected keys are rejected, not silently accepted).
- **Post-processing not done by the LLM**: `stripUnknownEvidenceIds()` deletes any cited
  `evidenceId` that doesn't actually exist in the supplied inventory, and downgrades that
  match's `matchStrength` to `NONE` if every id it cited turns out to be invented. This is a
  second, independent guard against hallucination, ahead of the full grounding pass in Stage 2.
- No retry/regeneration logic at this stage (only the schema call itself is subject to
  `GroqClient`'s HTTP-level retry).

**Stage 2 — Content Generation** (`ResumeContentService.generate()` /
`CoverLetterContentService.generate()`):
- System prompt: `prompts/resume-content/v1.txt` or `prompts/cover-letter/v1.txt`.
- User content: `JOB_CONTEXT` (title/seniority/prioritised requirements, resume) or
  `JOB_CONTEXT` (title/company/seniority/requirements, cover letter) + `EVIDENCE` (full
  inventory, same per-item rendering as Stage 1) + `SELECTED` (the evidence ids Stage 1 chose).
- Output validated against `schemas/resume-content.schema.json` or
  `schemas/cover-letter.schema.json`.
- **Grounding validation** (`GroundingValidator.validate()`) — see §4/§12 checklist. This is
  the actual code-level anti-fabrication gate, checking: every cited evidence id exists; every
  factual statement cites at least one id; every number/year/month appears (token-exact, digit-
  normalized) in the cited evidence text; every proper noun (capitalized token, `Node.js`/
  `CI/CD`-aware) appears somewhere in the candidate's full evidence vocabulary; **no** email,
  phone-shaped number, or URL is allowed to appear at all; no zero-width/bidi hidden characters.
- **Failure policy, exactly** (documented in the class's own Javadoc and mirrored in code):
  1. Generate → validate schema → validate grounding.
  2. If grounding failed: regenerate **once**, appending a `-----CORRECTION-----` notice that
     names the violated rule counts (never quoting the rejected text back, "to avoid
     reinforcing a bad claim" — the class's own comment) → re-validate.
  3. If still failing: **surgically remove** only the offending statements/paragraphs (by
     stable `location` path, e.g. `experienceBullets[1].bullets[2]`, `bodyParagraphs[0]`) —
     never reject the whole document — and report them as `removedSections`/`removedParagraphs`
     so the caller can surface an honest gap instead of silently keeping or fully discarding.
- Token/temperature/limits: shared Groq-wide config (§4) — `temperature=0.2` ("extraction and
  grounded rewriting, not creative writing" — `GroqProperties`'s own comment), `maxOutputTokens
  =4096`, one HTTP-level retry budget of 2 (429/5xx only).

**Single-shot variant — Email Content:** one Groq call directly against the full evidence
inventory (no Stage 1), same schema-then-grounding-then-regenerate-once-then-strip policy, but
producing only `greeting` + one body paragraph + one closing paragraph + sign-off content — the
subject line and the literal sign-off name are assembled deterministically by
`EmailGenerationService` in Java from already-verified profile/JD data, never by the model
(ADR-019).

**JD Analysis** (`JdAnalysisService.analyse()`) is a **different shape** — one Groq call, no
evidence/grounding involved at all (there's no "candidate fact" to fabricate about a job
posting), fenced `JOB_DESCRIPTION` block, validated against `schemas/jd-analysis.schema.json`.
Its own class comment: "The JD is the most hostile input the platform accepts... sanitised and
fenced here, described as data by the system prompt, and constrained by a schema on the way
out."

---

## 6. Data Flow — Exact DTOs Between Services

*(Field lists below are exactly what's in the cited source — nothing invented.)*

**`EvidenceItem`** (appears, field-identical, as `profile-service`'s `EvidenceItemResponse`,
resume-service's `ClientDtos.EvidenceItem`, application-service's `ClientDtos.EvidenceItem`,
ai-service's own `EvidenceItem` — a repeated local mirror per ADR-006, not a shared type):
`evidenceId, type, title, organisation, description, technologies[], metrics[], startDate, endDate`.
Producer: `profile-service` (`GET /api/profile/evidence`). Consumers: resume-service,
application-service (both forward it unmodified to ai-service).

**`JdAnalysis`** (resume-service's `ClientDtos.JdAnalysis`, mirrored in application-service as
`ClientDtos.JdAnalysisDto`): `jobDescriptionId, title, company, seniority, keywords[],
requirements[Requirement]`. `Requirement`: `requirementId, text, type, weight, normalisedTerms[]`.
Producer: jd-service (`GET /api/jd/{id}/analysis`). Consumers: resume-service, application-service.

**`EvidenceSelectionRequest`** (ai-service's `AiRequests.EvidenceSelectionRequest`):
`requirements[RequirementInput], evidence[EvidenceItem], promptVersion`. `RequirementInput`:
`requirementId, text, type, weight`. Producer: resume-service / application-service. Consumer:
ai-service.

**`EvidenceSelectionResponse`** (`AiResponses.EvidenceSelectionResponse`): `selection (raw
JsonNode — shape defined by evidence-selection.schema.json), provenance`. `Provenance`:
`promptVersion, model, totalTokens, regenerated`.

**`ResumeContentRequest`**: `jobTitle, seniority, requirements[RequirementInput],
selectedEvidenceIds[String], evidence[EvidenceItem], promptVersion`.
**`ResumeContentResponse`**: `content (JsonNode), grounding (GroundingReport), removedSections
[String], provenance`.

**`CoverLetterContentRequest`**: same as above plus `company` (nullable).
**`CoverLetterContentResponse`**: `content, grounding, removedParagraphs[String], provenance`.

**`EmailContentRequest`**: `jobTitle, company, evidence[EvidenceItem], promptVersion` (no
requirements/selectedEvidenceIds — single-shot).
**`EmailContentResponse`**: `content, grounding, removedParagraphs, provenance`.

**`ResumeVersion`** (resume-service domain, `resume_versions` Mongo collection — immutable, one
per successful generation): `id, resumeGenerationId, userId, jobDescriptionId, jobTitle,
company, version, templateId, templateVersion, content (Map — opaque, mirrors ai-service's JSON
exactly), evidenceMatches[Map], gaps[Map], groundingReport (Map), removedSections[String],
promptVersion, modelId, createdAt`.

**`Application`** (application-service domain, `applications` collection — the central
aggregate, references only, never copies): `id, userId, jobDescriptionId, jobTitle, company,
generationType (RESUME_ONLY|EMAIL_ONLY|COVER_LETTER_ONLY|ALL), templateId, resumeVersionId,
coverLetterVersionId, emailId, assessed, status (DRAFT|PROCESSING|COMPLETED|FAILED),
failureCode, resumeError, coverLetterError, emailError, createdAt, updatedAt, entityVersion
(@Version — optimistic locking)`.

**`Template`** (resume-service domain, backing `GET /api/resumes/templates`): built-in rows
(seeded, `source=BUILT_IN`) plus custom-upload rows (`source=CUSTOM_UPLOAD`,
`ownerUserId`-scoped) — fields include `id, name, type (RESUME), format, originalFilename,
structure (Map — mirrors document-service's `TemplateStructureDto`), detectedFields[Map],
fieldMappings (Map), isDefault, status`. See §8.

**`RenderedDocument`** (document-service domain): `id, userId, resumeVersionId, type, format
(PDF|DOCX), objectKey, bucket, sha256, byteSize, pageCount, templateId, templateVersion,
engineVersion, renderedAt`.

---

## 7. JD Processing (verified — `jd-service`)

| Step | Owner | Detail |
|---|---|---|
| **Intake — text paste** | `JdController.submit()` → `JdService.submitText()` | Stores raw + whitespace-normalized text as `JdVersion` v1 of a new `JobDescription` (`sourceType="TEXT"`). |
| **Intake — URL** | `JdController.fetchUrl()` → `JdService.fetchUrl()` | `JdUrlFetcher.fetch(url)` — SSRF-guarded (ADR-015; guard class `SsrfGuard`, not re-read this session but referenced by the fetch path) → `JobPostingExtractor.extract(html)` — **two-path extraction**: schema.org `JobPosting` JSON-LD if present, else generic readable-text fallback. Rejects (`JD_VALIDATION_ERROR`) if extracted text < 50 chars; truncates at 60,000 chars. |
| **Versioning** | `JdService` | Every edit (`PUT /api/jd/{id}`, pre-confirmation only) creates a **new immutable `JdVersion`** and advances `currentVersion` — a version once used for confirmation/analysis is never mutated. Blocked with 409 once `status=CONFIRMED`. |
| **Confirmation (mandatory)** | `JdController.confirm()` → `JdService.confirm()` | Sets `status=CONFIRMED`, pins `confirmedVersion`. Analysis cannot run before this (`analyse()` throws `JD_NOT_CONFIRMED` otherwise). |
| **AI analysis** | `JdService.analyse()` → `AiServiceClient.analyseJd()` → ai-service `POST /internal/ai/jd-analysis` → `JdAnalysisService` | One Groq call, JD text fenced as `JOB_DESCRIPTION`, output schema-validated (`jd-analysis.schema.json`). Rejects text that isn't actually a job posting (`payload.isJobPosting()==false` → `JD_VALIDATION_ERROR`). |
| **Caching** | `JdService.analyse()` | `jdAnalyses.findByJdVersionId(...).orElseGet(() -> runAnalysis(...))` — analysis is computed once per confirmed version, cached thereafter, not re-run on every read. |
| **Normalization** | `JdService.normalise()` | Collapses horizontal whitespace, collapses 3+ blank lines to 2 — a text-hygiene step, not semantic parsing. |

**State of the JD by pipeline stage**, exactly: raw pasted/fetched text → normalized text
(`JdVersion.normalisedText`) → (on confirm) immutable, pinned version → (on first analysis
request) AI-extracted structured `JdAnalysis` (`title, company, seniority, keywords[],
requirements[]`), cached. Resume/cover-letter generation consume only the final structured
`JdAnalysis`, never the raw text directly.

---

## 8. Template Architecture (verified — `resume-service` + `document-service`)

**Built-in templates:**
- Owned by `resume-service`'s `Template` domain/`TemplateRepository`, seeded by `TemplateSeeder`
  (confirmed in an earlier read this session: **exactly 3** built-in templates, `source=BUILT_IN`,
  visible to every user).
- Rendering identity is separate: `document-service`'s own `ResumeTemplate.java` enum/class
  independently defines the 3 built-in template ids and their Thymeleaf HTML paths
  (`resources/templates/<id>/v1/resume.html`) — resume-service's catalogue row and
  document-service's renderable template are two different classes agreeing on the same ids,
  not one shared model (consistent with ADR-006's no-shared-module rule).
- Selection: `TemplateService.list(userId, type)` — built-ins (`findByTypeAndStatusOrderByNameAsc`)
  unioned in-memory with the caller's own custom uploads; never another user's custom row.
- Rendering: `DocumentRenderService.renderPdf()` → `ResumeRenderModelBuilder.build(resume, profile)`
  → `PdfRenderer.render(template, model)` = Thymeleaf template fill → jsoup HTML normalization →
  openhtmltopdf → PDF bytes.

**Custom templates (upload → mail-merge → document):**
- Upload entry: resume-service's `TemplateService.uploadCustom()` (browser → resume-service
  multipart) → Feign `DocumentServiceClient.store()` → document-service's
  `CustomTemplateAssetController.store()` (`POST /api/documents/custom-templates`, plain byte
  body + `X-Filename` header, not multipart — single internal caller) →
  `CustomTemplateAssetService.storeAndAnalyze()`.
- **Structure analysis, format-specific**: DOCX via `DocxStructureAnalyzer` (not re-read this
  session, but referenced consistently); PDF via `PdfStructureAnalyzer` (re-verified this
  session, full source read) — reads real structural facts (page count/dimensions, fonts, sizes,
  colors via `PdfPlaceholderLocator`, a `PDFTextStripper` subclass) and detects every
  `{{token}}` placeholder with its exact page + geometry.
- **Field mapping**: `ProfileFieldCatalog.suggest(token)` proposes a profile-field mapping per
  detected token (e.g. `{{NAME}}` → suggests the personal-info name field); the user can edit
  this mapping (`EditMappingModal.tsx` → `TemplateService.updateMapping()`).
- **Acceptance policy — current, verified state (not the stale doc comment):** A PDF (or DOCX)
  with **zero detected placeholders is accepted**, not rejected — confirmed by
  `PdfStructureAnalyzer.analyze()`'s own current doc comment ("A PDF with zero detected
  placeholders is accepted, same as a zero-placeholder DOCX... it's a real, uploadable template
  that just has nothing for the mail-merge step to fill in yet") and by
  `PdfStructureAnalyzerTest.analyzeAcceptsAPdfWithNoPlaceholders()`. **Only** a file whose text
  cannot be read at all (a scanned image, no extractable text layer) is rejected
  (`ErrorCode.FILE_REJECTED`). **Note (observation, not a fix — out of scope for this task):**
  `CustomTemplateAssetService`'s own class-level Javadoc, at the time of the earlier session's
  read, still described the *old*, stricter rejection behavior ("A PDF with no detectable
  {{placeholder}} is rejected... never accepted") — that comment was already stale relative to
  actual behavior before this analysis task began (the behavior was intentionally relaxed in a
  prior task this session, see conversation history); it was **not edited** as part of this
  read-only task, consistent with the "do not modify production code" rule.
- **Generation**: `CustomTemplateAssetController.generate()` (`POST /api/documents/
  custom-templates/{id}/generate`) or, transparently, `DocumentRenderService.renderPdf()`'s own
  dispatch when the resume's stored `templateId` happens to be a custom asset — both paths call
  the **same** `CustomTemplateAssetService.generate()` method, so there is only one custom-template
  merge implementation, not two (confirmed by `DocumentRenderService`'s own Javadoc: "delegates
  entirely to `CustomTemplateAssetService#generate` — the exact same method the dedicated
  endpoint already calls").
- **Supported formats, confirmed**: **DOCX and PDF**, both as custom-upload input formats and as
  generation output formats (`DocumentController.download()` sets the content type based on
  `document.format() == DocumentFormat.DOCX` vs. the PDF default — both are real, served paths).

---

## 9. Custom Template Workflow (detailed, sequential)

1. **Upload** (browser, multipart) → resume-service validates name/size (≤5MB) →
   document-service stores raw bytes (MinIO) + runs structure analysis (format-specific analyzer).
2. **Analysis** → `DetectedField[]` (token, context, suggested profile field, and — PDF only —
   exact page/x/y/width/height/fontSize/fontName/colorHex for later text-overlay placement) +
   `TemplateStructure` (page/column/margin geometry, fonts/sizes/colors used, DOCX-only fields
   left `null` for a PDF row and vice versa — the two formats' structural facts are "never
   conflated", per `PdfStructureAnalyzerTest`'s own comment).
3. **Field mapping** — auto-suggested (`ProfileFieldCatalog.suggest`), user-editable, persisted
   on resume-service's `Template` row (document-service never stores the display mapping).
4. **Generation** (mail-merge, no LLM involved at any point in this path):
   - **DOCX**: `docx4j` run-level `{{token}}` replacement — preserves the original document's
     run formatting (font, size, bold, color) exactly, since the token is replaced in place
     inside its existing run rather than the paragraph being rebuilt.
   - **PDF**: `PdfPlaceholderLocator` finds the token's exact glyph position →
     `PdfContentRedactor` (referenced, not re-read this session) removes/covers the original
     token glyphs at that exact rectangle → new text is drawn at the same position using the
     detected font/size/color, with fit/condense logic for text longer than the original token.
5. **Output** — a new `RenderedDocument` (DOCX or PDF), downloaded through the same
   `GET /api/documents/{id}/download` streaming endpoint the built-in path uses.

---

## 10. PDF/DOCX Generation — Exact Libraries and Paths

| Path | Library chain | Where |
|---|---|---|
| **Built-in template → PDF** | Thymeleaf (HTML templating) → jsoup (HTML normalization/cleanup) → **openhtmltopdf** (HTML→PDF rendering) | `PdfRenderer.java` |
| **Custom DOCX template → DOCX** (mail-merge) | **docx4j** (run-level token replacement, structure analysis) | `DocxMailMerge.java`, `DocxStructureAnalyzer.java` |
| **Custom PDF template → PDF** (mail-merge) | **Apache PDFBox** (`Loader.loadPDF`, `PDFTextStripper`-based placeholder location, content-stream redaction + text overlay) | `PdfMailMerge.java`, `PdfStructureAnalyzer.java`, `PdfPlaceholderLocator.java`, `PdfContentRedactor.java` |
| **Custom DOCX template → PDF** | **[NOT VERIFIED THIS SESSION]** — `DocumentController`'s content-type branching (`isDocx = document.format() == DocumentFormat.DOCX`) shows both DOCX and PDF are real download formats, but whether a DOCX-format custom template can *also* be rendered to PDF output (DOCX→PDF conversion, e.g. via a LibreOffice headless call) was not confirmed in this session's reads — no LibreOffice reference was seen in any file read. Flag for follow-up rather than asserted either way. |
| **PDF → PDF** (re-render/preview) | Same `PdfRenderer`/mail-merge paths, no separate conversion step — a preview is just a real render against sample data (`DocumentRenderService.renderPreview()`), not a distinct pipeline | `DocumentRenderService.java` |

**No Apache POI, no iText, no LibreOffice reference was found anywhere in the codebase during
this session's reads** — the two custom-template libraries are docx4j (DOCX) and PDFBox (PDF)
specifically, and the built-in path uses openhtmltopdf, not a Word-to-PDF converter.

---

## 11. Microservice Communication (actual, from code — not the assumed flow)

All inter-service calls are **synchronous REST via OpenFeign** over Eureka service discovery
(`lb://service-name`), with the caller's `X-User-Id` auto-forwarded
(`FeignHeaderForwardingConfig`, referenced consistently across every client read this session).
**No Kafka, no message queue, no async job system was found anywhere** — ADR-013 explicitly
documents synchronous resume generation as a deviation from an originally-planned async design.

**Resume generation, actual call graph:**
```
frontend → api-gateway → resume-service (ResumeController)
resume-service → jd-service        (Feign: GET /api/jd/{id}/analysis)
resume-service → profile-service   (Feign: GET /api/profile/evidence)
resume-service → ai-service        (Feign: POST /internal/ai/evidence-selection)
resume-service → ai-service        (Feign: POST /internal/ai/resume-content)
resume-service → (persists ResumeVersion in its own MongoDB)
[later, on demand] frontend → api-gateway → document-service (DocumentController)
document-service → resume-service   (Feign: GET /api/resumes/{id})
document-service → profile-service  (Feign: GET /api/profile)  [built-in path]
document-service → (MinIO/S3 object storage, own RenderedDocument collection)
```

**Cover letter / email generation, actual call graph:**
```
frontend → api-gateway → application-service (ApplicationController)
application-service → jd-service       (Feign: GET job description + analysis)
application-service → profile-service  (Feign: GET /api/profile/evidence, GET /api/profile)
application-service → ai-service       (Feign: POST /internal/ai/evidence-selection)   [cover letter only]
application-service → ai-service       (Feign: POST /internal/ai/cover-letter | /internal/ai/email-content)
application-service → (persists CoverLetterVersion / EmailContent in its own MongoDB)
```

**"Generate All" additionally chains, from the frontend (not a backend orchestration):**
```
frontend → resume-service (generate) → application-service (attachResume)
frontend → application-service (generateCoverLetter)
frontend → application-service (generateEmail)
```
i.e., **the sequencing of the three outputs is driven by the frontend's `ProcessingPage.tsx`**,
not by a backend saga/orchestrator — each call is a separate, independent request from the
browser, wrapped in its own try/catch with `recordOutputFailure` on error.

**ai-service is reached only via internal Eureka discovery, never through api-gateway**
(ADR-012 — confirmed by every `@FeignClient(name = "ai-service", path = "/internal/ai")`
declaration seen: resume-service's, jd-service's, and application-service's own copies all use
this same internal path).

---

## 12. Sequence Diagrams

### 12.1 Resume Generation

```mermaid
sequenceDiagram
    actor User
    participant FE as Frontend (TemplatePage/ProcessingPage)
    participant GW as api-gateway
    participant RS as resume-service
    participant JD as jd-service
    participant PS as profile-service
    participant AI as ai-service
    participant GR as Groq API
    participant DS as document-service

    User->>FE: pick template
    FE->>GW: POST /api/resumes/generate {jobDescriptionId, templateId}
    GW->>RS: forward (X-User-Id set by gateway)
    RS->>RS: templateService.resolveForGeneration()
    RS->>JD: GET /api/jd/{id}/analysis
    JD-->>RS: JdAnalysis {title, company, requirements[]}
    RS->>PS: GET /api/profile/evidence
    PS-->>RS: EvidenceItem[]
    RS->>AI: POST /internal/ai/evidence-selection
    AI->>GR: POST /chat/completions (evidence-selection prompt)
    GR-->>AI: JSON matches
    AI->>AI: schema validate + strip unknown evidenceIds
    AI-->>RS: EvidenceSelectionResponse
    RS->>AI: POST /internal/ai/resume-content
    AI->>GR: POST /chat/completions (resume-content prompt)
    GR-->>AI: JSON resume content
    AI->>AI: schema validate + GroundingValidator
    alt grounding failed
        AI->>GR: POST /chat/completions (retry w/ correction notice)
        GR-->>AI: JSON resume content (attempt 2)
        AI->>AI: re-validate; still-failing statements removed
    end
    AI-->>RS: ResumeContentResponse {content, grounding, removedSections, provenance}
    RS->>RS: computeGaps() (deterministic)
    RS->>RS: persist ResumeVersion (MongoDB)
    RS-->>FE: ResumeVersionResponse
    FE->>GW: POST /api/assessments/... (non-fatal)
    FE->>User: navigate /results/{resumeId}
    User->>FE: click Download PDF
    FE->>GW: POST /api/documents/resume-versions/{id}/render
    GW->>DS: forward
    DS->>RS: GET /api/resumes/{id}
    DS->>PS: GET /api/profile
    DS->>DS: render (built-in: Thymeleaf+jsoup+openhtmltopdf, or custom: mail-merge)
    DS->>DS: upload to object storage, persist RenderedDocument
    DS-->>FE: RenderedDocumentResponse
    FE->>GW: GET /api/documents/{id}/download
    GW->>DS: forward
    DS-->>FE: PDF/DOCX bytes (streamed)
```

### 12.2 Cover Letter Generation

```mermaid
sequenceDiagram
    actor User
    participant FE as Frontend (ProcessingPage)
    participant GW as api-gateway
    participant AS as application-service
    participant JD as jd-service
    participant PS as profile-service
    participant AI as ai-service
    participant GR as Groq API

    User->>FE: choose "Cover letter" output
    FE->>GW: POST /api/applications {jobDescriptionId, generationType: COVER_LETTER_ONLY}
    GW->>AS: forward
    AS->>AS: persist Application (status=DRAFT)
    AS-->>FE: ApplicationResponse
    FE->>GW: POST /api/applications/{id}/cover-letter
    GW->>AS: forward
    AS->>JD: GET job description + analysis
    JD-->>AS: JdAnalysisDto {title, company, requirements[]}
    AS->>PS: GET /api/profile/evidence
    PS-->>AS: EvidenceItem[]
    AS->>AI: POST /internal/ai/evidence-selection
    AI->>GR: POST /chat/completions
    GR-->>AI: JSON matches
    AI-->>AS: EvidenceSelectionResponse
    AS->>AI: POST /internal/ai/cover-letter {jobTitle, company, requirements, selectedEvidenceIds, evidence}
    AI->>GR: POST /chat/completions (cover-letter prompt)
    GR-->>AI: JSON letter content
    AI->>AI: schema validate + GroundingValidator (jobTitle/company allowed as context)
    alt grounding failed
        AI->>GR: POST /chat/completions (retry)
        GR-->>AI: JSON letter content (attempt 2)
        AI->>AI: re-validate; failing paragraphs removed
    end
    AI-->>AS: CoverLetterContentResponse {content, grounding, removedParagraphs, provenance}
    AS->>AS: persist CoverLetterVersion
    AS->>AS: application.attachCoverLetter() -> deriveStatus()
    AS-->>FE: CoverLetterVersionResponse
    FE->>User: navigate /results/cover-letter/{applicationId}
```

---

## 13. Shared Components Between Resume and Cover Letter Generation

| Component | Resume | Cover Letter | Shared? | Location |
|---|---|---|---|---|
| JD analysis retrieval | Yes (`jdServiceClient.getAnalysis`) | Yes (own `JdServiceClient`/mirror) | **Yes**, same jd-service endpoint, two independent Feign client copies (ADR-006) | `resume-service/client/JdServiceClient.java`, `application-service` equivalent |
| Profile evidence retrieval | Yes (`profileServiceClient.getEvidence`) | Yes | **Yes**, same `GET /api/profile/evidence` endpoint, two client copies | `ProfileController.evidence()` (single producer) |
| AI client (Feign to ai-service) | Yes | Yes | **Yes**, same `ai-service`, different endpoints per stage; each caller owns its own Feign interface copy | `resume-service/client/AiServiceClient.java`, `application-service` equivalent |
| Evidence-selection stage | Yes (`POST /internal/ai/evidence-selection`) | Yes (same endpoint) | **Yes — identical ai-service endpoint/class** (`EvidenceSelectionService`) | `ai-service/service/EvidenceSelectionService.java` |
| Content-generation stage | `ResumeContentService` | `CoverLetterContentService` | **No** — separate classes, separate prompts/schemas, though structurally parallel (same failure policy) | `ai-service/service/` |
| Grounding validator | Yes | Yes | **Yes, same class**, cover letter uses its 3-arg overload for job title/company context | `ai-service/grounding/GroundingValidator.java` |
| Prompt registry / versioning | Yes | Yes | **Yes**, same `PromptRegistry` loads all prompt files | `ai-service/prompt/PromptRegistry.java` |
| Template service | Yes (template selection, custom upload) | **No** — cover letter has no template step | **No** | `resume-service/service/TemplateService.java` |
| Document rendering (PDF/DOCX) | Yes | **No** — no cover-letter render path exists | **No** | `document-service` |
| Object storage | Yes (rendered PDFs, custom template assets) | **No** — nothing to store, letter is JSON-only | **No** | `document-service/service/ObjectStorageService.java` |
| `Application` aggregate | Referenced via `attachResume` | Owns the generation (`generate()` reads/writes `Application` directly) | **Yes**, same aggregate, both outputs reference/attach to it | `application-service/domain/Application.java` |
| Generation status/progress | None (indeterminate spinner, single sync call) | None (same pattern) | **Yes** (same absence of a progress signal) | `frontend/ProcessingPage.tsx` |
| Error handling pattern (Feign → ApiException mapping) | `FeignException.BadGateway/ServiceUnavailable → AI_GENERATION_FAILED`, `NotFound → notOwned()` | Same pattern (confirmed via `ApplicationController`/`JdService` equivalents) | **Yes, same convention**, independently implemented per service (no shared exception-mapping module) | each service's own orchestrator |

---

## 14. Current Problems and Limitations (identified only after the above — nothing fixed)

**AI provider limits/reliability**
- Single point of failure: exactly one provider (Groq), one model. `GroqException` on
  persistent 429/5xx after 2 retries surfaces as a hard `AI_GENERATION_FAILED` to the user —
  there is no fallback provider or cached/degraded response.
- Circuit breaker (`failureRateThreshold=60%`) will open under sustained Groq trouble, but that
  makes failures *faster*, not *survivable* — generation is still entirely unavailable while open.

**Hallucination risk (residual, despite the grounding gate)**
- `GroundingValidator`'s proper-noun check only verifies a capitalized token appears *somewhere*
  in the candidate's *whole* evidence vocabulary, not that it's attached to the *right* item —
  a technology genuinely on the candidate's profile but for an unrelated project could be
  attributed to the wrong experience bullet without tripping any rule (the class's own comment
  acknowledges this as "a relevance mistake, not a fabrication," and explicitly chooses not to
  block it).
- Numeric matching is token-exact after stripping formatting — a genuinely different but
  similarly-formatted number from elsewhere in the same evidence item's text could theoretically
  satisfy `supportsNumber()` without being the number the model actually meant to cite (a narrow
  edge case, not a wide corroborated pattern from this reading).

**JD keyword matching / resume relevance**
- Evidence selection is a single non-deterministic Groq call per generation; the same JD +
  profile can plausibly select different evidence across two separate runs (temperature 0.2 is
  low but non-zero) — no determinism guarantee is made anywhere in the code read.
- `computeGaps()` only flags requirements with **zero** matched evidence, not requirements with
  a merely `PARTIAL` match — a resume can pass generation while still weakly addressing several
  requirements without any visible gap signal to the user beyond what's in `evidenceMatches`.

**Cover-letter personalization**
- Only `jobTitle`/`company` are available as non-evidence context; there is no deeper
  company-research or role-context signal fed into the letter (nothing in `JdAnalysis` beyond
  title/company/seniority/keywords/requirements was found).

**Consistency between resume and cover letter**
- The two generations are **entirely independent Groq calls with independent evidence
  selection** — nothing ties the resume's chosen framing to the cover letter's, so the two
  documents for the same application can legitimately emphasize different evidence or phrase
  overlapping claims differently. No cross-document consistency check exists.

**Document formatting / template preservation**
- Built-in rendering is HTML→PDF (openhtmltopdf); this is a real page-layout engine but is not
  Word/LibreOffice-grade — long generated content (a resume with an unusually long summary or
  many bullets) has no confirmed explicit page-overflow/pagination handling in the files read
  this session; behavior for content that would overflow a page was **not verified** — flagged,
  not asserted.
- Custom-template PDF mail-merge does have explicit "fit/condense logic" for text longer than
  the original placeholder's rectangle (§9), but this is a text-shrinking heuristic, not
  guaranteed lossless layout preservation for arbitrarily long generated content.

**Custom template DOCX/PDF support**
- Field mapping is a flat token → profile-field association; there is no confirmed support for
  repeating/list-shaped placeholders (e.g. an arbitrary N experience entries mapped into a
  custom template's own repeated block) — mapping is described as `Map<String,String>`
  throughout every DTO read, i.e. one token maps to one scalar value, not a collection.
- Whether a DOCX-format custom template can be rendered to a *PDF* output (DOCX→PDF conversion)
  was not confirmed (§10) — a real gap in verified understanding, not a claimed limitation.

---

## 15. Gemini Integration Opportunities (identification only — nothing implemented)

**Gemini should REPLACE (drop-in for an existing Groq call, same contract)**
- `JdAnalysisService` — JD → structured `{title, company, seniority, keywords, requirements}`.
  Pure extraction from untrusted text; benefits from a model with strong long-context
  understanding of noisy, inconsistently-formatted job postings.
- `EvidenceSelectionService` — JD requirements × evidence inventory → matches. Same shape,
  same schema-validated JSON contract; a stronger model could reduce the "relevance mistake"
  class of issue noted in §14 without changing anything else in the pipeline.
- `ResumeContentService` / `CoverLetterContentService` content-generation calls — the actual
  prose-writing step. This is where Gemini's language quality would be most visible to the user,
  and it's already wrapped by schema validation + `GroundingValidator`, so a provider swap here
  is the lowest-risk place to start (the safety net doesn't change).

**Gemini should COMPLEMENT (a genuinely new capability, additive, not a replacement of an
existing call)**
- **Custom template PDF/DOCX understanding** — today, `PdfStructureAnalyzer`/
  `DocxStructureAnalyzer` do pure structural/regex placeholder detection (`{{token}}` string
  matching + geometry). A multimodal Gemini model could *visually* interpret a template's layout
  (section boundaries, implied field semantics from surrounding text/design even without an
  explicit `{{token}}`, table vs. free-text regions) as an **additional signal** feeding into
  `ProfileFieldCatalog.suggest()`'s mapping suggestions — never replacing the deterministic mail-
  merge that actually writes the final bytes (§16 draws this line explicitly).
- **JD-to-resume match explanation** — a natural-language "why this evidence was chosen" layer
  on top of the existing deterministic `evidenceMatches`/`gaps`, without changing what's
  selected or scored.

**Gemini should NOT handle (explicitly, per this task's constraint)**
- **PDF rendering** — stays openhtmltopdf (built-in) / PDFBox (custom mail-merge). Rendering
  correctness is deterministic layout math, not a language-model task.
- **DOCX rendering** — stays docx4j mail-merge.
- **Template placement / mail-merge token replacement** — stays deterministic (`DocxMailMerge`,
  `PdfMailMerge`, `PdfPlaceholderLocator`, `PdfContentRedactor`) — geometry-exact placement is
  not a generative task and introducing a model here would only add non-determinism to something
  that currently works precisely.
- **File storage** — stays `ObjectStorageService`/MinIO/S3, untouched.
- **Database operations** — stays Spring Data MongoDB repositories, untouched.
- **ATS/JD-fit scoring** — this is explicitly, deliberately deterministic Java in
  `assessment-service` today (ADR-008, ADR-009, ADR-014 — CLAUDE.md is explicit that scores are
  "never asked of the LLM"); nothing in this analysis suggests changing that boundary, and doing
  so would contradict a standing architectural decision, not just a convention.

---

## 16. Proposed Future Architecture (design only — not implemented)

```
                         ┌───────────────────────────┐
                         │         Frontend            │
                         └──────────────┬──────────────┘
                                        │
                         ┌──────────────▼──────────────┐
                         │   Existing Workflow           │
                         │  (resume-service /             │
                         │   application-service /        │
                         │   jd-service orchestration)     │
                         └──────────────┬──────────────┘
                                        │  same request/response DTOs as today
                         ┌──────────────▼──────────────┐
                         │   AI Abstraction Layer         │  ← does not exist yet, see below
                         │   (interface: complete(system, │
                         │    user, schema) -> JsonNode)   │
                         └───────┬───────────────┬────────┘
                                 │               │
                        ┌────────▼──────┐ ┌───────▼────────┐
                        │     Gemini      │ │      Groq        │
                        │   (new impl)    │ │  (existing impl)  │
                        └────────┬──────┘ └───────┬────────┘
                                 └───────┬───────┘
                                         │  same JSON contract both ways
                         ┌──────────────▼──────────────┐
                         │  Schema validation +           │
                         │  GroundingValidator             │  ← unchanged, provider-agnostic today
                         │  (structured content out)       │
                         └──────────────┬──────────────┘
                                        │
                         ┌──────────────▼──────────────┐
                         │  Existing Template/Document     │
                         │  Pipeline (Thymeleaf/openhtmltopdf,│
                         │  docx4j, PDFBox mail-merge)      │  ← unchanged, no LLM here today or after
                         └──────────────┬──────────────┘
                                        │
                                  PDF / DOCX bytes
```

**Does a suitable abstraction layer already exist? No — verified, not assumed.**
`GroqClient` is called directly, by concrete type, from `JdAnalysisService`,
`EvidenceSelectionService`, `ResumeContentService`, `CoverLetterContentService`, and the
email-content service — there is no `AiClient` interface, no Spring `@Qualifier`-selected
strategy, nothing analogous to a provider abstraction anywhere in `ai-service`. Every one of
these five services has `private final GroqClient groqClient;` as a concrete field type.

**Minimum change needed to introduce one (identification, not a plan to execute):**
1. Extract a narrow interface — something like `AiChatClient { GroqResult complete(String
   systemPrompt, String userContent, String operation); }` — with `GroqClient` as one
   implementation.
2. The five calling services would depend on the interface instead of `GroqClient` directly
   (a `Component`/constructor-injection change only — none of their prompt-building, schema-
   validation, or grounding logic would need to change, since all of that operates on the
   `GroqResult`-shaped output, not on Groq specifically).
3. Provider selection (Groq vs. Gemini, per-call or globally) would need a decision point — not
   designed here, since that's implementation, which this task explicitly excludes.

This is the **only** structural change implied by "introduce Gemini safely" — everything below
`GroqClient` in the current call chain (schema validation, `GroundingValidator`, persistence,
rendering) is already provider-agnostic, since it operates on parsed JSON content, not on
anything Groq-specific.

---

## 17. Risks

- **Schema drift**: Gemini's JSON output, even under a schema-constrained mode, may not produce
  byte-identical shapes to what Groq's `response_format: json_object` currently yields — every
  one of the 5 schemas (`resume-content`, `evidence-selection`, `cover-letter`, `email-content`,
  `jd-analysis`) would need real-response validation against a Gemini call before any cutover,
  not just a code review.
- **Grounding-rule false-positive rate could shift**: `GroundingValidator`'s rules (proper-noun
  vocabulary matching, numeric token matching) were tuned against Groq/Llama's actual phrasing
  habits (implicit in the "low temperature, extraction not creative writing" framing). A
  differently-phrasing model could trip more (or fewer) false positives, changing how often the
  regenerate-then-strip fallback fires in practice.
- **Cost/latency profile change**: nothing in this codebase measures or bounds cost today beyond
  `maxOutputTokens`; a provider swap changes the real-money cost per generation without any
  existing guardrail to catch a runaway change.
- **Losing the "one class touches the API key" guarantee**: `GroqClient`'s Javadoc states it is
  "the only class in the platform that talks to Groq" — introducing a second provider without an
  abstraction (§16) would duplicate the retry/circuit-breaker/logging-discipline guarantees
  across two classes instead of enforcing them once.
- **ADR contradiction risk**: any Gemini use for ATS/JD-fit scoring, template rendering, or file
  storage would directly contradict ADR-008/009/014 (deterministic scoring) or the project's own
  "never bent" rules (CLAUDE.md) — §15 already draws this boundary; it is a real risk only if a
  future implementation ignores it.

---

## 18. Recommended Migration Strategy (design-level recommendation only)

1. **Introduce the abstraction first, provider-neutral, Groq-only initially** — extract the
   `AiChatClient`-shaped interface (§16) with `GroqClient` as its sole implementation, and update
   the five calling services to depend on the interface. This alone changes zero behavior and is
   independently safe to ship before any Gemini code exists.
2. **Add a Gemini implementation behind the same interface**, validated offline against the
   existing 5 JSON schemas before it's wired into any live call path.
3. **Cut over one endpoint at a time, starting with `JdAnalysisService`** (§15) — it's the
   lowest-blast-radius call: no `GroundingValidator` dependency, no evidence-selection knock-on
   effect, and a bad JD analysis is caught early by `payload.isJobPosting()` and requirement-
   emptiness checks already in `ResumeGenerationService`/`JdService`.
4. **Only after JD analysis is stable on Gemini, consider `EvidenceSelectionService`**, watching
   whether `stripUnknownEvidenceIds()`'s hallucinated-citation rate changes.
5. **Content generation (`ResumeContentService`/`CoverLetterContentService`) last** — highest
   blast radius (directly user-facing prose, grounding-gated) — cut over only once the
   grounding-rule false-positive/negative rate has been observed on Gemini output in a
   non-production path.
6. **Keep Groq as the fallback implementation throughout**, not removed — the interface from
   step 1 makes a per-call or per-environment provider choice a config decision, not a code
   branch, and preserves a rollback path at every stage.

---

## 19. Files / Classes Involved (this analysis's evidence base)

**resume-service**: `ResumeController`, `ResumeGenerationService`, `TemplateService`,
`AiServiceClient`, `ClientDtos`, `ResumeRequests`, `ResumeVersion` (domain), `Template`/
`TemplateStatus`/`TemplateType` (domain), `TemplateRepository`, `TemplateSeeder`.

**ai-service**: `AiController`, `GroqClient`, `GroqProperties`, `GroqMessages`, `PromptRegistry`,
`UntrustedContent`, `EvidenceSelectionService`, `ResumeContentService`,
`CoverLetterContentService`, `JdAnalysisService`, `AiGenerationSupport`, `GroundingValidator`,
`GroundingReport`/`GroundingViolation`, `AiRequests`, `AiResponses`, `EvidenceItem`, plus
`resources/prompts/{jd-analysis,evidence-selection,resume-content,cover-letter,email-content}/v1.txt`
and `resources/schemas/*.schema.json`.

**application-service**: `ApplicationController`, `ApplicationService`,
`CoverLetterGenerationService`, `EmailGenerationService`, `ClientDtos`, `Application` (domain),
`ApplicationStatus`, `GenerationType`, `CoverLetterVersion`, `EmailContent`.

**document-service**: `DocumentController`, `CustomTemplateAssetController`,
`DocumentRenderService`, `CustomTemplateAssetService`, `ResumeRenderModelBuilder`,
`PdfRenderer`, `DocxMailMerge`, `PdfMailMerge`, `PdfStructureAnalyzer`, `PdfPlaceholderLocator`,
`ObjectStorageService`, `ResumeTemplate` (built-in template registry), `CustomTemplateAsset`
(domain), `DetectedField`, `TemplateStructure`, `RenderedDocument` (domain).

**jd-service**: `JdController`, `JdService`, `AiServiceClient` (jd-service's own),
`JobPostingExtractor`, `JobDescription`/`JdVersion`/`JdAnalysis`/`Requirement` (domain).

**profile-service**: `ProfileController` (all six evidence sections + `/evidence` aggregate
endpoint), `ProfileService`, `Profile` (domain, `EvidenceItemResponse` shape).

**frontend**: `TemplatePage.tsx`, `GenerateLayout.tsx`, `ProcessingPage.tsx`,
`GenerationProgress.tsx` (step list), `resumeApi.ts`, `applicationApi.ts`, `jdApi.ts`.

---

## 20. Environment Variables and External APIs (names only — no values)

| Variable | Purpose | Service |
|---|---|---|
| `GROQ_API_KEY` | Groq API secret | ai-service |
| `GROQ_BASE_URL` | Groq API base URL (defaults to `https://api.groq.com/openai/v1`) | ai-service |
| `GROQ_MODEL` | Model id (defaults to `openai/gpt-oss-120b`) | ai-service |
| `GROQ_TIMEOUT_SECONDS` | Per-request timeout (default 60) | ai-service |
| `GROQ_MAX_OUTPUT_TOKENS` | Generation token cap (default 4096) | ai-service |
| `GROQ_TEMPERATURE` | Sampling temperature (default 0.2) | ai-service |
| `GROQ_MAX_RETRIES` | HTTP-level retry budget for 429/5xx (default 2) | ai-service |
| `MONGODB_URI` | Atlas connection string, one per service database | every service |
| `JWT_SECRET` | Auth token signing secret | auth-service, api-gateway |
| MinIO/S3 credentials (names not enumerated here — not part of this task's scope) | Object storage for rendered documents/custom template assets | document-service |
| `EUREKA_SERVER_URI` | Service discovery registry URL | every service |
| `CONFIG_IMPORT` | Spring Cloud Config server URL | every service |

**No other external AI API is configured anywhere** — confirmed by the same repository-wide
search reported in §4.

---

*End of analysis. No production code was modified to produce this document or its companion
`docs/generation-architecture.json`.*
