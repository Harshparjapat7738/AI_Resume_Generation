# Architecture Decision Records

Every deviation from, gap in, or contradiction within
`CareerForge_AI_Final_Developer_Blueprint_MongoDB_Atlas.md` is recorded here before it is
implemented. The blueprint remains the source of truth; these records explain where
implementation reality required a choice the blueprint did not make.

| ADR | Decision | Status |
|---|---|---|
| [ADR-001](#adr-001) | Spring Data MongoDB only — no JPA/ORM layer | Accepted |
| [ADR-002](#adr-002) | Database ownership for the four services the blueprint omitted | Accepted |
| [ADR-003](#adr-003) | Testcontainers `mongodb-atlas-local` for integration tests | Accepted |
| [ADR-004](#adr-004) | Template ownership split between resume-service and document-service | Accepted |
| [ADR-005](#adr-005) | Async workers are in-process Redis Stream consumers, not separate deployables | Accepted |
| [ADR-006](#adr-006) | A thin `platform-common` library for cross-cutting web concerns | Accepted |
| [ADR-007](#adr-007) | Gateway-verified identity propagated as trusted headers | Accepted |
| [ADR-008](#adr-008) | ATS scoring uses fractional sub-checks so 99.9% is actually reachable | Accepted |
| [ADR-009](#adr-009) | Screening-readiness bands evaluated in strict priority order | Accepted |
| [ADR-010](#adr-010) | Assessments are keyed by resume **version**, not resume ID | Accepted |
| [ADR-011](#adr-011) | Config Server uses the `native` backend with a committed, secret-free config repo | Accepted |
| [ADR-012](#adr-012) | ai-service and notification-service are not routed through the public gateway | Accepted (notification-service portion moot — the service itself was removed, ADR-035; the ai-service reasoning still applies unchanged) |
| [ADR-013](#adr-013) | Resume generation runs synchronously — no Redis Streams job queue yet | Superseded by ADR-033 |
| [ADR-014](#adr-014) | ATS scoring checks structured resume content, not a rendered document | Accepted |
| [ADR-015](#adr-015) | JD URL extraction: SSRF guard + JSON-LD when present, generic text otherwise | Accepted |
| [ADR-016](#adr-016) | Template system Phase 1: built-in catalogue only, upload/online deferred | Superseded by ADR-033 |
| [ADR-017](#adr-017) | The central `Application` aggregate references artifacts; generation lifecycle ≠ tracking lifecycle | Accepted |
| [ADR-018](#adr-018) | document-service renders real Resume PDFs synchronously; download streams through the service rather than a presigned URL | Superseded by ADR-033 |
| [ADR-019](#adr-019) | Email generation: deterministic subject/frame + one grounded, model-written highlight paragraph | Accepted |
| [ADR-020](#adr-020) | Cover-letter generation orchestrated from application-service, mirroring resume-service's two-stage pipeline | Superseded by ADR-033 |
| [ADR-021](#adr-021) | Google OAuth state/PKCE store is Redis, not an HTTP session; account linking trusts only a Google-verified email | Accepted |
| [ADR-022](#adr-022) | "Generate All" is one `Application`, three independently-tracked outputs generated sequentially through the existing single-output pipelines | Accepted |
| [ADR-023](#adr-023) | Custom templates gain PDF support and become selectable in the main generation wizard by dispatching the existing render endpoint, not by adding a second pipeline | Superseded by ADR-033 |
| [ADR-024](#adr-024) | A provider-agnostic `AiChatClient` interface sits between the five AI generation call sites and `GroqClient`, with no second implementation yet | Accepted |
| [ADR-025](#adr-025) | Gemini added as a second, config-selected `AiChatClient` implementation via `@ConditionalOnProperty`; Groq remains the default, no workflow migrated | Superseded by ADR-032 |
| [ADR-026](#adr-026) | JD Analysis alone routed to Gemini via a new `AiProviderRouter`, Groq as automatic fallback; every other operation stays directly on Groq | Superseded by ADR-032 |
| [ADR-027](#adr-027) | Evidence Selection added as a second operation routed through `AiProviderRouter` to Gemini, same Groq-fallback contract; Resume/Cover Letter/Email content generation untouched | Superseded by ADR-032 |
| [ADR-028](#adr-028) | Cover Letter content routed to Gemini (third `AiProviderRouter` operation); document-service gains a parallel cover-letter PDF/DOCX rendering pipeline reusing the existing Thymeleaf/openhtmltopdf and docx4j/PDFBox engines | Superseded by ADR-032 (routing only — the cover-letter PDF/DOCX rendering pipeline itself is unaffected and remains Accepted) |
| [ADR-029](#adr-029) | Custom PDF templates the deterministic PDFBox scan can't understand fall back to Gemini-assisted layout analysis; Gemini output is validated/converted to the existing field model and rendered by the same deterministic PDFBox engine — never by Gemini itself | Superseded by ADR-033 |
| [ADR-030](#adr-030) | Resume Content routed to Gemini (fourth `AiProviderRouter` operation), same Groq-fallback contract; prompt, schema, grounding, regenerate-once-then-strip, and `ResumeVersion` persistence all untouched — only Email Content remains directly on Groq | Superseded by ADR-032 |
| [ADR-031](#adr-031) | Production-hardening pass over the Gemini/Groq architecture: router-level fallback/completion metrics, explicit tests proving grounding/schema failures never trigger provider fallback, and correction of several stale docs/config comments describing already-superseded behaviour — no change to routing, prompts, schemas, or rendering | Superseded by ADR-032 |
| [ADR-032](#adr-032) | Gemini-primary/Groq-fallback routing reverted platform-wide: Groq is once again the sole provider for every JSON/content-generation operation, `AiProviderRouter`/`AiOperation` deleted, `GeminiClient` trimmed to document/layout analysis only (its one still-`Accepted` use, ADR-029) | Superseded by ADR-033 (Gemini removed outright) |
| [ADR-033](#adr-033) | Resume and Cover Letter generation removed entirely: CareerForge produces JD-optimization data, not documents. `resume-service`/`document-service` deleted, Gemini removed, assessment re-keyed to the optimization. Email generation unchanged | Accepted |
| [ADR-034](#adr-034) | "My Templates": a user-owned library of uploaded Resume/Cover Letter files, living in profile-service (`templates` collection + the MinIO/S3 bucket document-service used to own), selected — never re-uploaded — at JD-optimization handoff time and referenced in the external AI prompt. No structural analysis, mail-merge or AI involvement; `document-service` is not reintroduced | Accepted |
| [ADR-035](#adr-035) | `notification-service` removed: it never grew past a wired-up bootstrap skeleton (no controller, no domain logic, no Redis Stream consumer was ever implemented) and had zero active callers anywhere in the platform. Candidate-facing email (application-service) is unaffected | Accepted |

---

<a id="adr-001"></a>
# ADR-001

## Decision

Persist exclusively through Spring Data MongoDB. No JPA, no Hibernate, no ORM abstraction
layer over MongoDB.

## Problem

The blueprint contradicts itself. §6 "Backend" lists both `Spring Data MongoDB` **and**
`MongoDB ORM/JPA layer` as stack items, while §28 Decision 1 and §30 both state plainly:
"Do not use JPA, Hibernate, Flyway or MySQL." An implementer following §6 literally would
add a dependency that §28 forbids.

## Options

1. Add a JPA-style abstraction (e.g. Hibernate OGM, JNoSQL) over MongoDB.
2. Use Spring Data MongoDB repositories plus `MongoTemplate` for complex queries.
3. Use the raw MongoDB Java driver.

## Selected Option

Option 2.

## Reason

Hibernate OGM is effectively unmaintained and MongoDB is not a relational store, so a JPA
façade adds an impedance mismatch with no benefit. Spring Data MongoDB already provides
repository derivation, and `MongoTemplate` covers aggregation and partial updates. §28 is
a "Critical Architecture Decision" section and §6 is a stack listing, so §28 wins.

## Impact

`spring-boot-starter-data-mongodb` is the only persistence dependency. `@Document`,
`@Field` and `@Indexed` replace `@Entity`/`@Column`. There is no schema-migration tool;
document-shape evolution is handled by tolerant readers and a `schemaVersion` field
(see `docs/DATABASE.md`).

---

<a id="adr-002"></a>
# ADR-002

## Decision

- `document-service` owns a new database `careerforge_document` for rendered-artifact
  metadata.
- `ai-service` owns no database. It is stateless apart from Redis (idempotency keys,
  in-flight job state, response cache).
- `notification-service` owns no database in v1. It consumes a Redis Stream and writes a
  delivery outcome back to the stream.
- `api-gateway`, `config-server` and `discovery-server` own no database.

## Problem

Blueprint §7 enumerates six databases (`auth`, `profile`, `jd`, `resume`, `assessment`,
`application`) but §5 defines ten services. Four services have no declared storage, yet
document-service must record where each rendered artifact lives, its checksum, its
template version and who owns it — and §30A requires that every service have explicit
collection ownership.

## Options

1. Store artifact metadata in `careerforge_resume` and let document-service write to it.
2. Give document-service its own database `careerforge_document`.
3. Store artifact metadata only in object-storage tags/keys.

## Selected Option

Option 2.

## Reason

Option 1 violates the blueprint's own hard rule that no service writes another service's
collections. Option 3 makes ownership checks depend on a storage-provider feature and
makes listing a user's artifacts an expensive bucket scan. Option 2 preserves the
ownership model at the cost of one extra logical database on the same Atlas cluster.

## Impact

Adds `MONGODB_DB_DOCUMENT=careerforge_document` to `.env.example`, one collection
(`rendered_documents`), and its indexes to `docs/DATABASE.md`. Resume-service references
artifacts by ID and fetches metadata over the document-service API, never by direct query.

---

<a id="adr-003"></a>
# ADR-003

## Decision

Integration tests run against the `mongodb/mongodb-atlas-local` Testcontainers image.
Runtime — local, staging and production — always uses real MongoDB Atlas.

## Problem

Blueprint §26 says "Integration — Testcontainers: MongoDB Atlas", and the implementation
prompt §9 says local development must not use a MongoDB container. Atlas is a hosted
service and cannot literally run in a container, so §26 as written is not executable.
Meanwhile §30A requires repository tests to run "against an isolated MongoDB test
environment", which a shared cloud cluster is not.

## Options

1. Point integration tests at a shared Atlas dev cluster.
2. Use the plain `mongo` Testcontainers image.
3. Use `mongodb/mongodb-atlas-local`, which packages the server plus the Atlas Search
   and Vector Search components.

## Selected Option

Option 3, with Option 2 as a fallback for services that need no Atlas-specific feature.

## Reason

Option 1 makes tests order-dependent, non-parallelisable and dependent on network and
credentials in CI — it fails §30A's "isolated" requirement. Option 3 keeps tests isolated
and ephemeral while preserving a path to the Atlas Search / Vector Search features the
blueprint defers to a later version (§7). Option 2 remains valid where no Atlas feature is
exercised, and starts faster.

## Impact

`docker-compose.yml` contains no MongoDB service, satisfying the prompt. Each
Mongo-backed service gets a `@Testcontainers` base test class. CI needs Docker but no
Atlas credentials. `MONGODB_URI` in CI is a placeholder that is never dialled.

---

<a id="adr-004"></a>
# ADR-004

## Decision

Split template responsibility:

- **document-service** owns the *renderable* template — versioned HTML/CSS and DOCX
  assets stored as files under `document-service/src/main/resources/templates/<id>/<version>/`.
- **resume-service** owns `careerforge_resume.templates`, which holds only *selectable
  template metadata*: id, display name, description, thumbnail key, supported formats,
  current version, enabled flag.

## Problem

Blueprint §7 places a `templates` collection under `careerforge_resume`, but §14 and §22
make document-service responsible for "versioned templates" and rendering. Left
unresolved, two services would each believe they own template definitions.

## Options

1. All template data in `careerforge_resume`, document-service fetches it per render.
2. All template data in document-service; resume-service proxies the catalogue.
3. Split: metadata in resume-service, renderable assets in document-service.

## Selected Option

Option 3.

## Reason

Renderable templates are code, not data: they are reviewed, diffed and released with the
service that executes them, which also enforces §14's rule that the LLM never produces a
template. The catalogue the user picks from is product data that belongs with the
generation workflow. The split keeps `careerforge_resume.templates` exactly where §7 put
it while honouring §14.

## Impact

Adding a template is a two-part change: ship assets in document-service, then insert the
catalogue row in resume-service. Version skew is caught at render time — document-service
rejects a `templateVersion` it does not have with `DOCUMENT_RENDER_FAILED`.

---

<a id="adr-005"></a>
# ADR-005

## Decision

Asynchronous work (AI generation, PDF/DOCX rendering, URL extraction) runs as Redis Stream
consumer groups **inside the owning service process**, not as separate deployable worker
services.

## Problem

Blueprint §5 calls for "dedicated asynchronous workers", but §29's repository structure
defines no worker modules, and §28 Decision 4 forbids adding Kafka. It is ambiguous whether
a worker is a separate deployable or a thread pool.

## Options

1. Separate `*-worker` Spring Boot deployables per async concern.
2. In-process consumer groups in the owning service, scaled by running more replicas.
3. Synchronous request handling with long HTTP timeouts.

## Selected Option

Option 2.

## Reason

Option 3 is ruled out by §7's "AI/document generation must be asynchronous and idempotent".
Option 1 doubles the deployable count for v1 with no isolation benefit, since a worker
would share the same code, config and MongoDB database as its service — and §5 explicitly
warns against creating dozens of services. Option 2 gives the same back-pressure and
retry semantics via consumer groups, and can be promoted to Option 1 later without
changing the message contract.

## Impact

`resume-service`, `document-service` and `jd-service` each run a consumer group alongside
their web tier. Horizontal scaling is per service. Every consumer must be idempotent and
keyed by a job ID, because Redis Streams give at-least-once delivery.

---

<a id="adr-006"></a>
# ADR-006

## Decision

Introduce `services/platform-common`, a plain JAR containing only: the `ApiError` envelope,
`ErrorCode`, `ApiException`, `GlobalExceptionHandler`, the correlation-ID servlet filter,
and the `@CallerId` argument resolver. It contains no domain model, no entity, no
repository and no client for any service.

## Problem

The implementation prompt requires a single consistent error envelope (§31), correlation
IDs on every request (§32) and ownership checks in every service (§14). Implemented
independently in nine services, these drift. Implemented as a fat shared library, they
couple services and defeat independent deployability.

## Options

1. Copy the boilerplate into each service.
2. A thin shared library restricted to cross-cutting web concerns.
3. A fat shared library that also holds shared DTOs and Feign clients.

## Selected Option

Option 2.

## Reason

Option 1 guarantees inconsistent error codes within two milestones. Option 3 is the classic
distributed-monolith failure mode: a DTO change forces a lock-step redeploy of every
service. Option 2 shares only what must be identical to satisfy an explicit requirement,
and changing it is a deliberate, reviewable platform change.

## Impact

`platform-common` is a module in the reactor; every service Dockerfile builds with
`-am` so the reactor resolves it. It registers via Spring's
`AutoConfiguration.imports`, so services do not need to scan `ai.careerforge.common`.
**Rule: nothing domain-specific ever enters this module.** Adding a DTO here requires a new ADR.

---

<a id="adr-007"></a>
# ADR-007

## Decision

The gateway verifies the JWT once and forwards the caller's identity as `X-User-Id` and
`X-User-Roles`. Internal services trust these headers for *identity* but still perform
their own *authorisation* check on every document they read or write.

## Problem

The blueprint requires JWT auth at the gateway (§5) and per-resource ownership checks in
every service (§21). It does not say whether services should re-verify the token
themselves.

## Options

1. Every service independently verifies the JWT signature.
2. Gateway verifies; services trust propagated headers.
3. Gateway verifies and mints a short-lived internal token per hop.

## Selected Option

Option 2, with two mandatory safeguards.

## Reason

Option 1 spreads the signing secret to nine services, multiplying the blast radius of a
leak. Option 3 is the strongest but adds a token-minting hop that is not justified for a
single-cluster v1. Option 2 is standard for a private service network, provided the two
safeguards below hold.

**Safeguard 1 — anti-spoofing.** `JwtAuthenticationFilter` unconditionally strips any
inbound `X-User-Id`/`X-User-Roles` before setting its own, so a client cannot inject
another user's ID.

**Safeguard 2 — network isolation.** Business services are declared with `expose:` rather
than `ports:` in `docker-compose.yml`, so they are reachable only on the internal Docker
network. In production they must sit behind an equivalent boundary (private subnet, mesh
mTLS, or NetworkPolicy).

## Impact

`careerforge.jwt.secret` is required only by `auth-service` (signing) and `api-gateway`
(verification). Identity propagation is explicitly **not** the authorisation boundary:
every repository query must be scoped by `userId`, and a document belonging to another
user returns 404 rather than 403 so IDs cannot be enumerated.

---

<a id="adr-008"></a>
# ADR-008

## Decision

Each ATS check produces a score in `[0.0, 1.0]` rather than a boolean, and the total is
rounded to one decimal place:

```text
ATS Score = round( Σ (weightᵢ × passRatioᵢ), 1 )
```

## Problem

Blueprint §15 defines ten integer weights summing to 100 and the formula
`ATS Score = sum(weight × pass)`. With `pass ∈ {0,1}` every achievable score is an
integer, so the 99.9% figure the product displays (§2, §15, §17) is **mathematically
unreachable**. Implemented literally, the product would advertise a number its own engine
can never produce.

## Options

1. Keep boolean checks and display "100%" when all pass — contradicts the stated 99.9%.
2. Keep boolean checks and hard-cap the display at 99.9% — dishonest; §15 forbids
   manipulating the score.
3. Make sub-checks fractional so a near-perfect but imperfect document scores 99.9.

## Selected Option

Option 3.

## Reason

Option 2 is exactly the score manipulation §15 prohibits. Fractional sub-checks are also
more truthful: "Standard headings" is genuinely a ratio (7 of 8 headings recognised), not a
yes/no. A document that fails one sub-check inside one weighted category lands just below
100, which is precisely the 99.9 case the product describes.

## Impact

Each of the ten checks defines its own named sub-checks and returns
`passedSubChecks / totalSubChecks`. `ats_assessments` stores the per-check breakdown so
the UI can explain the exact fraction lost. 100.0 remains achievable and is displayed as
"100%"; the product copy must continue to state that this is CareerForge's own ATS
compatibility measure and says nothing about any external ATS.

---

<a id="adr-009"></a>
# ADR-009

## Decision

Screening-readiness bands are evaluated top-down, first match wins, with `WEAK_FIT` as an
exhaustive default:

```text
if unmetHardRequirements >= 3 or coverage < 0.35            -> WEAK_FIT
else if ats >= 85 and coverage >= 0.80 and unmetHard == 0   -> STRONG
else if ats >= 75 and coverage >= 0.60                      -> COMPETITIVE
else if coverage >= 0.35                                    -> STRETCH
else                                                         -> WEAK_FIT
```

## Problem

Blueprint §17's bands are neither exhaustive nor mutually exclusive:

- **Gap.** ATS 70 with coverage 75% matches no band (fails COMPETITIVE's ATS floor,
  above STRETCH's coverage ceiling).
- **Overlap.** Coverage exactly 60% satisfies both COMPETITIVE and STRETCH.
- **Precedence unstated.** A resume with ATS 90 and coverage 85% but two unmet hard
  requirements matches STRONG's numeric thresholds while failing its qualitative rule,
  and also matches STRETCH's "1–2 important gaps".

## Options

1. Implement as written and let the first matching branch win by accident of code order.
2. Define an explicit, exhaustive, ordered rule set.
3. Replace bands with a continuous score.

## Selected Option

Option 2.

## Reason

Option 1 makes the band an artefact of source ordering — untestable and unexplainable,
which §16 and §17 both forbid. Option 3 contradicts §2's explicit ban on a
probability-like number. Option 2 keeps the blueprint's four bands and its thresholds,
and only supplies the precedence and default the blueprint left undefined. Unmet hard
requirements are checked first because a hard requirement is disqualifying regardless of
formatting quality.

## Impact

The band function is total: every input maps to exactly one band. Unit tests cover each
boundary (coverage 0.349/0.35, 0.599/0.60, 0.799/0.80; ATS 74.9/75, 84.9/85) and the
previously unreachable region. The UI always shows which rule fired and why, per §17.

---

<a id="adr-010"></a>
# ADR-010

## Decision

Assessment endpoints are keyed by resume **version** ID:

```text
POST /api/assessment/resume-versions/{resumeVersionId}
GET  /api/assessment/resume-versions/{resumeVersionId}
```

The blueprint's `/api/assessment/{resumeId}` is kept as a convenience read that resolves
to the latest version of that resume.

## Problem

Blueprint §22 exposes `/api/assessment/{resumeId}`, but §7's indexes key assessments on
`resumeVersionId` (`ats_assessments: resumeVersionId unique`). With a resume ID alone, the
unique index cannot be honoured — regenerating a resume would either collide or silently
overwrite the previous assessment.

## Options

1. Follow §22 and key assessments by resume ID, dropping the uniqueness guarantee.
2. Key by resume version ID and change the URL.
3. Key by version ID internally, keep the §22 URL, resolve to latest server-side.

## Selected Option

Option 2 for writes, Option 3 for reads.

## Reason

Scores describe a specific rendered document; a regenerated resume is a different document
and must not inherit the old score. Keeping a latest-version convenience read preserves the
blueprint's URL for the common dashboard case without weakening the write contract.

## Impact

`docs/API_CATALOG.md` documents both forms. The frontend always uses the explicit
version-scoped URL when displaying scores next to a preview, so the number on screen
always belongs to the document on screen.

---

<a id="adr-011"></a>
# ADR-011

## Decision

Config Server runs the `native` backend over `infrastructure/config-repo/`, which is
committed to Git and contains **no secrets**. Secrets reach services only as environment
variables, referenced from config as `${ENV_VAR}` placeholders.

## Problem

The blueprint requires Spring Cloud Config (§6) but does not say where configuration lives
or how it stays compatible with §21's absolute prohibition on committing secrets.

## Options

1. Git backend pointing at a second private repository.
2. Native backend over a directory in this repository.
3. Vault backend.

## Selected Option

Option 2 for v1; Option 3 is the documented production upgrade path.

## Reason

A second repository is operational overhead for a single-team v1 and tempts contributors to
commit secrets into a "private" repo. The native backend keeps non-secret configuration
reviewable in the same pull request as the code that consumes it, while placeholder
resolution guarantees that a leaked config file discloses variable *names* only.

## Impact

`config-server` mounts `./infrastructure/config-repo` read-only in Docker. Gitleaks runs in
CI over the whole repository, so an accidentally committed secret fails the build. Moving to
Vault later changes only the config-server backend, not any consuming service.

---

<a id="adr-012"></a>
# ADR-012

## Decision

`ai-service` and `notification-service` have no route in `api-gateway`. They are reachable
only from other services on the internal network.

## Problem

Blueprint §5 shows all services under the gateway, and §22 lists no AI or notification
endpoints — leaving it open whether a browser could call them.

## Options

1. Route both through the gateway like every other service.
2. Route neither; expose them internally only.

## Selected Option

Option 2.

## Reason

A browser-reachable AI endpoint is a direct path to prompt-injection and cost abuse: a
caller could bypass JD confirmation, evidence selection and grounding validation, and drive
Groq spend arbitrarily. Every legitimate AI call originates from resume-service or
jd-service *after* the JD is confirmed. Notification-service is likewise event-driven and
has no user-facing operation. This also satisfies §17 (AI security) and §37 (the Groq key
never approaches the client).

## Impact

Adding a user-facing AI feature requires a new gateway route plus a new ADR, forcing the
security review rather than allowing it to happen by omission. Local debugging of these two
services uses their container ports on the Docker network, not `localhost`.

---

<a id="adr-013"></a>
# ADR-013

## Decision

`POST /api/resumes/generate` runs the full generation pipeline synchronously inside the HTTP
request/response cycle and returns the finished `resume_versions` document directly, instead
of the documented `202 Accepted` + job id, polled via
`GET /api/resumes/generations/{jobId}`.

## Problem

The documented contract (`docs/API_CATALOG.md` §3, Milestone 5) assumes an async worker
consuming a Redis Stream (ADR-005) to drive the evidence-selection → resume-content pipeline
in the background. That worker does not exist yet — this milestone slice implements the
first real, end-to-end vertical path (auth → profile → JD → resume) ahead of the async job
infrastructure, to prove genuine integration rather than build a job queue no other service
uses yet.

## Options

1. Build the Redis Streams worker now, ahead of everything else that would use it.
2. Run generation synchronously for this slice; add the async worker when a second
   long-running job type exists to justify the shared infrastructure.

## Selected Option

Option 2.

## Reason

The pipeline is two sequential Groq calls (evidence-selection, then resume-content, with at
most one grounding-triggered retry) — single-digit seconds in the common case, tens of
seconds worst case. That's within a normal HTTP timeout and doesn't yet justify a queue,
a consumer, idempotency-key bookkeeping and job-status polling for a single caller.
Building that infrastructure speculatively, before a second async workflow exists to share
it, would be exactly the kind of premature generality the blueprint's aggregate design
(docs/DATABASE.md §2) warns against.

## Impact

- `resume_generations.status` is written once, already in a terminal state
  (`COMPLETED`/`FAILED`), rather than progressing through `QUEUED → SELECTING_EVIDENCE → …`.
- There is no `GET /api/resumes/generations/{jobId}` endpoint yet — the frontend shows an
  indeterminate "generating" state for the duration of the request instead of staged
  progress, which is the honest representation of what the backend actually reports.
- `POST /api/resumes/generate` returns `200` with the full result, not `202` with a job id.
- Moving to the async contract later is additive: introduce the Redis Streams worker,
  change this endpoint to enqueue and return `202`, and add the polling endpoint — no
  existing field or persisted document shape needs to change.

---

<a id="adr-014"></a>
# ADR-014

## Decision

`assessment-service`'s ATS engine scores the generated resume's **structured JSON content**
(the same payload `resume-service` persists and returns), not a rendered PDF/DOCX. The
seven checks it computes (contact info present, summary present, experience section
present, date consistency, bullet length suitability, keyword presence, grounding
integrity) replace the blueprint's ten document-formatting checks for now.

## Problem

`docs/DATABASE.md` §3 documents ten checks — `MACHINE_READABLE_TEXT`, `ATS_SAFE_LAYOUT`,
`STANDARD_HEADINGS`, `CONTACT_PARSING`, `DATE_CONSISTENCY`, `NO_INFO_ONLY_IMAGES`,
`STANDARD_FONTS`, `HEADER_FOOTER_SAFETY`, `FILENAME`, `LENGTH_SUITABILITY` — most of which
only mean something against an actual rendered file (does the PDF use embedded fonts? does
it have a header/footer that swallows content when parsed?). `document-service` doesn't
exist yet (no PDF/DOCX rendering, ADR-013's scope), so there is no rendered artifact for
any of those checks to inspect.

## Options

1. Build `document-service` (PDF rendering) first, so the ten checks can run against a real
   file, before implementing any ATS scoring at all.
2. Score what's actually available today — the structured content, the evidence behind it,
   and the JD it was matched against — with a smaller, honestly-scoped check set, and revisit
   once a rendered document exists.
3. Compute all ten named checks now, hard-coding the document-formatting ones to a fixed
   passing value.

## Selected Option

Option 2.

## Reason

Option 3 would be exactly the fabrication this product's own core principle forbids applied
to itself — a "check" that always reports success without checking anything is not a check,
it's a decoration. Option 1 blocks a working, honest ATS score behind an unrelated and much
larger feature (document rendering). Option 2 ships a real, deterministic, explainable score
today, scoped to what genuinely can be verified from data that exists: profile completeness,
date sanity, bullet quality, JD keyword presence, and the grounding report `ai-service`
already computed. `JdFitScoringEngine`'s compatibility formula
(`0.50·coverage + 0.20·keyword + 0.20·seniority + 0.10·recency`) matches
`docs/CODEBASE.md` §2 exactly — only the *ATS* checks are rescoped, not the JD-fit formula.

## Impact

- `AtsAssessment.engineVersion = "content-v1"` — when a document-rendering-based engine
  ships, it becomes `"document-v1"` (or similar) so historical scores stay attributable to
  the engine that produced them, per `docs/DATABASE.md` §3's own stated reason for
  versioning the engine.
- The seven checks, their weights (15/10/15/15/15/15/15 = 100) and detail strings are
  implemented in `AtsScoringEngine` (assessment-service) — see
  `docs/API_CATALOG.md` §2 for the exact response shape.
- `jd_fit_assessments.jdVersionId` from the blueprint's unique-together key is dropped;
  `resumeVersionId` alone is the unique key, since `resume-service` doesn't track a
  `jdVersionId` today (ADR-013's scope) and a resume version is generated against exactly
  one JD, so this is equivalent in practice.
- `recommendations` is embedded on `JdFitAssessment` rather than its own collection — see
  the class Javadoc; it's always read together with the fit score, which is exactly when
  `docs/DATABASE.md` §2 says to embed.
- Moving to Option 1 later doesn't invalidate this work: `AtsScoringEngine` becomes one
  input into a combined score (or is replaced), and the JD-fit engine is untouched either
  way.

---

<a id="adr-015"></a>
# ADR-015

## Decision

`jd-service` fetches a job posting URL through a purpose-built SSRF guard
(`SsrfGuard` + `JdUrlFetcher`), extracts it via schema.org `JobPosting` JSON-LD when the
page provides it, and otherwise falls back to generic readable-text extraction
(`JobPostingExtractor`) — the same two-tier honesty principle as ADR-014: structured fields
are populated only when genuinely present, never guessed from unstructured HTML.

## Problem

The blueprint calls for JD intake by URL "from platforms such as LinkedIn, Indeed, Naukri,
company career pages" with SSRF hardening. Two separate problems hide in that one sentence:

1. **Security.** A URL supplied by an authenticated user is still executed by the backend on
   the internal network. Without validation, it can be used to probe or reach services that
   are never meant to be internet-reachable — including, notoriously, the
   `169.254.169.254` cloud-metadata endpoint.
2. **Honesty about coverage.** Named platforms (LinkedIn, Indeed) actively block automated
   fetching; a scraper built to defeat that would be both fragile and outside what this
   product should do. There is also no single HTML shape shared across "company career
   pages" to scrape reliably.

## Options

1. Build named, site-specific scrapers for LinkedIn/Indeed/Naukri (CSS selectors per site).
2. Fetch generically, with a real SSRF guard, and extract via whatever structured data the
   page actually provides — schema.org `JobPosting` JSON-LD where present, readable text
   otherwise. Make no per-site promises.
3. Don't build URL intake at all; text paste remains the only intake path.

## Selected Option

Option 2.

## Reason

Option 1 requires reverse-engineering and continuously maintaining scrapers against sites
that actively fight scraping (LinkedIn/Indeed rate-limit and block non-browser traffic; a
scraper robust enough to reliably defeat that would be evading anti-bot measures, which this
product should not do). It would also silently stop working the moment any target site
changes its markup, with no way for the product to know. Option 3 leaves a real, requested
capability unbuilt. Option 2 is honest by construction: JSON-LD `JobPosting` is a real,
stable, machine-readable contract that a meaningful share of company career pages and ATS
platforms (Greenhouse, Lever, Workday, and others) already publish — when it's there, the
preview is genuinely structured; when it isn't, the user still gets the page's real text
(exactly like a paste) rather than an error, and the AI analysis step (already built,
ADR-none-needed — it's the existing pipeline) derives title/company/requirements from that
text the same way it does for pasted JDs. Sites that block the fetch outright fail cleanly
with "Unable to extract this job description from this URL" and a one-click path back to
pasting — never a fabricated result.

## Security design (`SsrfGuard`, `JdUrlFetcher`)

- **Scheme** — `http`/`https` only.
- **Port** — the scheme's default port only (`80`/`443`); no probing arbitrary ports.
- **Address validation** — every DNS-resolved address is checked against loopback,
  link-local (covers the `169.254.169.254` metadata endpoint), site-local/RFC1918 private
  ranges, multicast, wildcard, carrier-grade NAT (`100.64.0.0/10`), `0.0.0.0/8`, and IPv6
  unique-local (`fc00::/7`, which `InetAddress.isSiteLocalAddress()` does not cover).
- **Redirects are never followed automatically** — `HttpClient.Redirect.NEVER`, then each
  `Location` header is re-validated through the full guard before being followed (up to 5
  hops). A public URL redirecting to a private one is exactly the pattern the guard exists
  to stop; validating only the first URL would miss it entirely.
- **Content-type allowlist** — only `text/html` responses are read; anything else (a JSON
  API, a binary) is rejected before its body is parsed.
- **Size cap** — 3&nbsp;MB, enforced while streaming, not after buffering the whole body.
- **Timeouts** — 5s connect, 10s per request.
- **Known, documented limitation:** DNS is resolved once, validated, then the connection is
  made a moment later using the hostname again — a DNS-rebinding attacker who controls both
  the DNS record and the timing could in theory swap the answer in between. Full mitigation
  means pinning the connection to the exact validated address, which the JDK's `HttpClient`
  doesn't expose simply. Accepted for now because the caller is an authenticated user
  submitting a URL about their own job search, not an anonymous adversarial input — recorded
  here rather than silently assumed away.

## Impact

- `job_descriptions` gains `sourceUrl`, `location`, `skillsSummary`, `experienceSummary` (all
  nullable; see `docs/DATABASE.md` §3). Existing `TEXT`-sourced documents are unaffected —
  the new fields simply don't apply to them.
- `POST /api/jd/fetch-url` reuses the exact same confirm → analyse pipeline as text intake;
  no new confirmation step or status was introduced (see `docs/API_CATALOG.md` §2).
- No new supported-provider list is claimed anywhere in the product. Support is determined
  by what a given page actually publishes, not by domain name.

---

<a id="adr-016"></a>
# ADR-016

## Decision

Phase 1 of the template system implements only the **built-in template catalogue**: metadata
in `resume-service` (`templates` collection, per ADR-004), a selection API
(`GET /api/resumes/templates[/​{id}]`), and `templateId`/`templateVersion` persisted on
`resume_generations` and `resume_versions`. Custom-upload and online templates are **not**
built — the frontend shows both as an honest "Coming Soon" state, and the schema reserves
`source` (`BUILT_IN` · `CUSTOM_UPLOAD` · `ONLINE`) and `ownerUserId` so neither requires a
breaking change when they ship.

## Problem

`document-service` — the service ADR-004 assigns ownership of *renderable* template assets to
— is an empty skeleton today (`DocumentApplication.java` only, no rendering code). Building
upload would mean accepting a user-supplied template file that nothing can actually render;
building "browse online templates" would mean either fabricating a catalogue or scraping
third-party sites for content this product has no license to redistribute. Both would be
exactly the kind of fabricated capability this codebase's other ADRs (008, 014, 015) have
consistently refused to ship.

## Options

1. Build all three sources now, stubbing the parts that don't have a real backend yet.
2. Build only the built-in catalogue; represent upload/online honestly as not-yet-available.
3. Defer the entire template system until document-service exists.

## Selected Option

Option 2.

## Reason

Option 1 fails the same test ADR-014 already applied to ATS checks: a feature that appears to
work but doesn't do the thing it claims is a fabrication, not a feature. Option 3 blocks a
real, independently useful capability — the wizard can record and honor a template selection
today, and every generated resume already carries a `templateId` ready for document-service to
render against once it exists — behind unrelated, larger work. Option 2 ships exactly what has
a genuine backend behind it.

## Additional decisions folded into this ADR

- **Default template.** `POST /api/resumes/generate` accepts an optional `templateId`;
  omitting it resolves to `classic` (`TemplateService.DEFAULT_TEMPLATE_ID`) rather than
  requiring every caller to select one, keeping the pre-existing generation contract backward
  compatible.
- **`previewKey`, not `thumbnailKey`.** `docs/DATABASE.md`'s original sketch named this field
  `thumbnailKey`, implying a stored image asset. No thumbnail-rendering pipeline exists, so the
  field is named `previewKey` and the frontend maps it to a real local component that renders
  the template's actual structural definition (header style, column count, section-heading
  treatment) — a genuine, if lightweight, preview of the real layout, never a stock image.
- **All three built-in templates are single-column.** Multi-column resumes are a known ATS
  parsing risk, and there is no renderer yet to verify any layout actually parses cleanly.
  Claiming `atsSafe: true` for an unverified multi-column layout would be exactly the score
  manipulation ADR-008 already prohibits, applied to a different field.
- **Route.** The frontend step lives at `/generate/template/:jdId`, matching the existing
  `:jdId`-scoped pattern of every other wizard step (`/generate/review/:jdId`,
  `/generate/processing/:jdId`) rather than a bare `/generate/template` with no JD context.

## Impact

- `resume-service` gains `Template`/`TemplateRepository`/`TemplateService`/
  `TemplateController`/`TemplateSeeder`; `ResumeGeneration` and `ResumeVersion` gain
  `templateId`/`templateVersion`.
- Selecting an unauthorized, disabled, or nonexistent template returns `404` before any AI
  call is made — consistent with ADR-007's BOLA hardening (`ApiException.notOwned()`), and
  cheaper than failing after spending a Groq request.
- Moving to Option 1's full scope later is additive: `document-service` implements rendering
  first (closing the gap this ADR identifies), then upload gets a real endpoint and online
  gets a real provider integration — neither requires changing the `templates` schema or the
  `templateId` already stamped on every existing resume version.

---

<a id="adr-017"></a>
# ADR-017

## Decision

`application-service` implements the central `Application` aggregate the blueprint's diagram
describes — user, job, generation type, template, resume, cover letter, email, ATS
assessment, JD-fit assessment — as a **references-only** document, not a copy of any of it.
`GenerationType` (`RESUME_ONLY` · `COVER_LETTER_ONLY` · `EMAIL_ONLY` · `ALL`) is a real,
storable domain value today; only `RESUME_ONLY` can reach `ApplicationStatus.COMPLETED`,
because resume-service is the only generation pipeline that exists (ADR-013). Application
status (`DRAFT` → `PROCESSING` → `COMPLETED`/`FAILED`) is the **generation** lifecycle, kept
deliberately separate from the job-search **tracking** lifecycle
(applied/interviewing/offer/rejected/withdrawn) `docs/DATABASE.md`'s original `applications`
sketch conflated into one `status` field.

## Problem

Two open questions blocked this milestone:

1. **Does a central `Application` entity need to exist at all**, or is a resume-service
   generation plus an assessment-service score already "the application"? The blueprint's
   diagram (user → profile → job → JD → generation type → template → resume → cover letter →
   email → ATS assessment → JD-fit assessment) names something none of the five implemented
   services own: the *bundle*, not any one artifact in it. profile-service, jd-service,
   resume-service and assessment-service each correctly own one artifact and nothing else
   (`docs/DATABASE.md` §2's aggregate-boundary rule) — none of them is the right owner for
   "this specific resume, generated against this specific JD, for this specific job, is one
   application."
2. **`docs/DATABASE.md`'s existing `applications` sketch** (`status`: `DRAFT` · `READY` ·
   `APPLIED` · `INTERVIEWING` · `OFFER` · `REJECTED` · `WITHDRAWN`) describes a *tracking
   board* for applications a user has already sent — a different, later feature from "did
   generation for this application finish", which is what this milestone actually needs and
   what the task's lifecycle (`DRAFT`/`PROCESSING`/`COMPLETED`/`FAILED`) describes.

## Options

For (1):

1. Treat "the application" as implicit — a resume version plus its assessment, resolved by
   the frontend joining `resume-service` and `assessment-service` responses at render time.
2. Create the central `Application` aggregate in `application-service`, which already exists
   as a wired-up skeleton (`docs/CODEBASE.md`, `docs/API_CATALOG.md` Milestone 8) reserved for
   exactly this role, and have it reference the other services' artifacts by ID.
3. Fold an `applications` collection into `resume-service` instead, since resume is the only
   artifact that exists today.

For (2):

1. Overwrite the sketch's tracking `status` values with the generation-lifecycle ones this
   milestone needs.
2. Add a second field for the tracking status now, unused until a later milestone implements
   it.
3. Keep `status` as the generation lifecycle only; treat the tracking board as an explicitly
   separate, later concern with its own field when it's actually built.

## Selected Option

Option 2 for both.

## Reason

For (1): Option 1 has no place to persist "this resume, cover letter and email are one
application" — a page refresh loses the association, and nothing prevents a second dashboard
feature from re-deriving it differently. Option 3 repeats the exact mistake ADR-002 already
rejected for document-service: a service writing data that conceptually belongs to a
different aggregate. Option 2 costs nothing extra — the service, its port, its gateway route
(`/api/applications/**`) and its planned collection (`careerforge_application.applications`)
were already reserved for this — and matches `docs/DATABASE.md` §2's embed-vs-reference rule
exactly: an application *references* a resume, cover letter, email and two assessments
because each is independently versioned and independently queried, never embeds them.

For (1), assessment references specifically: assessment-service keys both the ATS and JD-fit
assessment uniquely by `resumeVersionId + userId` (ADR-010) and exposes no separate assessment
ID through its API. Inventing one on `Application` to satisfy "persist an assessment
reference" literally would mean storing an ID nothing else recognises — exactly the kind of
fabricated field ADR-008/014/015/016 have each refused to ship elsewhere in this codebase. The
real, honest reference is `resumeVersionId` itself, which `Application` already stores; a
boolean `assessed` records only whether one was found, set on a best-effort lookup the same
way the frontend's own assessment call is already non-fatal (`ProcessingPage`).

For (2): Option 1 would retroactively change the meaning of a value the blueprint's own
diagram and this milestone's explicit instructions describe as the *generation* lifecycle,
silently repurposing `READY`/`APPLIED`/etc. for something they were never designed to mean.
Option 2 speculatively adds a field for a feature not yet designed — exactly the premature
generality ADR-013 already declined elsewhere. Option 3 keeps today's model honest about what
it actually tracks (has generation finished?) and leaves the tracking board as a clean
additive change: a new `trackingStatus` field plus its own state machine, whenever a real
"mark as applied / interviewing / offer" feature is built, changing no existing document.

## Impact

- New collections `careerforge_application.applications` and
  `.application_status_history`, both documented in `docs/DATABASE.md` §3, with indexes
  `userId+createdAt`, `userId+status`, and `applicationId+changedAt` (§4) — the exact indexes
  already reserved there.
- `POST /api/applications` verifies `jobDescriptionId` (jd-service), and, if supplied,
  `resumeVersionId` (resume-service, and it must belong to the same `jobDescriptionId`) and
  `templateId` (resume-service's template catalogue, ADR-016) before persisting — the same
  `ApiException.notOwned()` 404 pattern every other service in this codebase already uses
  (ADR-007). It never calls ai-service and never drives generation itself; it records the
  outcome of a pipeline that already ran, matching the Milestone 8 API contract's own wording:
  "save an application version with JD, resume, scores and template."
  `PATCH /api/applications/{id}/status` enforces a small legal-transition table
  (`DRAFT/FAILED → PROCESSING`, `PROCESSING → COMPLETED/FAILED`, `FAILED → DRAFT`) and appends
  to `application_status_history`; `COMPLETED` is terminal.
- `resume-service`'s and `assessment-service`'s domain models, generation pipeline (ADR-013)
  and scoring engines (ADR-014) are untouched — `application-service` only calls their
  existing read APIs (`GET /api/jd/{id}`, `GET /api/resumes/{id}`,
  `GET /api/resumes/templates/{id}`, `GET /api/assessment/resume-versions/{resumeVersionId}`).
  `/results/:resumeId` and the existing resume/ATS dashboard flow keep working unmodified and
  unaware `application-service` exists.
- Cover-letter, email and Gmail-draft generation endpoints documented for Milestone 8
  (`.../cover-letter`, `.../email`, `.../gmail-draft`) remain unimplemented, as instructed;
  `GenerationType` represents the values, nothing generates them yet.
- The frontend is not wired to `application-service` in this milestone — `OutputTypePage`,
  `ProcessingPage` and `/results/:resumeId` are unchanged. Wiring the wizard to call
  `POST /api/applications` after a resume generation succeeds is additive follow-up work, not
  required for the domain model to exist and be tested.

---

<a id="adr-018"></a>
# ADR-018

## Decision

`document-service` renders real Resume PDFs — Thymeleaf HTML template → jsoup DOM
normalisation → openhtmltopdf-pdfbox → PDF, stored in MinIO/S3 and persisted as
`rendered_documents` (ADR-002) — running **synchronously** inside the HTTP request, the same
deviation ADR-013 already established for resume generation. Download streams the PDF bytes
through this service rather than issuing a presigned MinIO URL. Cover letters, DOCX and the
async render-job contract the blueprint and `docs/API_CATALOG.md` §3 originally sketched
remain unimplemented.

## Problem

ADR-016 left `document-service` an empty skeleton and named exactly what it needed to become
real: "document-service implements rendering first (closing the gap this ADR identifies)".
Two questions had to be answered to do that:

1. **Synchronous or async?** The documented Milestone 6 contract is
   `POST /api/documents/{resumeVersionId}/render` → `202` + a job to poll, matching the
   blueprint's async job-queue design for every generation-shaped endpoint.
2. **How does a browser get the bytes?** The same documented contract's download endpoint is
   a 300-second presigned MinIO URL — the browser fetches the file directly from object
   storage, not through this service.

## Options

For (1):

1. Build the documented async contract now — a Redis Streams render job, polled via a new
   endpoint.
2. Render synchronously in the request handler, exactly like ADR-013's resume generation.

For (2):

1. Issue a presigned MinIO URL, as documented, and let the browser fetch it directly.
2. Stream the PDF bytes through document-service's own authenticated endpoint.

## Selected Option

Option 2 for both.

## Reason

For (1): there is still no Redis Streams worker anywhere in this codebase (ADR-013, ADR-005)
— building one solely for document rendering, while resume generation itself (the far more
expensive, multi-Groq-call step immediately upstream of this one) remains synchronous, would
add a queue in the wrong place first. Rendering an already-validated, already-grounded
resume's structured content through a template is deterministic, local CPU work — no external
API call, no unbounded latency — and reuses the exact deviation already accepted and tested
for resume generation. Building the queue is real future work, not a gap specific to this
service.

For (2): the documented presigned-URL flow requires a MinIO/S3 endpoint the *browser* can
reach directly, distinct from the internal Docker-network address (`http://minio:9000`)
document-service itself uses to talk to it — a second, public storage endpoint to provision,
secure and keep in sync with the internal one, purely so a URL can be handed to the browser.
Streaming through document-service needs none of that: MinIO is never reached from outside
the Docker network at all, no storage endpoint or credential is ever part of any response,
and the download URL contains only an opaque Mongo id — the actual object key inside the
bucket (a separate random UUID) never reaches the client. This satisfies "do not expose
private storage credentials" and "do not expose unrestricted filesystem paths" more directly
than the originally-sketched design, at the cost of proxying bytes through one extra hop,
which is negligible for a resume-sized PDF.

## Rendering pipeline detail

Thymeleaf's default HTML5 output is not guaranteed to be strict XHTML, which
openhtmltopdf's XML-based renderer requires. Rather than hand-write strict XHTML templates,
the rendered HTML string is parsed by jsoup (tolerant of real-world HTML) and its DOM walked
directly into a W3C `Document` (`W3CDom().fromJsoup(...)`), which openhtmltopdf consumes —
templates stay ordinary, readable HTML+CSS. One concrete win this caught during testing:
openhtmltopdf's CSS engine does not support the `:not()` pseudo-class — a
`span:not(:last-child)::after` separator rule silently failed to apply (logged as a parser
warning, not an error) in two of the three templates until replaced with an equivalent
`span + span::before` adjacent-sibling rule, which is supported.

## Impact

- `document-service` gains `Template`-adjacent-but-distinct rendering code: it does **not**
  duplicate resume-service's `templates` catalogue (ADR-004's split holds) — it only maps the
  same three ids (`classic`, `modern-ats`, `professional`) to versioned HTML/CSS assets under
  `document-service/src/main/resources/templates/<id>/<version>/resume.html`, and exposes no
  catalogue endpoint of its own.
- Rendering merges two kinds of data: ai-service's already-grounded content (summary,
  experience bullets, project descriptions — verified against evidence at generation time,
  ADR-013's pipeline) with factual profile data that was never AI-touched (name, contact,
  dates, education, certifications, achievements) pulled directly from profile-service. Only
  experiences/projects the AI actually selected for this JD are rendered — education,
  certifications and achievements are shown in full, since they're the candidate's own
  factual record, not tailored content.
- Rendering defaults to whichever template a resume version was actually generated with
  (`ResumeVersion.templateId`, ADR-016) rather than a document-service-local default; an
  explicit `templateId` on the render call overrides it. A resume version that predates
  template selection entirely falls back to `classic`, matching
  `TemplateService.DEFAULT_TEMPLATE_ID`.
- `rendered_documents` is unique on `(resumeVersionId, format)` — re-rendering (e.g. picking a
  different template) replaces the row and its stored object rather than accumulating
  history; identical content and template return the cached artifact.
- `assessment-service`'s ATS scoring (ADR-014) is unchanged — it still scores structured
  content, deliberately, not the rendered PDF this ADR adds. Nothing about this ADR requires
  or implies revisiting that scope.
- A resume version generated before this feature existed has no `rendered_documents` row;
  `GET /api/documents/resume-versions/{id}` 404s for it, and the frontend shows "PDF
  unavailable for this older generation" rather than an error — existing history keeps
  working, it simply offers to render a PDF on demand rather than already having one.

---

<a id="adr-019"></a>
# ADR-019

## Decision

`EMAIL_ONLY` generation is implemented as a **hybrid**: application-service deterministically
assembles the subject line and the greeting/closing/sign-off frame of the email body from
data already verified elsewhere (the application's denormalised job title/company, ADR-017;
the candidate's own stated name, profile-service) — never generated by the model — and asks
ai-service for exactly one thing: a short, grounded highlight paragraph connecting the
candidate's real evidence to the role. ai-service's `EmailContentService` mirrors
`CoverLetterContentService`'s pattern (schema validate → grounding validate → regenerate once
→ drop-and-report), reusing `GroundingValidator`'s 3-arg `validate(statements, evidence,
additionalContext)` overload unchanged so the job title and company may be named without a
citation, exactly as cover-letter generation already established.

## Problem

An application email needs to state three things with total certainty — the job title, the
company, and the candidate's own name — plus one thing that benefits from generation: a brief,
genuine reason the candidate is a fit. Treating the whole email as free LLM output risks the
model paraphrasing (or mis-stating) the job title/company it's given as context, exactly the
class of failure ADR-014/015/016/018 have each independently refused to risk elsewhere in this
codebase. Treating it as pure template text with no generation would satisfy "do not invent
user information" trivially but wouldn't be a *generated* email at all — nothing would need
evidence, an LLM, or grounding, and the feature would be indistinguishable from a form letter.

## Options

1. Generate the entire subject + body freely from the model, relying on grounding checks
   alone to catch a misstated job title or company.
2. Generate nothing; assemble the whole email deterministically from `Application` fields and
   profile data (mail-merge style).
3. Split responsibility: the model writes only the fact-dependent-but-genuinely-generative
   part (one grounded highlight), everything else — subject, greeting, closing, sign-off,
   signature — is assembled deterministically from data already known to be correct.

## Selected Option

Option 3.

## Reason

Option 1 makes the two facts most load-bearing for "is this even the right email" —
job title and company — dependent on the model restating them correctly every time, with no
deterministic fallback if it doesn't. Option 2 produces something too generic to satisfy
"appropriate for a job application" in any meaningful sense and wastes the one place
generation genuinely adds value: connecting the candidate's real background to this specific
role in natural language. Option 3 costs nothing extra — `GroundingValidator`'s 3-arg overload
and the schema/prompt/service/degrade-path structure already exist for cover-letter
generation (`CoverLetterContentService`, this same codebase) — and gives the strongest
guarantee available: the two facts that must never be wrong are never asked of the model at
all, while the one paragraph that is model-written is checked exactly the way every other
generated sentence in this product is checked.

## Impact

- `ai-service` gains `email-content.schema.json`, `prompts/email-content/v1.txt`,
  `EmailContentService` and `POST /internal/ai/email-content` — structurally near-identical to
  `CoverLetterContentService`, reduced to one body paragraph and one closing paragraph
  (an email is shorter than a letter) and single-shot (no separate evidence-selection stage;
  the model picks directly from the full evidence inventory, appropriate for one short
  paragraph). `GroundingValidator` itself is unchanged.
- `application-service` gains `EmailContent` (`careerforge_application.emails`, versioned and
  immutable per generation, mirroring `resume_versions`), `EmailGenerationService`, and
  `POST`/`GET /api/applications/{id}/email`. `Application.deriveStatus()` is generalised from
  its `RESUME_ONLY`-only form to a per-type switch (`RESUME_ONLY` needs the resume,
  `EMAIL_ONLY` needs the email, `COVER_LETTER_ONLY` needs the letter, `ALL` needs all three) —
  behaviourally identical to the old logic for `RESUME_ONLY`, and `ALL` remains unreachable to
  `COMPLETED` until every pipeline exists, per the earlier instruction not to implement it yet.
- Email generation requires the application to already have a confirmed job title
  (`Application.jobTitle() != null`) and the candidate to have at least one evidence item —
  the same "add evidence first" guard `resume-service` already enforces, reported with the
  same `VALIDATION_ERROR` shape.
- A paragraph the grounding degrade path removes (both attempts failed) falls back to one
  deterministic sentence built only from the application's own verified job title/company —
  the email is never left with a visible gap, and the fallback never states anything beyond
  what was already safe to state.
- Calling `POST .../email` again regenerates: a new `EmailContent` version is persisted and
  `Application.emailId` repoints at it, the same "call the generate endpoint again" pattern
  `resume-service` already uses — no new endpoint shape needed for "Regenerate".
- The frontend threads `?type=EMAIL_ONLY` through the existing wizard as a query param (the
  same survives-a-reload technique `TemplatePage` already uses for `templateId`), skips the
  resume-only template step, and lands on a new `/results/email/:applicationId` page. The
  `RESUME_ONLY` path through `OutputTypePage`/`JobDescriptionPage`/`ReviewPage`/
  `ProcessingPage` is unchanged in behaviour — every branch added defaults to it.
- `COVER_LETTER_ONLY` and `ALL` remain out of this change's scope, as instructed, though
  `EMAIL_ONLY`'s implementation now sits alongside cover-letter generation built the same
  session (both reuse the same `GroundingValidator` overload and `Application` status
  generalisation) rather than duplicating that infrastructure.

---

# ADR-020

## Decision

`COVER_LETTER_ONLY` generation is orchestrated from **application-service**, not
resume-service, calling ai-service's new `CoverLetterContentService` via a two-stage pipeline
(evidence selection, then content) that otherwise mirrors resume-service's own pipeline
exactly (ADR-013). `GroundingValidator` gains a second, 3-arg `validate(statements, evidence,
additionalContext)` overload so the confirmed job title and company may be named in the
letter without needing an evidence citation for them specifically — everything else a
statement asserts (employer, technology, metric, date) is still checked exactly as before.
Email generation (ADR-019, built the same session) reuses this overload unchanged.

## Problem

A cover letter is not a résumé and not an email: it needs the JD's actual *requirements* (to
write a letter that speaks to this specific role, not a generic one — unlike email's
single-shot, no-evidence-selection shape), but it also has no template, no ATS score, and no
document-service rendering of its own, so it does not obviously belong inside
resume-service's pipeline. It also legitimately needs to name the company and role it's
addressed to — content `GroundingValidator` would otherwise flag as an unsupported entity
`Kubernetes`-style, exactly the failure this system is built to catch when the entity is
*actually* invented, but wrong to catch when the entity is the real, user-confirmed target of
the letter.

## Options

1. A new `cover-letter-service` deployable, duplicating resume-service's Feign clients,
   evidence-selection call and persistence pattern for one endpoint.
2. Extend resume-service to also generate cover letters, since it already owns the
   evidence-selection-then-content pipeline shape and the relevant Feign clients.
3. Orchestrate from application-service — add its own `ProfileServiceClient` and
   `AiServiceClient` (evidence selection + cover-letter content), a new `CoverLetterVersion`
   collection, and `CoverLetterGenerationService` structured like `EmailGenerationService`
   (ADR-019): depends on `ApplicationService` for ownership and to record the resulting
   reference, rather than the reverse.
4. Widen `GroundingValidator` itself to special-case "the target job/company" as always
   allowed, without a caller-supplied allowlist.

## Selected Option

Option 3, plus the `GroundingValidator` overload from Option 4's problem statement but shaped
as an explicit parameter rather than a special case.

## Reason

Option 1 is a new service, a new Eureka registration, a new port, a new pom, a new Docker
Compose entry and a new CI job for logic that fits in a few hundred lines — disproportionate
for one generation type reusing infrastructure that mostly already exists. Option 2 makes
resume-service responsible for an artifact it has nothing else to do with (no template, no
document rendering, no ATS relationship) and risks exactly the kind of regression this task
was explicitly told to avoid — touching the resume pipeline to add something unrelated to it.
Option 3 costs one new domain type and one new service in application-service, which already
owns the `Application` aggregate `coverLetterVersionId` was reserved on (ADR-017) and is
where `docs/API_CATALOG.md`'s Milestone 8 contract already places
`POST /api/applications/{id}/cover-letter` — the public endpoint's home was decided before
this ADR, only its implementation was pending.

For the grounding question: a bare special case (Option 4) would hide "the model may name
the company without evidence" inside `GroundingValidator` itself, invisible to a caller that
didn't know to look — a resume-content call could silently start accepting arbitrary company
names if the profile happened to contain a company-shaped word. An explicit
`additionalContext` parameter keeps the exception visible at the call site: only cover-letter
(and now email) generation supplies it, and only with the two specific, already-verified
strings (job title, company) that justify it. Every other rule — numbers, dates, employers,
technologies, contact details — is completely unaffected; a regression test
(`GroundingValidatorTest$AdditionalContext`) asserts the allowlist widens proper-noun
matching only, not the numeric-claim check.

## Impact

- `ai-service` gains `cover-letter.schema.json`, `prompts/cover-letter/v1.txt`,
  `CoverLetterContentService` and `POST /internal/ai/cover-letter`. Output is `greeting`
  (plain string, never grounding-checked — boilerplate, not a factual claim),
  `openingParagraph`/`bodyParagraphs[]`/`closingParagraph` (each `{ text, evidenceIds }`,
  grounding-checked exactly like resume bullets), and `signOff` (plain string). A paragraph
  that fails grounding twice is dropped entirely (mirrors `ResumeContentService`'s summary
  removal) rather than shown unverified; the response reports which locations were removed.
- `GroundingValidator.validate` gains the 3-arg overload described above; the 2-arg form now
  delegates to it with an empty set, so every existing caller and test is unaffected. Writing
  the regression test for this overload also surfaced and fixed a real, pre-existing bug in
  `supportsNumber`: it stripped all non-digit characters from the *entire* cited-evidence blob
  before substring-matching a claimed number, so two unrelated numbers sitting near each other
  in the evidence (a metric, then a date a few words later) could concatenate into a substring
  that coincidentally matched a fabricated one — a real evidence fixture with `"...300ms
  2021-03 2024-01"` let a fabricated `"40%"` through undetected, because `"2024-01"` digit-
  stripped to `"...2401"`, which contains `"40"`. Fixed to compare per whitespace-delimited
  token instead of across the whole blob — `GroundingValidatorTest$Rejects.inventedMetric`
  now genuinely rejects it.
- `application-service` gains `ProfileServiceClient` (`GET /api/profile/evidence`),
  extends `JdServiceClient` with `GET /api/jd/{id}/analysis`, extends `AiServiceClient` with
  the evidence-selection and cover-letter calls, gains `CoverLetterVersion`
  (`careerforge_application.cover_letter_versions`, versioned and immutable per generation,
  mirroring `resume_versions`/`EmailContent`), `CoverLetterGenerationService`, and
  `POST`/`GET /api/applications/{id}/cover-letter`. `Application.attachCoverLetter(...)` sets
  the reference and recomputes status via the same per-type `deriveStatus()` switch ADR-019
  generalised — no further change needed there.
- Cover-letter generation requires the job description to be confirmed and analysed (reuses
  `JD_NOT_CONFIRMED` — the same conflict resume-service already surfaces) and the candidate to
  have at least one evidence item, the same guard resume-service and email generation both
  enforce.
- Calling `POST .../cover-letter` again regenerates: a new `CoverLetterVersion` is persisted
  and `Application.coverLetterVersionId` repoints at it — the same "call the generate endpoint
  again" pattern resume-service and email generation both use.
- **Unrelated fix discovered along the way:** `application-service`'s
  `spring-boot-starter-oauth2-client` dependency (added for a future Gmail-draft OAuth flow)
  transitively pulls in Spring Security's autoconfiguration, which — absent an explicit
  `SecurityFilterChain` bean — defaults every endpoint to requiring a generated-password HTTP
  Basic login. This silently 401'd every application-service endpoint, including the
  already-implemented `POST /api/applications` from ADR-017, once the dependency was added.
  Fixed with a `SecurityConfig` identical to auth-service's own (permit-all, stateless, no
  CSRF/basic/form-login — identity is the gateway-forwarded `X-User-Id` header, trusted the
  same way every other internal service trusts it).
- The frontend threads `?type=COVER_LETTER_ONLY` through the existing wizard exactly the way
  `?type=EMAIL_ONLY` already does (survives-a-reload query param, skips the template step),
  and lands on a new `/results/cover-letter/:applicationId` page. `RESUME_ONLY` and
  `EMAIL_ONLY` are unchanged.
- Out of scope, per the task: `EMAIL_ONLY`/`ALL` combined generation (`GenerationType.ALL`),
  templates, PDF/DOCX rendering, and any change to the resume pipeline.

---

# ADR-021

## Decision

Google OAuth (Authorization Code + PKCE, docs/EXTERNAL_APIS.md) binds its `state` to the
PKCE `code_verifier` in **Redis**, not an HTTP session — auth-service already ran fully
stateless (`SecurityConfig`: `SessionCreationPolicy.STATELESS`, no login form) before this
change, and stays that way. Account resolution on callback trusts a Google identity only
when the ID token's own `email_verified` claim is true; it then matches, in order, an
existing linked `oauth_accounts` row, then an existing password account with the same
verified email (auto-linked), then falls through to creating a new OAuth-only account
(`passwordHash: null`). Both new endpoints always end the browser navigation in a redirect —
never the platform's usual JSON error envelope — because Google delivers the callback as a
full page load a frontend `fetch` can't intercept.

## Problem

Three things the blueprint and docs/EXTERNAL_APIS.md specify don't have an obvious default
implementation:

1. **Where does `state` live between the authorize redirect and the callback?** The
   conventional answer — an `HttpSession` — requires sticky sessions or a shared session
   store the moment more than one auth-service instance is running, and conflicts with this
   service's existing deliberately-stateless design.
2. **What proves a Google identity is safe to attach to (or create) a CareerForge account?**
   Google's ID token asserts an email, but Google itself flags whether that email is
   verified — a claim from an identity provider that hasn't verified the mailbox is not
   meaningfully different from a client asserting its own email unchecked.
3. **What does the callback return?** Every other auth-service endpoint returns JSON
   (`AuthResponse` on success, the platform's standard error envelope on failure) — but this
   endpoint is reached by the browser navigating away from Google, not by a `fetch` call a
   frontend can catch and branch on.

## Options

For (1): (a) enable a session just for this flow; (b) sign the verifier into the `state`
value itself (e.g. as a JWT), avoiding server-side storage entirely; (c) store `state` →
`verifier` in Redis, already a dependency of this service (added ahead of use — see the
`application-nodocker.yml` comment it previously carried) but never yet wired to anything.

For (2): (a) match only on `oauth_accounts`, requiring a user to explicitly "link" Google
from a settings page before it can ever sign them in — no auto-linking; (b) match on email
regardless of Google's `email_verified` claim; (c) match on email, but only when Google
reports it verified.

For (3): (a) return the platform's normal JSON error envelope on failure, same as every
other endpoint; (b) always redirect to the frontend, success or failure, with a query-string
error code on failure.

## Selected Option

(1c), (2c), (3b).

## Reason

For (1): option (a) reintroduces server affinity this service was explicitly built without.
Option (b) avoids Redis but leaks the verifier's presence into a value that transits the
browser and Google's own redirect chain — putting the proof of possession in the same place
as the thing it's meant to protect is a weaker construction than keeping it server-side.
Redis is already provisioned and already the standard place this codebase puts short-lived,
single-use server state (see resume-service/api-gateway's own Redis usage), so (c) costs
nothing new to add and is strictly stronger.

For (2): option (a) is safer still but is a materially different, larger feature (a linked-
accounts settings UI) than "Google OAuth sign-in" as scoped by this task — deferred, not
rejected. Option (b) would let anyone who merely *claims* an email at Google (Google does
allow unverified test/GSuite-transitional addresses in some flows) sign into an existing
password account without ever proving they control it — a real account-takeover path. Option
(c) is the standard, safe middle ground every major "Sign in with Google" integration uses.

For (3): option (a) would show a bare JSON blob in the browser window after Google's
redirect — not a broken security posture, but a broken experience for something that's
supposed to hand control back to the SPA. Option (b) costs nothing structurally (the
controller already builds a `ResponseEntity` either way) and degrades honestly: the frontend
decides how `?error=google_oauth` is displayed, this service just never leaves the user
looking at raw JSON.

## Impact

- `auth-service` gains `oauth_accounts` and `security_events` (docs/DATABASE.md §3) — the
  latter's full documented event set is modelled (`SecurityEventType`), but only
  `OAUTH_LINKED` has a writer today; `LOGIN_SUCCESS`/`LOGIN_FAILURE`/`REFRESH_REUSE` are
  real, existing code paths (`AuthService.login`/`refresh`) not yet retrofitted to also write
  an event — tracked as remaining work, not silently dropped.
  `User` gains `markEmailVerified()` — set only from a Google-verified callback, never from
  a client-supplied flag.
- `AuthService` gains `issueSessionTokens(userId)`, a public seam that mints a fresh
  refresh-token family for an already-authenticated user — the same path `login` uses after
  a password check, reused by `GoogleOAuthService` after it verifies a Google identity
  instead. No change to `login`/`refresh`/`logout`'s own behaviour.
- Google's own access/refresh tokens are read once, during the code exchange, to obtain and
  verify the ID token, and are never persisted — matches docs/EXTERNAL_APIS.md exactly
  ("Google's access token is not stored for plain sign-in").
- `GET /api/auth/oauth2/authorize/google` and `.../callback/google` were already present in
  the API gateway's `public-paths` allowlist (added ahead of implementation) — no gateway
  change was needed.
- `FRONTEND_BASE_URL` is a new env var (default `http://localhost:5173`, matching the
  gateway's own CORS default) naming where the callback redirects; unrelated to and
  independent of `VITE_API_BASE_URL`.

---

# ADR-022

## Decision

"Generate All" (`GenerationType.ALL`) creates exactly **one** `Application` and generates its
three outputs — resume, cover letter, email — **sequentially**, each through the exact
generate/attach call the corresponding single-output flow already uses
(`POST /api/resumes/generate` + `POST /api/applications/{id}/resume`;
`POST /api/applications/{id}/cover-letter`; `POST /api/applications/{id}/email`). No new
generation pipeline was built. `EmailGenerationService`/`CoverLetterGenerationService`'s
existing generation-type guard is widened from "exactly `EMAIL_ONLY`" / "exactly
`COVER_LETTER_ONLY`" to "that type, or `ALL`" — the only change to either service's actual
generation logic.

A failure in one output is recorded against that output specifically
(`Application.resumeError`/`coverLetterError`/`emailError`, set via the new
`POST /api/applications/{id}/outputs/{output}/failed`) and never blocks, hides, or is
confused with the other two — `Application.status` keeps deriving exactly as it already did
(`Application.deriveStatus()`, unchanged) from which references are attached, not from
whether an error is recorded, so `COMPLETED` still requires all three and a failed output
stays independently retryable by calling that same output's own generate endpoint again.

## Problem

Three questions had no existing answer to reuse:

1. **How does one `Application` end up with three outputs when they're generated by two
   different pipelines in two different services** (resume-service's own `/generate`,
   orchestrated by the caller and attached by reference; application-service's own
   `EmailGenerationService`/`CoverLetterGenerationService`, which generate *and* attach in one
   call)? `EmailGenerationService`/`CoverLetterGenerationService` also each hard-rejected any
   `Application` whose `generationType` wasn't exactly their own single-output type — by
   design, until now, since no caller could reach them any other way.
2. **How does a partial failure survive a page refresh, with enough detail to say which
   output failed and why** — not just that *something* about the application is incomplete?
   `resumeVersionId`/`coverLetterVersionId`/`emailId` being `null` already distinguishes
   "not generated" from "generated," but resume generation's own failures happen entirely
   outside application-service (the call never reaches it), so nothing already recorded a
   reason anywhere the aggregate could report back.
3. **Can the three outputs be generated concurrently** to finish faster? `Application` uses
   Spring Data's optimistic locking (`@Version`), and every one of the three generate/attach
   calls does a read-modify-save on the *same* `Application` document.

## Options

For (1): (a) build a fourth, `ALL`-specific generation pipeline in application-service that
duplicates resume/cover-letter/email generation into one combined call; (b) keep the three
existing pipelines exactly as they are and just widen `EmailGenerationService`'s and
`CoverLetterGenerationService`'s own guard to also accept `ALL`, letting the caller (the
frontend's orchestration) invoke each one in turn against the same `applicationId`.

For (2): (a) treat `Application.failureCode` (a single field, already used by the
`PATCH .../status` transition) as good enough — the caller keeps its own record of which call
failed; (b) add three new nullable per-output error fields plus one new endpoint the caller
uses to record a failure against a specific output, regardless of which service's call
actually failed.

For (3): (a) run the three generate calls with `Promise.all`, accepting that a version
conflict occasionally forces a caller-side retry; (b) run them sequentially, in the order the
task's own flow describes (resume → cover letter → email).

## Selected Option

(1b), (2b), (3b).

## Reason

For (1): Option (a) is exactly the kind of duplicate implementation reuse was supposed to
prevent — resume-service's grounded generation and application-service's two AI pipelines
already exist, are already tested, and already do the right thing; a fourth pipeline would
maintain the same logic twice for no behavioural difference. Option (b) is a two-line guard
change per service (verified by a new test in each) plus frontend orchestration — the
minimum edit that makes the existing, working pipelines reachable for `ALL` too.

For (2): Option (a) fails the task's own requirement directly — "show exactly which output
failed + reason" needs a reason *per output*, and `failureCode` has no way to say "the email
failed for this reason but the cover letter is fine." Option (b) is the smallest schema
change that actually satisfies it, deliberately generic (`{output}` is a path segment, not
three near-duplicate endpoints) so it works uniformly whether the failing call was
resume-service's (which application-service never even sees) or application-service's own.
Recording a failure never touches `status`: forcing the whole `Application` to `FAILED` the
moment one of three outputs breaks would contradict "do NOT mark the Application fully
successful if any output fails" in the wrong direction — it would also stop the *other* two,
already-succeeding outputs from being representable as done.

For (3): given the `@Version` optimistic lock, option (a)'s occasional retry-on-conflict is a
self-inflicted failure mode with no upside here — the three calls are already fast (a handful
of Groq requests, single-digit seconds), so sequential execution costs little and removes an
entire class of spurious "generation failed" reports that would have nothing to do with the
actual generation logic. It also matches the task's own documented flow (resume → cover
letter → email) exactly.

## Impact

- `Application` gains `resumeError`/`coverLetterError`/`emailError` (cleared by the matching
  `attachResume`/`attachEmail`/`attachCoverLetter`) and `recordOutputFailure(output, reason)`.
  `ApplicationResponse` and `ApplicationSummaryResponse` both gained these three fields (plus,
  on the summary, the three reference ids) so the dashboard can render `ALL` rows' per-output
  status without an extra request per row — harmless `null`s for the other generation types,
  which only ever populate the one field relevant to them.
- `EmailGenerationService`/`CoverLetterGenerationService`'s private guard methods were
  renamed (`requireEmailOnly` → `requireEmailGenerationAllowed`, etc.) to stop describing a
  constraint they no longer enforce.
- New endpoint `POST /api/applications/{id}/outputs/{output}/failed` — the only new backend
  endpoint this feature needed.
- Frontend: `OutputTypePage`'s "Generate All" card is enabled; `ReviewPage`/`TemplatePage`
  carry `type=ALL` through the existing wizard steps (no new steps — `ALL` uses the same
  template step `RESUME_ONLY` does); `ProcessingPage` gains a `runAll()` that creates the
  `Application` then runs the three steps sequentially, each wrapped so one failing doesn't
  stop the next and is recorded via the new endpoint; a new `AllResultPage` (route
  `/results/all/:applicationId`) provides the Resume/Cover Letter/Email/ATS Analysis/JD Fit
  tabs and per-output retry, reusing the existing single-output result pages' display and
  download/copy logic rather than reimplementing it. `DashboardPage` gained a fourth section
  for `ALL` applications, unfiltered by status (unlike the existing cover-letter section)
  specifically so a partially-failed application is visible, not hidden.
- `RESUME_ONLY`/`EMAIL_ONLY`/`COVER_LETTER_ONLY` are unchanged: verified both by the existing
  test suites (all 43 application-service tests still pass unmodified) and live, against the
  real stack, that an `EMAIL_ONLY` application still generates an email and a
  `COVER_LETTER_ONLY` application is still correctly rejected from generating one.

---

<a id="adr-023"></a>
# ADR-023

## Decision

Custom templates (uploaded via `POST /api/resumes/templates/custom`, analyzed and stored by
document-service — see `CustomTemplateAssetService`) gain a second accepted format, **PDF**,
alongside the existing DOCX path, and become **selectable in the main `/generate` wizard**
(`TemplatePage.tsx`) exactly like a built-in template — not only through the separate,
standalone "Templates" page's "Use this template" action that was, until now, the only way to
render one.

Both changes reuse the existing architecture rather than adding to it:

1. **PDF is analyzed and merged by new classes that mirror the DOCX ones exactly**
   (`PdfStructureAnalyzer` mirrors `DocxStructureAnalyzer`; `PdfMailMerge` mirrors
   `DocxMailMerge`), sharing the same `{{token}}` placeholder convention, the same
   `CustomTemplateAsset`/`DetectedField`/`RenderedDocument` entities (extended with nullable
   format-specific fields, the same pattern `Template` already uses for
   BUILT_IN-vs-CUSTOM_UPLOAD-only fields), and the same `TemplateService`/`Template` catalogue
   on the resume-service side — untouched. No second template system was created.
2. **Making a custom template reachable from the main wizard required no new generation
   pipeline.** `POST /api/resumes/generate` already accepts and persists any `templateId` the
   caller is allowed to select — including a `CUSTOM_UPLOAD` one — because
   `TemplateService.resolveForGeneration` was never built to check `source` (discovered while
   inspecting resume-service before writing any code, per this feature's own inspection
   requirement). The only real gap was document-service's render endpoint
   (`POST /api/documents/resume-versions/{id}/render`), which unconditionally resolved
   `templateId` through `ResumeTemplate.fromId` — the *built-in* enum — and would reject any
   custom id. `DocumentRenderService.renderPdf` now checks whether `templateId` names a
   `CustomTemplateAsset` the caller owns first, and if so delegates to
   `CustomTemplateAssetService.generate` (the same method the standalone Templates-page flow
   already calls) instead of the built-in Thymeleaf/PDF path. Every caller of that render
   endpoint — `ProcessingPage`'s eventual PDF fetch, `ResultPage`/`AllResultPage`'s "Download"
   button, Resume-only *and* Generate All — reaches this dispatch automatically, unchanged,
   because they already only deal in `resumeVersionId`/`templateId` and never assumed a
   built-in template. `Application`/`applicationId` tracking needed no changes at all: a
   custom-templated resume is still generated, attached and assessed through the exact same
   calls as a built-in one; only the later, on-demand render step differs.

## Problem

Three gaps, found by inspecting the existing implementation before writing anything (per this
feature's own "Important implementation rule"):

1. Custom-template upload validated and accepted only `.docx` (`CustomTemplateAssetService
   .validateUpload`, hard-checking the filename extension and the DOCX/ZIP `"PK"` signature) —
   the task requires PDF too, and a PDF is structurally nothing like a DOCX (no OOXML
   paragraphs/runs to walk; text position is fixed, not reflowable), so it cannot reuse
   `DocxStructureAnalyzer`/`DocxMailMerge` as-is.
2. `TemplatePage.tsx` — the template-selection step of the actual `/generate` wizard every
   generation flows through — still showed "Upload your own template… not built yet 🔒 Coming
   Soon", a leftover from ADR-016's original Phase 1 scoping that was never updated when
   custom-upload was later built as a *separate*, standalone feature (its own "Templates" page,
   reachable only by picking an *already-generated* resume and mail-merging it there, entirely
   outside the wizard, Application tracking, ATS/JD-fit and the dashboard). A user following
   the wizard has never been able to reach their own uploaded template at all.
3. A PDF's fixed layout means inserted content can overflow a placeholder's space in a way DOCX
   (which reflows) structurally cannot — the task requires detecting this and either condensing
   or failing cleanly, never silently overlapping/clipping text.

## Options

For PDF analysis/merge: (a) render the PDF to an image and composite text on top (loses
selectable/searchable text, a real regression for an ATS-relevant document); (b) convert the
PDF to DOCX first and reuse the DOCX pipeline unmodified (no reliable PDF→DOCX converter exists
in this stack, and the conversion itself would be exactly the kind of "redesign the template"
the task explicitly forbids); (c) parse and rewrite the PDF's own content streams directly with
PDFBox — locate each `{{token}}`'s exact glyph positions/font/size, redact that region, draw the
resolved value in the same font at the same origin, leaving every other byte of the page
(images, lines, logos, other text) untouched.

For wizard integration: (a) build a new, wizard-specific "custom template" step distinct from
`TemplatePage.tsx`; (b) extend `TemplatePage.tsx`'s existing template grid (which already calls
`listTemplates`, and `TemplateService.list` already mixes in the caller's own `CUSTOM_UPLOAD`
rows) to show custom templates as selectable cards too, and replace the dead "Coming Soon" stub
with a working upload entry point reusing the existing `UploadTemplateWizard`.

## Selected Option

(c) for PDF; (b) for wizard integration.

## Reason

PDFBox (already a transitive dependency via `openhtmltopdf-pdfbox`, now declared directly)
supports exactly this: `PDFTextStripper` reports each string's `TextPosition`s (page, x/y
baseline, width, the actual live `PDFont` and size), and `PDPageContentStream` in append mode
can draw new content into an already-loaded page without touching anything else on it. This is
the PDF-native equivalent of what `DocxMailMerge` already does for DOCX — replace only the
placeholder, reusing its own exact formatting, leave everything else byte-for-byte alone —
rather than a different, weaker strategy. Redetection happens fresh against the live document
being merged (mirroring `DocxMailMerge`'s own re-walk of the freshly-loaded package) rather than
reusing coordinates captured at upload time, so the merge is never working from stale geometry.
If zero placeholders are found at upload, the file is rejected immediately (`FILE_REJECTED`,
same as a corrupt DOCX) — never silently accepted as a template nothing can actually fill in.

For wizard integration, Option (b) is the smaller change by a wide margin and fixes the actual
defect (the wizard's own stub lying about what the backend can do) rather than adding a second,
parallel way to reach the same capability. It also means Generate All gets custom-template
support for free: `ProcessingPage.runAll` already just passes whatever `templateId`
`TemplatePage` selected through to `generateResume`, identically for built-in and custom.

Content fit: rather than adding a second AI call purely to shorten text for layout reasons
(new cross-service coupling document-service has never had, for a narrow purpose), condensation
is deterministic — proportional truncation of the specific overflowing field's already-grounded
text, never inventing anything, exactly what "may condense/rephrase... must never add fabricated
information" permits without requiring it to come from a fresh model call. If a field still
doesn't fit after that, generation fails with `TEMPLATE_CONTENT_OVERFLOW` and the real reason
(which field, by how much) — no `RenderedDocument` is ever persisted for a layout that didn't
fit, matching every other failure in this codebase's rendering path (nothing partial is ever
saved).

## Impact

- **document-service**: new `pdf` package (`PdfStructureAnalyzer`, `PdfPlaceholderLocator`
  shared by analysis and merge, `PdfMailMerge`); `CustomTemplateAssetService` dispatches to the
  DOCX or PDF analyzer/merge by the asset's stored `format`; `CustomTemplateAsset` gains
  `format` (`DOCX`/`PDF`); `TemplateStructure`/`DetectedField` gain nullable PDF-specific fields
  (page count/dimensions in points, per-field page/bounding box/font size/color) alongside the
  existing DOCX-specific ones (twips), following the same "one shape, format-specific fields
  null when not applicable" convention `Template` already uses — no second structure model.
  `DocumentRenderService.renderPdf` gains the custom-template dispatch described above, and a
  new minimal `TemplateServiceClient` Feign call (`GET /api/resumes/templates/{id}`) to fetch
  the caller's saved field mapping when the main render endpoint — which, unlike the dedicated
  `/custom-templates/{id}/generate` endpoint, never received a mapping in its request body —
  needs one. New `ErrorCode.TEMPLATE_CONTENT_OVERFLOW` (422).
- **resume-service**: `CustomTemplateAssetDto`/`Template.forCustomUpload` pass through the
  asset's real `format` for `supportedFormats` instead of the previous hardcoded `List.of
  ("DOCX")`. No change to `TemplateService`/`TemplateController`'s selection/ownership logic —
  it already worked for any source.
  `.docx"` extension/signature gate moved from a single hard check to a format dispatch that
  also accepts `.pdf"` + the `%PDF-` signature; both still rejected the same way (`FILE_REJECTED`)
  for anything else.
- **frontend**: `TemplatePage.tsx` shows the caller's own custom templates (already returned by
  `listTemplates`) as selectable cards, plus a working "Upload a template" entry point (the
  existing `UploadTemplateWizard`, now accepting `.pdf` too); `StructureSummary.tsx` renders the
  PDF-shaped facts (page count/size, no twips) when given a PDF template. The standalone
  Templates page and its "Use this template" modal are unchanged — they remain a valid way to
  re-run a template against a resume generated earlier, alongside the new wizard path.
- Built-in templates, existing DOCX custom templates, Resume-only, Cover-Letter-only,
  Email-only and Generate All are all unchanged: `DocumentRenderService`'s dispatch only takes
  the custom-template branch when the id actually resolves to a `CustomTemplateAsset` the
  caller owns; every other `templateId` falls through to the exact `ResumeTemplate.fromId` path
  that already existed.

---

<a id="adr-024"></a>

# ADR-024

## Decision

A new interface, `ai.careerforge.ai.client.AiChatClient` (ai-service), sits between the five AI
generation call sites — `JdAnalysisService`, `EvidenceSelectionService`, `ResumeContentService`,
`CoverLetterContentService`, `EmailContentService` — and `GroqClient`. `GroqClient` now
`implements AiChatClient` and is, today, still the **only** implementation; Spring autowires it
into all five call sites automatically as the sole `@Component` of that type. Nothing about
Groq's request shape, prompts, model, retry/backoff, circuit breaker, timeout, or error
handling changed — this is a pure dependency-direction change (call sites now depend on an
interface instead of a concrete class), not a behavioural one. See
`docs/current-generation-workflow.md` §16 for the analysis that identified this as the minimum
structural change needed before a second provider (e.g. Gemini) could be added without touching
every call site again, and `docs/ai-abstraction.md` for the interface's full contract.

`AiController`'s own diagnostic `/internal/ai/status` endpoint — which reports whether *Groq
specifically* is configured/reachable, using `GroqProperties`' masked key/model/base-URL — keeps
its direct `GroqClient`/`GroqException` dependency unchanged. That endpoint's entire purpose is
Groq-specific connectivity diagnostics, not general AI generation, so routing it through the new
interface would have added indirection without removing any coupling that mattered.

A new, currently-inert configuration key, `ai.provider` (env `AI_PROVIDER`, default `groq`), was
added to `ai-service`'s `application.yml`. No code reads it yet — with exactly one `AiChatClient`
bean, Spring's autowiring already resolves the provider unambiguously. It exists only as a
documented, forward-looking config surface for when a second implementation needs to be selected
between; adding bean-selection logic ahead of that need was judged unnecessary abstraction.

## Problem

Every AI generation call site depended on `GroqClient` by concrete type. Adding a second
provider (e.g. Gemini) would have meant either (a) editing all five call sites again the moment
a second provider existed, or (b) growing `GroqClient` itself into a multi-provider client
internally, blurring the "the only class that talks to Groq" guarantee its own Javadoc makes.
Neither is necessary to prepare for a future provider — only a seam between the call sites and
the concrete client is.

## Options

(a) Do nothing yet — defer the abstraction until a second provider is actually being added, and
edit all five call sites then. (b) Introduce a full-featured `AiClient` abstraction now,
anticipating features no existing call site uses (per-call model override, per-call
temperature, tool/function calling, streaming). (c) Introduce the narrowest interface that
exactly matches `GroqClient#complete`'s existing shape — system prompt, user content, operation
tag in; JSON content, model, token count out — and change only the five call sites' declared
dependency type from `GroqClient` to it.

## Selected Option

(c).

## Reason

Option (a) defers a change that is safe, small, and independently valuable (it is the entire
content of this ADR) for no benefit — doing it now, decoupled from any actual provider work,
keeps the eventual Gemini change smaller and lets this step be verified in isolation against the
full existing test suite before any second provider exists to complicate that verification.
Option (b) would add parameters (temperature, per-call model, schema, streaming) that no
existing call site uses — every one of those is uniform, implementation-level configuration
today (`GroqProperties`), not something any call site varies — so adding them now would be
speculative surface area with nothing to exercise it, contradicting the task's own "avoid
creating unnecessary abstractions" instruction. Option (c) is the smallest change that achieves
the actual goal (no call site depends on `GroqClient` by concrete type) while leaving every
existing behavioural guarantee — retry policy, circuit breaker, logging discipline, error
type — exactly where it already lived, inside `GroqClient` itself.

## Impact

- **New file**: `ai.careerforge.ai.client.AiChatClient` — interface with one method
  (`complete(systemPrompt, userContent, operation)`) and one nested record (`AiChatResult`,
  field-for-field identical to `GroqClient`'s former, now-removed `GroqResult`).
- **`GroqClient`**: `implements AiChatClient`; its `complete` method now returns
  `AiChatResult` instead of the removed `GroqResult`. No other change — same `WebClient`, same
  retry/backoff/circuit-breaker config, same `GroqException` on failure, same logging.
- **`JdAnalysisService`, `EvidenceSelectionService`, `ResumeContentService`,
  `CoverLetterContentService`, `EmailContentService`**: constructor parameter and field type
  changed from `GroqClient` to `AiChatClient` (field renamed `groqClient` → `aiChatClient` for
  clarity); local variable types changed from `GroqClient.GroqResult` to
  `AiChatClient.AiChatResult`. No change to prompt construction, schema validation, grounding
  validation, regeneration, or surgical-removal logic in any of the five.
- **`AiController`**: unchanged — still depends on `GroqClient`/`GroqException` directly for its
  Groq-specific `/status` diagnostic, by design (see Decision).
- **Config**: new `ai.provider` key (`ai-service/application.yml`, env `AI_PROVIDER`, default
  `groq`) and matching `.env.example` entry — both documented as currently unread by any code.
- **No Gemini code, dependency, or configuration was added.** No prompt, schema, model,
  temperature, timeout, or retry value changed. No API contract (`/internal/ai/*` request/
  response shapes) changed. No document-generation, PDF/DOCX rendering, or template code was
  touched. Verified via a full reactor `mvn compile` (all 13 services + `platform-common`
  compile cleanly) and `ai-service`'s full unit test suite (56/56 passing, unchanged assertions).

---

<a id="adr-025"></a>

# ADR-025

## Decision

Google Gemini is added as a second, real `AiChatClient` implementation —
`ai.careerforge.ai.client.GeminiClient`, using Google's official Gen AI Java SDK
(`com.google.genai:google-genai`, version 1.66.0 — current GA release per Maven Central at the
time of this change; reached General Availability May 2025, supersedes the deprecated
`google-generativeai`/standalone Vertex AI client libraries). It talks to the **Gemini
Developer API** by API key only — never Vertex AI, never a service-account credential —
matching exactly how `GroqClient` talks to Groq: one key, one client, no additional cloud
project/credential plumbing.

`GroqClient` and `GeminiClient` are both now gated by `@ConditionalOnProperty` on the same
`ai.provider` key ADR-024 introduced (previously unread by any code):

```yaml
ai:
  provider: ${AI_PROVIDER:groq}   # "groq" (default) or "gemini"
```

`GroqClient` matches `havingValue = "groq", matchIfMissing = true` — created whenever the
property is unset or `"groq"`. `GeminiClient` matches `havingValue = "gemini"` — created only
when the property is exactly `"gemini"`. Exactly one of the two is ever a bean in the context at
a time, so the five business call sites' unqualified `AiChatClient` injection stays
unambiguous whichever provider is active. **No business call site was edited** — the same five
classes from ADR-024 (`JdAnalysisService`, `EvidenceSelectionService`, `ResumeContentService`,
`CoverLetterContentService`, `EmailContentService`) still depend only on the interface.

Structured JSON output uses Gemini's real structured-output mechanism
(`responseMimeType=application/json`, plus `responseJsonSchema` when a matching
`schemas/<operation>.schema.json` classpath resource exists — every existing operation tag
already equals its schema's base name) rather than a prose "please return JSON" instruction —
required by this feature's own task 5. This is a best-effort generation aid only;
`AiGenerationSupport.validateSchema` remains the authoritative, unchanged, provider-agnostic
validation gate applied after `complete()` returns, exactly as before.

## Problem

ADR-024 built the seam (`AiChatClient`) but deliberately stopped there — no second
implementation, and `ai.provider` was documented as inert. Three things had no existing answer:

1. **How does a second implementation actually get selected without breaking every existing
   deployment?** `GroqClient` was, and had to remain, the sole `@Component` of type
   `AiChatClient` for anyone not explicitly opting into Gemini — simply adding `GeminiClient` as
   a second unconditional `@Component` would make all five call sites' unqualified
   `AiChatClient` constructor injection ambiguous, failing application startup for every
   existing deployment the moment this feature merged, Gemini key or not.
2. **Can `ai-service` boot without `GEMINI_API_KEY` set?** `GroqProperties` hard-fails
   (`IllegalStateException`) at bean-creation time when `GROQ_API_KEY` is blank, because Groq is
   mandatory. Gemini is optional/inactive by default — the equivalent hard-fail for
   `GeminiProperties` would have required `GEMINI_API_KEY` on every deployment that only ever
   configured Groq, breaking all of them.
3. **How does Gemini's structured-output support get exercised for real** (task requirement:
   not just a prompt instruction), given the interface itself deliberately carries no schema
   parameter (ADR-024's own "deliberately excluded" list)?

## Options

For (1): (a) an explicit `@Configuration` class with a `@Bean` method per provider, chosen via
an `if`/`switch` on an injected `Environment`/property; (b) `@ConditionalOnProperty` directly on
each `@Component`, one pinned to each accepted value, matching against `ai.provider`.

For (2): (a) mirror `GroqProperties` exactly — hard-fail on a blank key; (b) let
`GeminiProperties` bind successfully with a blank key, and have `GeminiClient.complete()`
report the problem clearly on first use instead.

For (3): (a) hand-build a `com.google.genai.types.Schema` object field-by-field from each JSON
Schema file — the SDK's original, more restrictive typed structured-output field; (b) use the
SDK's newer `responseJsonSchema` field, which accepts arbitrary JSON Schema (a plain
`Map<String,Object>`) directly — "an alternative to `responseSchema` that accepts JSON Schema...
if `responseSchema` doesn't process your schema correctly, try `responseJsonSchema` instead"
(SDK's own field Javadoc) — loading and passing the *existing* `.schema.json` resource
unmodified.

## Selected Option

(1b), (2b), (3b).

## Reason

For (1): Option (a) works but requires a `@Configuration` class that knows about every provider
and re-implements the exact selection logic `@ConditionalOnProperty` already provides as a
one-line annotation per class; option (b) is Spring Boot's own idiomatic mechanism for exactly
this ("pick one of several conditional components by a property value"), needs no new
configuration class, and keeps each client's activation condition next to the client itself.
Verified, not just asserted: `AiProviderWiringTest` (`ApplicationContextRunner`) proves all
three cases — default/explicit-`groq` → only `GroqClient`; `gemini` → only `GeminiClient`; an
unrecognised value → neither (a deliberately strict failure over a silent fallback, so a typo'd
`ai.provider` value fails loudly rather than quietly keeping the old provider or guessing).

For (2): Option (a) directly breaks every deployment that only configures Groq the moment
`GeminiProperties` exists, regardless of whether `ai.provider=gemini` was ever set — completely
disproportionate for a provider nobody selected. Option (b) confines the failure to exactly the
people who opted in and then also forgot the key, and fails at the first real attempt to use it
rather than silently at boot for everyone else.

For (3): Option (a) means hand-translating five JSON Schema files into a different, more
restrictive schema representation and keeping the two in sync by hand forever — the schema
`GeminiClient` would guide generation with could quietly drift from the schema
`AiGenerationSupport.validateSchema` actually validates against. Option (b) reuses the *same*
file both places, guaranteeing they can never drift, and is deliberately best-effort: a missing
or unparseable resource (e.g. a future operation with no `.schema.json` counterpart) falls back
to `responseMimeType=application/json` alone — the same unconstrained guarantee `GroqClient`
already provides today — never blocking generation over a schema-attachment failure.

## Impact

- **New files**: `ai.careerforge.ai.client.GeminiClient` (the implementation),
  `ai.careerforge.ai.client.GeminiException` (mirrors `GroqException` field-for-field; not
  shared with it — `AiChatClient` mandates no common exception type, see ADR-024),
  `ai.careerforge.ai.config.GeminiProperties` (mirrors `GroqProperties`, except it binds
  successfully with a blank key — see Reason above).
- **`GroqClient`**: gained `@ConditionalOnProperty(prefix = "ai", name = "provider", havingValue
  = "groq", matchIfMissing = true)`. No other change — same request/response handling, retry,
  circuit breaker, logging as ADR-024 left it.
- **Dependency**: `com.google.genai:google-genai:1.66.0`, version-managed in the root `pom.xml`
  (`google-genai.version` property + `dependencyManagement`, matching every other third-party
  dependency's existing pattern), declared in `ai-service/pom.xml` only. Transitive additions
  (okhttp, google-auth-library, protobuf-java, guava 33.4.0-jre) are isolated to `GeminiClient`'s
  own SDK usage; `mvn dependency:tree` confirms no version conflict with Spring Boot's
  Jackson/Guava (Spring Boot's BOM-managed Jackson 2.18.3 wins by convergence).
- **Config**: `careerforge.gemini.*` block added to `ai-service/application.yml` (api-key,
  model, timeout-seconds, max-output-tokens, temperature, max-retries — same shape as
  `careerforge.groq.*`) and matching `GEMINI_*` entries in `.env.example`. `ai.provider`'s own
  comment updated from "reserved, currently unread" (ADR-024) to describe its now-real effect.
- **No business call site changed again.** `JdAnalysisService`, `EvidenceSelectionService`,
  `ResumeContentService`, `CoverLetterContentService`, `EmailContentService` are untouched since
  ADR-024 — they still depend only on `AiChatClient`; which implementation they get is resolved
  entirely by Spring's conditional wiring.
- **`AiController`**: unchanged — its `/status` diagnostic remains Groq-specific by design
  (ADR-024).
- **Tests**: `GeminiClientTest` (17 cases: successful response, structured-output schema
  attachment and its fallback, empty/truncated/blocked responses, 400/401/403/429 client errors,
  429-then-success, 5xx server errors, I/O/timeout failures, missing/null API key) — every
  scenario runs against a mocked `GenerateFunction` seam, never a real network call or SDK
  object, and retry-exhaustion scenarios use a no-op backoff so the suite stays fast.
  `GeminiPropertiesTest` (6 cases: blank/null key binds successfully, `hasApiKey()`,
  `maskedKey()`). `AiProviderWiringTest` (4 cases, `ApplicationContextRunner`: default → Groq
  only; explicit `groq` → Groq only; `gemini` → Gemini only; unrecognised value → neither) —
  this test caught a real bug during development (`GeminiClient` needed `@Autowired` on its
  production constructor once a second, test-only constructor existed, or Spring could not
  determine which to use) before it ever reached a real deployment.
- **Default provider is unchanged: Groq.** No workflow (resume, cover letter, email, JD
  analysis, evidence selection) is migrated to Gemini by this change. Setting
  `ai.provider=gemini` is a real, live switch for all five — not cosmetic — and requires
  `GEMINI_API_KEY`; it was not turned on anywhere. Verified via a full reactor `mvn compile`
  (all 13 services + `platform-common` compile cleanly) and `ai-service`'s full unit test suite
  (83/83 passing — the 56 from ADR-024 plus 27 new).

---

<a id="adr-026"></a>

# ADR-026

## Decision

`JdAnalysisService` — and only `JdAnalysisService` — is migrated from a direct `AiChatClient`
dependency to a new `ai.careerforge.ai.client.AiProviderRouter`, which routes
`AiOperation.JD_ANALYSIS` to Gemini as primary, automatically and transparently falling back to
Groq on any Gemini failure. The other four call sites (`EvidenceSelectionService`,
`ResumeContentService`, `CoverLetterContentService`, `EmailContentService`) are **not touched**
and keep injecting `AiChatClient` unqualified, which continues to resolve to `GroqClient` —
unambiguously, always, regardless of anything to do with Gemini.

This required superseding the coarse, all-or-nothing global switch ADR-025 introduced
(`ai.provider=groq|gemini`, gating which single `AiChatClient` bean existed at all) with a
finer-grained model: **both `GroqClient` and `GeminiClient` are now unconditionally beans, at
all times**, and `GroqClient` is `@Primary` (also unconditionally). This is what lets
`AiProviderRouter` always reach both providers for JD Analysis regardless of any global
setting, while the four untouched call sites' unqualified injection stays exactly as
deterministic as before — resolved via `@Primary` rather than "the only bean of that type
exists." `ai.provider` (env `AI_PROVIDER`) no longer selects which client beans exist; it is
retained as a config key but no longer read by `GroqClient`/`GeminiClient` themselves (see
Impact).

## Problem

Three things had no existing answer:

1. **ADR-025's global switch and this feature's per-operation requirement are structurally
   incompatible.** "Migrate ONLY JD Analysis... All other operations remain on Groq" cannot be
   satisfied by a bean-existence toggle that applies to all five operations uniformly — it needs
   both providers reachable at once, with routing decided per call, not per deployment.
2. **`AiProviderRouter`'s own dependencies need to be genuinely unit-testable.** Directly typing
   its constructor to the concrete `GeminiClient`/`GroqClient` classes (matching this feature's
   own literal naming) ran into a real environment constraint discovered while writing the
   tests: on this JDK, Mockito's inline mock maker (Byte Buddy) cannot mock concrete classes at
   all — only interfaces — a fact that surfaced for the first time in this codebase's test suite
   here, since every earlier test either mocked an interface or avoided mocking altogether.
3. **What happens to `AiController`'s existing failure-mapping** (`catch (GroqException ex)`,
   unchanged since ADR-024) once a JD analysis failure can now originate from either provider?

## Options

For (1): (a) extend the existing `@ConditionalOnProperty` model with a third value
(`ai.provider=mixed`) that somehow makes both beans exist only in that mode; (b) make both
`GroqClient` and `GeminiClient` unconditional beans always, with `GroqClient` marked
`@Primary` so unqualified injection is unaffected, and let a new, explicit router class own the
one case that needs both.

For (2): (a) keep `AiProviderRouter`'s constructor typed to the concrete classes and accept that
its own unit tests can't mock them directly — construct real instances backed by further
test doubles instead; (b) type `AiProviderRouter`'s constructor to `AiChatClient` (qualified by
Spring's default bean names for the two concrete classes) instead of the concrete types.

For (3): (a) add a new `catch` clause to `AiController.call()` for a possible combined/wrapped
exception type; (b) design the router so a total failure always surfaces as whatever the *final*
attempted provider threw, unchanged — meaning, since Groq is always the last attempt, always a
plain `GroqException` when both fail.

## Selected Option

(1b), (2b), (3b).

## Reason

For (1): Option (a) invents a third global mode whose actual effect ("both exist, but which is
primary?") would need the same `@Primary`-style resolution option (b) already provides directly,
for no benefit — the two options converge on the same runtime shape, but (a) adds an unused
enum-like value with no distinct behaviour of its own. Option (b) is the smallest change that
makes "always reachable for routing, but never ambiguous for unqualified injection" true, and it
is exactly the standard Spring idiom for "several candidates, one default" (`@Primary`) rather
than the standard idiom for "exactly one of several candidates should exist"
(`@ConditionalOnProperty`) — the right tool changed because the requirement changed.

For (2): Option (a) is technically possible (constructing real `GroqClient`/`GeminiClient`
instances with stubbed collaborators, mirroring `GeminiClientTest`'s own `GenerateFunction` seam)
but adds real friction for every future test of this router — needing a working `WebClient`/
`MeterRegistry`/`GroqProperties` trio just to exercise routing logic that has nothing to do with
either provider's transport details. Option (b) is also the more honest design: a *router*
routing between *providers* should depend on the provider abstraction, not two concrete
implementations of it — `AiChatClient` was built in ADR-024 precisely so call sites wouldn't need
to know which concrete class they were talking to, and `AiProviderRouter` is a call site of that
abstraction like any other. It also happens to sidestep the Byte-Buddy/JDK compatibility gap
entirely, since mocking an interface never touches the code path that gap affects — a real,
verified constraint of the environment this was built and tested in (Byte Buddy's own error
names the exact JDK-support gate it fails on), not a workaround-shaped guess.

For (3): Option (a) would mean `AiController` needs to know about `AiProviderRouter`'s internal
fan-out — new coupling for no behavioural gain. Option (b) requires no change to `AiController`
at all: `AiProviderRouter.complete()` always attempts Gemini first and Groq second for
`JD_ANALYSIS`; if Gemini fails, the failure is caught and logged internally, and only Groq's own
exception (if Groq also fails) is ever allowed to propagate — so the type reaching
`AiController.call()`'s existing `catch (GroqException ex)` is unchanged in every failure
combination. Verified by `AiProviderRouterTest.propagatesGroqsOwnFailureWhenBothProvidersFail`.

## Impact

- **New files**: `ai.careerforge.ai.client.AiOperation` (enum naming all five known operations,
  used today only to key `AiProviderRouter`), `ai.careerforge.ai.client.AiProviderRouter`
  (routes `JD_ANALYSIS` to Gemini-then-Groq-fallback; rejects every other operation with
  `IllegalArgumentException` — deliberately not a generic all-operations router yet, matching
  this task's explicit "ONLY JD Analysis" scope).
- **`GroqClient`**: `@ConditionalOnProperty` (ADR-025) replaced with `@Primary` — now an
  unconditional bean, always available both as the default `AiChatClient` for the four untouched
  call sites and as `AiProviderRouter`'s fallback.
- **`GeminiClient`**: `@ConditionalOnProperty` (ADR-025) removed entirely — now an unconditional
  bean too (never `@Primary`), so `AiProviderRouter` can always reach it. Safe unconditionally:
  constructing this bean with no `GEMINI_API_KEY` configured was already designed in ADR-025 to
  defer that failure to first use, not bean creation — the exact property that makes "Gemini
  unconditionally exists as a bean" safe for every deployment that has never configured a Gemini
  key at all.
- **`JdAnalysisService`**: constructor parameter changed from `AiChatClient` to
  `AiProviderRouter`; the single `.complete(...)` call site now passes `AiOperation.JD_ANALYSIS`
  through the router instead of calling an injected client directly. Prompt resolution
  (`PromptRegistry`/`AiGenerationSupport.resolvePrompt`), untrusted-content fencing, schema name
  (`jd-analysis.schema.json`), schema validation (`AiGenerationSupport.validateSchema`), and the
  `JdAnalysisResponse`/`Provenance` response shape are byte-for-byte unchanged.
- **Not changed at all**: `JdService` (caching via `jdAnalyses.findByJdVersionId(...)
  .orElseGet(...)`, confirmation-required guard, `FeignException`→`ApiException` mapping),
  `JdController`, the `POST /internal/ai/jd-analysis` request/response DTOs, the
  `jd-analysis.schema.json` file, the `prompts/jd-analysis/v1.txt` prompt, `AiController` (its
  `call()` wrapper needed no new catch clause — see Reason), any Resume/Cover Letter/Email
  service, document-service, or any custom-template code.
- **Config**: `ai.provider` (`AI_PROVIDER`) is retained in `application.yml`/`.env.example` but
  its comment is updated — it no longer selects which `AiChatClient` bean(s) exist; that's now
  `@Primary`/unconditional, per this ADR. Groq's and Gemini's own `careerforge.groq.*`/
  `careerforge.gemini.*` config blocks (api-key, model, timeout, tokens, temperature, retries)
  are unchanged and are what both clients actually use.
- **Tooling**: added `-Dnet.bytebuddy.experimental=true` to the root `pom.xml`'s
  `maven-surefire-plugin` configuration (`pluginManagement`, applies reactor-wide). Root cause:
  this environment's JDK is newer than the officially-validated version range of the Byte Buddy
  release Mockito's inline mock maker currently bundles, so mocking any *concrete* class failed
  outright (interfaces were unaffected) until this flag — named directly in Byte Buddy's own
  error message — was set. Fixes concrete-class mocking for the whole reactor going forward, not
  only this feature's new tests.
- **Tests**: `AiProviderRouterTest` (9 cases: JD analysis succeeds via Gemini without ever
  calling Groq; falls back to Groq on Gemini failure, including a missing-API-key failure
  specifically; never retries Gemini a second time after falling back; propagates Groq's own
  exception type when both fail; all four non-JD operations rejected with
  `IllegalArgumentException`, neither client ever called for them). `JdAnalysisServiceTest` (5
  cases, new — none existed before this ADR: succeeds via Gemini with the existing response
  shape intact; falls back to Groq transparently; untrusted-content fencing unchanged; both-fail
  propagates `GroqException` unchanged; invalid model output still fails via the existing
  `AiGenerationSupport.validateSchema` gate). `AiProviderWiringTest` rewritten for the new
  invariants (both clients always beans; Groq uniquely `@Primary`; unaffected by `ai.provider`;
  4 cases). `ai-service` full suite: 97/97 passing (83 from ADR-025 + 9 + 5, `AiProviderWiringTest`'s
  own 4 replaced in place rather than added). Full reactor `mvn compile`: BUILD SUCCESS.
  Additional regression run beyond compilation: `resume-service` (14/14), `application-service`
  (43/43), `jd-service` (5/5) unit test suites all pass unchanged — none of these modules
  reference any ai-service-internal class; their only contract with this change is the
  unmodified `POST /internal/ai/jd-analysis` HTTP request/response shape.

---

<a id="adr-027"></a>

# ADR-027

## Decision

`EvidenceSelectionService` — Stage 1 of both the resume and cover-letter generation pipelines
(matching JD requirements to candidate evidence, shared between `ResumeContentService`'s and
`CoverLetterContentService`'s own Stage 2 callers via resume-service and application-service
respectively) — is migrated from a direct `AiChatClient` dependency to `AiProviderRouter`,
exactly mirroring how ADR-026 migrated `JdAnalysisService`. `AiProviderRouter` is widened from
routing one operation to routing two: `AiOperation.JD_ANALYSIS` (ADR-026) and
`AiOperation.EVIDENCE_SELECTION` (this ADR), both to Gemini as primary with an automatic,
transparent Groq fallback on any failure — the identical contract, unchanged.

`ResumeContentService`, `CoverLetterContentService`, and `EmailContentService` — the three
content-generation stages that actually write the grounded prose a candidate sends to an
employer — are **not touched** and keep injecting `AiChatClient` unqualified, resolving to
`GroqClient` via its `@Primary` annotation (ADR-026), exactly as before this change.

## Problem

Two things had no existing answer:

1. **Was `AiProviderRouter`'s one-operation design (ADR-026) actually reusable, or did it need
   rebuilding to add a second operation?** The router was built with a single
   `if (operation != AiOperation.JD_ANALYSIS)` check rejecting everything else — the narrowest
   thing that satisfied "migrate ONLY JD Analysis" at the time, deliberately not a generic
   table, per that ADR's own stated reasoning.
2. **Does Evidence Selection have anything analogous to `GroundingValidator`/regeneration that
   also needed to move?** The task's own preservation checklist named
   "GroundingValidator, regeneration, surgical removal" — the exact pattern
   `ResumeContentService`/`CoverLetterContentService`/`EmailContentService` use, but
   `EvidenceSelectionService` was built differently from the start (see its own Javadoc,
   unchanged by ADR-024): a single schema-validated call, no grounding pass, no regenerate-on-
   failure loop, and its own equivalent of "surgical removal" is `stripUnknownEvidenceIds` —
   deleting citations to evidence ids that don't exist in the supplied inventory and downgrading
   the match to `NONE` when nothing survives. Confusing these two patterns and inventing a
   grounding/regeneration step that never existed here would have been a real behavioural
   change, not a preservation of one.

## Options

For (1): (a) keep the one-operation check and duplicate the routing method for a second
operation; (b) replace the single-operation check with a small `Set<AiOperation>` membership
test, leaving every other line of `complete()`/`completeWithGeminiPrimaryGroqFallback`
untouched, since both already took `operation`/`operationTag` as parameters rather than being
hardcoded to JD Analysis internally.

For (2): (a) treat the task's checklist literally and add a `GroundingValidator` call/regenerate
loop to `EvidenceSelectionService` that was never there before; (b) preserve the actual existing
behavior — `stripUnknownEvidenceIds` unchanged, no grounding pass, no regeneration — and
document explicitly that this stage never had what the checklist named, rather than silently
introducing new behavior under the banner of "preservation."

## Selected Option

(1b), (2b).

## Reason

For (1): Option (a) would have meant two near-identical private methods differing only in which
`AiOperation` constant they checked against — the router already took `operation` as a
parameter and forwarded `operation.operationTag()` generically; the *only* hardcoded thing was
the membership test itself. Option (b) is a one-line change (an `if` becomes a `Set.contains`)
that generalizes correctly for however many operations are ever added this way, without
building routing infrastructure (weighted routing, per-operation retry counts, etc.) nothing has
asked for yet — consistent with ADR-024/025/026's repeated "avoid unnecessary abstractions"
reasoning.

For (2): Option (a) fails the task's own actual goal — "preserve exactly" existing behavior —
by adding behavior that was never there, which is a functional change dressed as a
preservation. Option (b) is the only choice that is actually a pure provider-selection change,
verified by `EvidenceSelectionServiceTest.hallucinatedEvidenceIdsAreStillStrippedRegardlessOf
WhichProviderAnswered`, which exercises the real `stripUnknownEvidenceIds` logic against output
from each provider path and confirms it behaves identically either way.

## Impact

- **`AiProviderRouter`**: the `operation != AiOperation.JD_ANALYSIS` check replaced with
  `!ROUTED_OPERATIONS.contains(operation)`, where `ROUTED_OPERATIONS = Set.of(JD_ANALYSIS,
  EVIDENCE_SELECTION)`. No other line changed — same fallback contract, same exception
  propagation, same logging discipline.
- **`EvidenceSelectionService`**: constructor parameter changed from `AiChatClient` to
  `AiProviderRouter`; the single `.complete(...)` call site now passes
  `AiOperation.EVIDENCE_SELECTION` through the router. Prompt resolution, requirement/evidence
  fencing (`UntrustedContent.fence`), schema name (`evidence-selection.schema.json`), schema
  validation, `stripUnknownEvidenceIds`, and the `EvidenceSelectionResponse`/`Provenance`
  response shape are byte-for-byte unchanged.
- **Not changed at all**: `ResumeContentService`, `CoverLetterContentService`,
  `EmailContentService` (all three still inject `AiChatClient` unqualified → `GroqClient`),
  `AiController`, the `POST /internal/ai/evidence-selection` request/response DTOs, the
  `evidence-selection.schema.json` file, the `prompts/evidence-selection/v1.txt` prompt,
  document-service, or any custom-template code. Both of `EvidenceSelectionService`'s actual
  callers — `resume-service`'s `ResumeGenerationService` and `application-service`'s
  `CoverLetterGenerationService`, both reaching it via the unchanged `POST /internal/ai/
  evidence-selection` endpoint — see no difference in the contract at all.
- **Tests**: `AiProviderRouterTest` restructured — the five routing/fallback scenarios that were
  JD-Analysis-specific are now `@ParameterizedTest`s over `{JD_ANALYSIS, EVIDENCE_SELECTION}`
  (`RoutedOperations`, 10 cases total) rather than duplicated per operation, so the two
  operations' coverage cannot drift apart; `UnroutedOperations` now covers the three remaining
  operations (`RESUME_CONTENT`, `COVER_LETTER_CONTENT`, `EMAIL_CONTENT`), no longer
  `EVIDENCE_SELECTION`. New `EvidenceSelectionServiceTest` (6 cases): succeeds via Gemini with
  the existing response shape intact; falls back to Groq transparently; hallucinated-evidence-id
  stripping still works regardless of which provider answered; requirement/evidence text still
  fenced as untrusted content; both-providers-fail propagates `GroqException` unchanged; invalid
  model output still fails via the existing `AiGenerationSupport.validateSchema` gate.
  `ai-service` full suite: 107/107 passing (97 from ADR-026 + 10). Full reactor `mvn compile`:
  BUILD SUCCESS. Regression run: `resume-service` (14/14), `application-service` (43/43),
  `jd-service` (5/5) unit test suites all pass unchanged.

---

<a id="adr-028"></a>

# ADR-028

## Decision

Two independent changes, delivered together because the second was requested to build on the
first:

**Part A — AI.** `CoverLetterContentService` is migrated from a direct `AiChatClient`
dependency to `AiProviderRouter`, exactly like `JdAnalysisService` (ADR-026) and
`EvidenceSelectionService` (ADR-027) before it — `ROUTED_OPERATIONS` widens to
`{JD_ANALYSIS, EVIDENCE_SELECTION, COVER_LETTER_CONTENT}`. Both the first generation attempt and
the (at most one) regeneration attempt route through Gemini-primary/Groq-fallback independently;
a Gemini failure on either call falls back to Groq for that call only, never the other. Nothing
about the prompt, schema, grounding validation, regenerate-once-then-strip failure policy, or
persisted shape changed — only which provider serves each of the two Groq/Gemini calls inside
`generate()`.

**Part B/C — Document rendering.** document-service gains a complete second rendering pipeline
for cover letters, structurally parallel to the existing resume one at every layer:

| Resume (existing, unchanged) | Cover letter (new) |
|---|---|
| `ResumeTemplate` (enum, 3 built-in ids) | `CoverLetterTemplate` (enum, same 3 ids) |
| `ResumeRenderModel` / `ResumeRenderModelBuilder` | `CoverLetterRenderModel` / `CoverLetterRenderModelBuilder` |
| `templates/<id>/v1/resume.html` | `templates/<id>/v1/cover-letter.html` |
| `PdfRenderer.render(ResumeTemplate, ResumeRenderModel)` | `PdfRenderer.renderCoverLetter(CoverLetterTemplate, CoverLetterRenderModel)` — same class, same `htmlToPdf`/`countPages` |
| `DocumentRenderService.renderPdf` | `DocumentRenderService.renderCoverLetterPdf` — same class, same idempotent-replace logic |
| `CustomTemplateAssetService.generate` | `CustomTemplateAssetService.generateForCoverLetter` — same class, same `DocxMailMerge`/`PdfMailMerge` engines |
| `RenderedDocument.resumeVersionId` | `RenderedDocument.coverLetterVersionId` (new nullable field, same collection) |
| `DocumentType.RESUME` | `DocumentType.COVER_LETTER` (new enum value, same enum) |
| `POST /api/documents/resume-versions/{id}/render` | `POST /api/documents/cover-letter-versions/{id}/render` |

No new PDF/DOCX engine was introduced — every byte of every cover-letter document is still
produced by Thymeleaf+jsoup+openhtmltopdf (built-in) or docx4j/PDFBox (custom templates), the
exact libraries the resume pipeline already used. Gemini/Groq are never involved in rendering;
`DocumentRenderService`/`CustomTemplateAssetService` have no dependency on ai-service of any
kind, so a document-only re-render or retry is architecturally incapable of re-invoking either
provider — not merely a convention this code happens to follow.

## Problem

Four things had no existing answer:

1. **Cover letters were persisted as JSON only.** `docs/current-generation-workflow.md` (the
   pre-abstraction baseline analysis) documented this explicitly: "no PDF/DOCX render path
   exists in document-service for this document type." A user could generate a grounded cover
   letter but never download it as a real document.
2. **document-service had no way to reach a specific `CoverLetterVersion`.** Unlike
   resume-service's `GET /api/resumes/{id}` (fetches by the resume version's own immutable id),
   application-service only exposed `GET /api/applications/{id}/cover-letter` — scoped to an
   *application*, returning whichever version is latest right now. Rendering (and, critically,
   *re-rendering* a specific past version) needs a version-scoped lookup, the same shape
   resume-service already provides.
3. **`RenderedDocument`'s constructor and `DocumentType` enum were resume-only** by construction
   (`DocumentType`'s own comment: "cover-letter rendering... is out of scope here"), and every
   render/mail-merge method was typed directly to `ResumeTemplate`/`ResumeRenderModel`/
   `resumeVersionId`.
4. **What does "retry without re-invoking Gemini" actually require?** The task named this as an
   explicit requirement (Part C), not just an implied one.

## Options

For Part A: identical reasoning to ADR-026/027 — widen `AiProviderRouter`'s routed-operation set
by one, rather than building a separate routing mechanism for content-generation operations.
Already established, not re-litigated here.

For Part B, the render orchestration: (a) generalize the *existing* resume methods/classes to
accept either content type via a shared interface or a type parameter; (b) add a fully parallel
set of cover-letter-specific classes/methods alongside the untouched resume ones, sharing only
the primitives that were already content-agnostic (`DocxMailMerge`, `PdfMailMerge`, `PdfRenderer`'s
`htmlToPdf`/`countPages`).

For the version-lookup gap: (a) have document-service call
`GET /api/applications/{id}/cover-letter` and accept that it can only ever render the *latest*
version, never a specific historical one; (b) add a new, minimal, version-scoped endpoint to
application-service mirroring resume-service's existing shape exactly.

## Selected Option

(b) for both.

## Reason

For Part B: option (a) — generalizing the existing resume classes — was rejected because it
directly contradicts this task's own explicit constraint, "DO NOT modify existing Resume
rendering behavior." Introducing a generic interface/type-parameter into `ResumeRenderModelBuilder`,
`PdfRenderer.render`, or `DocumentRenderService.renderPdf` is a *shape* change to code the task
required to stay untouched, even if the intent was behavior-preserving — a refactor of working,
tested code carries real regression risk for a request that never asked for it. Option (b) adds
exactly one new file or one new method per existing resume counterpart, touches the resume path
only where a shared, already-content-agnostic primitive is reused (`DocxMailMerge`, `PdfMailMerge`,
`PdfRenderer`'s two private helpers), and is what let every existing resume test in the module
keep passing unmodified (only their constructor call sites needed a new `null` argument for the
one shared domain class, `RenderedDocument` — a mechanical, additive change, not a behavioural
one).

For the version-lookup gap: option (a) would make the whole feature strictly worse than the
resume pipeline it's supposed to mirror — resume-service's own render endpoint already supports
re-rendering *any* stored `ResumeVersion` with a different template; limiting cover letters to
"only ever the latest version" would be a silent capability gap baked into the very shape this
ADR is supposed to make parallel. Option (b) — `GET /api/applications/cover-letter-versions/{id}`,
returning the exact same `CoverLetterVersionResponse` shape `GET /{id}/cover-letter` already
returns — costs one controller method and one new `CoverLetterGenerationService.requireOwned`
method (the ownership-scoped `versions.findByIdAndUserId` lookup already existed at the
repository layer, unused until now), and gives cover letters the identical versioned-rendering
capability resumes already have.

## Impact

**ai-service** (Part A): `AiProviderRouter.ROUTED_OPERATIONS` gains `COVER_LETTER_CONTENT`.
`CoverLetterContentService` constructor parameter `AiChatClient` → `AiProviderRouter`; both
`.complete(...)` call sites (first attempt, regeneration attempt) now go through
`router.complete(AiOperation.COVER_LETTER_CONTENT, ...)`. No other line in that class changed.

**application-service**: `CoverLetterGenerationService.requireOwned(userId, coverLetterVersionId)`
(new — thin wrapper over the pre-existing `CoverLetterVersionRepository.findByIdAndUserId`).
`ApplicationController` gains `GET /api/applications/cover-letter-versions/{id}` (internal,
Feign-reachable — not a route the frontend has a reason to call, `GET /{id}/cover-letter`
already covers every real frontend need).

**document-service**:
- New: `client/ApplicationServiceClient` (Feign), `client/ClientDtos.CoverLetterVersionDto`,
  `render/CoverLetterRenderModel`, `render/CoverLetterRenderModelBuilder`,
  `template/CoverLetterTemplate`, three `templates/<id>/v1/cover-letter.html` Thymeleaf assets
  (one per existing built-in id, styled to match that id's resume template), `api/dto/
  DocumentResponses.CoverLetterRenderedDocumentResponse`.
- Changed, additively: `domain/DocumentType` gains `COVER_LETTER`; `domain/RenderedDocument`
  gains a nullable `coverLetterVersionId` field and constructor parameter (all five existing
  call sites — two in main code, three in tests — updated to pass `null` there, since every one
  of them still only ever produces `RESUME`-typed rows); `repository/RenderedDocumentRepository`
  gains `findByCoverLetterVersionIdAndFormatAndUserId`; `render/PdfRenderer` gains
  `renderCoverLetter(...)` (new public method, reuses the two existing private helpers
  unchanged); `service/DocumentRenderService` gains `renderCoverLetterPdf`/
  `requireExistingCoverLetter`/`fetchOwnedCoverLetter`, and its constructor gains
  `ApplicationServiceClient`/`CoverLetterRenderModelBuilder` parameters; `service/
  CustomTemplateAssetService` gains `generateForCoverLetter`/`resolveCoverLetterValues`/
  `resolveCoverLetterField`/`fetchOwnedCoverLetter`, and its constructor gains the same two new
  parameters; `docx/ProfileFieldCatalog` gains seven cover-letter-content field keys
  (`JOB_TITLE`, `COMPANY`, `GREETING`, `OPENING_PARAGRAPH`, `BODY_PARAGRAPHS`,
  `CLOSING_PARAGRAPH`, `SIGN_OFF`) alongside the existing profile-derived ones — a custom
  template asset has no fixed resume-or-cover-letter identity of its own (only which
  `generate*` method is invoked decides which half of the catalogue is ever resolved for it);
  `api/DocumentController` gains `POST /api/documents/cover-letter-versions/{id}/render` and
  `GET /api/documents/cover-letter-versions/{id}`; the existing `GET /{id}/download` endpoint's
  filename now reads `document.documentType()` to name the file `cover-letter.pdf`/`.docx`
  instead of always `resume.*` — the one small behavioural change to a pre-existing endpoint,
  necessary for correctness (a downloaded cover letter must not be named "resume.pdf") and
  verified to leave every existing resume-download test passing unchanged (a `RESUME`-typed row
  still produces `resume.pdf`/`resume.docx` exactly as before).
- **Unrelated pre-existing test fix**: `CustomTemplateAssetServiceTest.aPdfWithNoPlaceholdersIsRejected`
  asserted the *old*, already-superseded rejection behavior for zero-placeholder PDF uploads
  (the actual behavior — acceptance — was already correct in production code, established
  earlier this session and confirmed by `PdfStructureAnalyzerTest`; only this one test method
  had never been updated to match). Renamed to `aPdfWithNoPlaceholdersIsAccepted` and rewritten
  to assert the actual, intended, already-shipped behavior. No production code changed by this
  fix — it was blocking a clean full-suite run for reasons unrelated to this ADR.

**Tests**: `ai-service` — `AiProviderRouterTest`'s parameterized cases extended to
`COVER_LETTER_CONTENT` (removed from `UnroutedOperations`); `CoverLetterContentServiceTest`
gains a `Routing` nested class (4 cases: succeeds via Gemini, falls back to Groq, both-fail
propagates `GroqException`, a regeneration attempt is also routed) — 115/115 passing (107 from
ADR-027 + 4 new + 4 more from the widened parameterization). `document-service` —
`CoverLetterRenderModelBuilderTest` (new, 3 cases), `PdfRendererTest` (+2 cases, real PDF text
extraction across all three cover-letter templates), `DocumentRenderServiceCoverLetterTest`
(new, 6 cases: 404-not-403, default/explicit built-in template, idempotent re-render, Part C
retry-never-touches-AI, custom-template dispatch), `CustomTemplateAssetServiceTest` (+2 cases:
real DOCX and PDF mail-merge, output verified by parsing the actual merged bytes, not just
"didn't throw") — 51/51 passing (38 baseline, including the one corrected pre-existing test, +13
new). `application-service` — `CoverLetterGenerationServiceTest` gains a `RequireOwned` nested
class (2 cases) — 45/45 passing. Full reactor `mvn compile`: BUILD SUCCESS. Regression:
`resume-service` (14/14), `jd-service` (5/5) unit suites pass unchanged.

**Explicitly not done**: no new PDF/DOCX rendering engine; no change to Resume Content, Email
Content, or their generation/grounding logic; no change to `ResumeTemplate`/`ResumeRenderModel`/
`ResumeRenderModelBuilder`/`CustomTemplateAssetService.generate`'s own behavior; no frontend
wiring (a cover-letter download button, template picker reuse) — this ADR delivers the backend
capability the task's acceptance criteria describe ("Gemini → validated JSON → persisted version
→ existing template system → PDF/DOCX"), verified via unit tests exercising the real rendering
engines against real bytes; a frontend surface for it is a natural, separate follow-on.

---

# ADR-029

## Decision

A custom PDF template upload keeps the existing, unchanged deterministic path first: PDFBox
text-extraction → `PdfStructureAnalyzer` scans the live document for literal `{{token}}`
placeholder text. Only when that scan finds **zero** fields — the template has no detectable
placeholder text at all, e.g. a visually-laid-out design with blank regions instead of literal
tokens — does `CustomTemplateAssetService` fall back to a new, additional analysis: it sends the
raw PDF bytes to ai-service's new `POST /internal/ai/pdf-template-analysis` endpoint, which asks
Gemini (multimodal — PDF bytes + a text instruction, via a new `GeminiClient.analyzeDocument`
method) to identify likely field regions as fractional (0.0–1.0) bounding boxes, headings, and a
best-guess field-vocabulary label per region. document-service treats that response as
**untrusted data**: a new `GeminiPdfFieldConverter` validates every field's token pattern, page
number (against the real page count), and all four fractions (against the real page's own
width/height in points) before converting fraction → absolute PDF points and rejecting anything
that falls outside real page bounds. Only fields that survive validation are persisted, tagged
with a new `TemplateAnalysisSource.GEMINI_ASSISTED` marker on `CustomTemplateAsset` (vs.
`DETERMINISTIC` for the pre-existing scan). If Gemini is unreachable, errors, or every field it
proposed fails validation, the asset is still saved — with an empty field list and
`DETERMINISTIC` source, exactly the same, already-accepted outcome as a template that genuinely
has nothing detectable in it (ADR-023 already established this as acceptable, not a rejection
reason).

Rendering never changes: `PdfMailMerge` still produces every byte via deterministic PDFBox
redraw. What's new is *where it gets its field coordinates from* — the pre-existing `merge()`
still re-scans the live document's text for literal placeholder tokens (the only thing that can
ever work for a `DETERMINISTIC` asset); a new sibling method, `mergeAtKnownLocations()`, instead
trusts a list of already-validated, already-persisted `DetectedField` coordinates directly,
skipping the re-scan entirely (there is no literal token text to find — that's the whole reason
Gemini was asked in the first place). `CustomTemplateAssetService` dispatches between the two at
render time based on the asset's stored `analysisSource()`. Gemini is never in the rendering
path, never sees profile data, and never produces PDF bytes, an HTML template, or a command —
only a bounded, schema-validated description of *where things are* on a page it was shown.
document-service never holds a provider API key; it reaches Gemini only through a new internal
Feign client, `AiServiceClient`, calling ai-service — ai-service remains the sole holder of
`GEMINI_API_KEY`/`GROQ_API_KEY` (ADR-012), completely unchanged by this feature.

## Problem

Two things had no existing answer:

1. **The deterministic PDFBox scan only ever finds literal `{{token}}` text.** A custom PDF
   template that was designed with visually blank fillable regions instead — a common real-world
   shape for a "download this template, fill it in" resume design — has genuinely nothing for
   `PdfStructureAnalyzer` to find, no matter how it's tuned. ADR-023 already made the deliberate
   choice that a zero-field PDF is *accepted*, not rejected (matching a zero-placeholder DOCX) —
   but "accepted with zero usable fields" still means the template can't actually be filled in
   for that user. This task asked for a second, AI-assisted attempt specifically for that case,
   not a change to what counts as an acceptable upload.
2. **Gemini has no concept of "the real PDF's page geometry."** A vision-capable model can
   describe *where it thinks something is* on the page it was shown, but only as a relative
   position — it has no reliable way to report exact PDF-point coordinates for an arbitrarily
   sized page, and it must never be trusted to invent an exact number even if it tried. Any
   design had to treat every coordinate, page reference, and field name it returned as something
   to check against the real, independently-known document — not as already-correct data ready
   to render from.

## Options

For where the fallback lives: (a) inside `PdfStructureAnalyzer` itself, so Gemini becomes part of
"the" PDF analysis; (b) as a separate step, invoked by `CustomTemplateAssetService` only after the
existing analyzer already ran and came back empty.

For what Gemini is allowed to touch: (a) let it also generate/adjust the final rendered PDF
somehow (e.g. asking it to describe drawing operations); (b) restrict it to describing regions
only, keeping 100% of actual PDF byte production in PDFBox, unchanged.

For how Gemini's output reaches persistence: (a) persist it close to as-is, validating only that
it's well-formed JSON (schema shape); (b) convert it into the exact same `DetectedField` domain
shape the deterministic scanner already produces, with a second, independent, geometry-aware
validation pass (page bounds, fraction range, computed-box-vs-real-page-size) before anything is
persisted.

## Selected Option

(b) for all three.

## Reason

For where the fallback lives: option (a) would make `PdfStructureAnalyzer` — a small, pure,
dependency-free class that only ever reads bytes it's given — suddenly need an HTTP dependency on
ai-service, entangling a cheap, always-safe-to-call local operation with a network call that can
fail, retry, and cost money. Option (b) keeps `PdfStructureAnalyzer` completely unchanged and puts
the *decision* to escalate ("did the deterministic scan find anything?") in
`CustomTemplateAssetService`, which already orchestrates both analyzers and is the natural place
to own a fallback between them.

For what Gemini is allowed to touch: option (a) directly contradicts this task's own explicit
requirement ("Gemini MUST NOT generate the final PDF... Rendering remains: PDFBox → deterministic
text placement/overlay") and this codebase's standing rule that ATS-relevant and
document-producing operations stay deterministic and auditable (mirrors ADR-008/009/014's
identical reasoning for scoring — never asked of the LLM). Option (b) is the only choice
consistent with both.

For how Gemini's output reaches persistence: option (a) would mean trusting a page number,
coordinate, or field name that's merely *shaped* correctly but could still reference a page that
doesn't exist or a box that runs off the real page — exactly the "coordinates... before
persistence" validation this task explicitly required, and a real risk given Gemini has no
authoritative knowledge of the exact PDF it was shown beyond what it visually inferred. Option (b)
— converting into `DetectedField` (the same shape `PdfStructureAnalyzer` already produces, so
every downstream consumer needs no new type) and checking every field's geometry against the
*real* PDF's own page count/dimensions (read by the deterministic analyzer moments earlier, in
the same request) — is the only option that satisfies the task's own validation requirement.
Rejecting one bad field rather than the whole analysis (mirroring
`EvidenceSelectionService.stripUnknownEvidenceIds`'s identical "strip the bad citation, keep the
rest" philosophy) avoids an all-or-nothing failure mode where one malformed field out of many
would otherwise waste an entire Gemini call.

## Impact

**ai-service**: new `schemas/pdf-template-analysis.schema.json` (page count, column count,
headings, and a bounded `fields` array — token pattern, page, four fractions, optional
suggested-field/context — `additionalProperties: false` throughout) and
`prompts/pdf-template-analysis/v1.txt` (defines the field vocabulary, forbids inventing content or
following embedded instructions, specifies fraction-based coordinates). `GeminiClient` gains a
second, independently-built multimodal generate function and a new public
`analyzeDocument(byte[], mimeType, systemPrompt, operation)` method (`Part.fromBytes` +
`Content.fromParts`, via the Gemini Java SDK's multimodal `generateContent` overload); the shared
retry/backoff logic (`callWithRetry`) is factored out and reused by both the text-only and
multimodal paths. New `PdfTemplateAnalysisService` (depends on `GeminiClient` directly, not
`AiProviderRouter` — there is no Groq equivalent for multimodal PDF understanding in this
codebase, so there is nothing to route between). New `AiController` endpoint,
`POST /internal/ai/pdf-template-analysis` (internal-only, ADR-012, no gateway route) — the first
endpoint where a raw `GeminiException` (not already absorbed by a router's Groq fallback) can
reach the controller's error-mapping wrapper.

**document-service**: new `client/AiServiceClient` (Feign, document-service's first-ever
dependency on ai-service) and `client/ClientDtos.PdfTemplateAnalysisResponseDto`/
`PdfTemplateFieldDto`; new `domain/TemplateAnalysisSource` enum (`DETERMINISTIC`/
`GEMINI_ASSISTED`); `domain/CustomTemplateAsset` gains a nullable `analysisSource` field
(defaults to `DETERMINISTIC` for every row persisted before this feature existed, via the same
null-defaulting-accessor convention `format()`/`objectKey()` already use); new
`pdf/GeminiPdfFieldConverter` (the untrusted-input validation/conversion boundary described
above); `pdf/PdfMailMerge` gains `mergeAtKnownLocations(document, resolvedValues,
knownFields)`, sharing every downstream rendering primitive (redaction, fit/condense,
font-fallback, serialization) with the existing `merge()` — only the *source* of field
coordinates differs (re-scan vs. pre-supplied); `service/CustomTemplateAssetService`'s
`storeAndAnalyze` gains a Gemini-fallback branch (`analyzePdf`/`attemptGeminiAssistedAnalysis`,
both new private methods) invoked only when the deterministic scan's `detectedFields` is empty,
and its `mergePdf` helper now dispatches on the asset's `analysisSource()`; constructor gains
`AiServiceClient`/`GeminiPdfFieldConverter` parameters (all call sites, including tests, updated).
No change to `DocxMailMerge`/`DocxStructureAnalyzer` — DOCX rendering was explicitly out of scope
for this task and was not touched.

**Explicitly not done**: no change to the existing deterministic PDF/DOCX analysis or rendering
behavior for any template that already has literal `{{token}}` placeholders (verified: a PDF with
real placeholders never calls Gemini at all — the deterministic path short-circuits before Gemini
is ever considered); no change to Resume Content, Cover Letter Content, Email Content, JD
Analysis, or Evidence Selection generation/grounding logic; Gemini never produces PDF bytes, HTML,
or a command, and never sees resume/cover-letter content data — only the uploaded template's own
bytes, for layout description only.

**Tests**: `ai-service` — `GeminiClientTest` gains a `Multimodal` nested class (5 cases: success
path, response-schema attached, never falls through to the text-only generate function, retries
and fails safely when Gemini is unavailable, fails immediately without any call when
`GEMINI_API_KEY` is unset), new `PdfTemplateAnalysisServiceTest` (3 cases) — 123/123 passing.
`document-service` — new `GeminiPdfFieldConverterTest` (10 cases: valid conversion with correctly
flipped PDF coordinates, null/empty input, out-of-range/missing page number, out-of-range
fractions, invalid/missing/oversized token, box outside real page bounds or non-positive computed
size, unrecognized `suggestedField` dropped to `null`, duplicate-token dedup, one bad field never
rejecting the others); `PdfMailMergeTest` gains 2 cases (`mergeAtKnownLocations` renders a
resolved value at its stored coordinates with no literal placeholder text involved anywhere in
the document; a field with no stored PDF position is silently skipped, never rendered);
`CustomTemplateAssetServiceTest` gains 8 cases (existing-placeholder PDF never calls Gemini;
zero-placeholder PDF falls back to Gemini and persists `GEMINI_ASSISTED` fields; a multi-page PDF
with a Gemini field on page 2 validates and places it correctly; an out-of-range page number is
rejected safely; a batch of only-invalid fields falls back to `DETERMINISTIC`/empty rather than
throwing; a Feign failure reaching ai-service is handled safely; an unexpected runtime error from
the Gemini call path is handled safely) — 70/70 passing (58 pre-existing/regression + 12 new).
Full reactor `mvn compile`: BUILD SUCCESS. Regression: `application-service` (45/45),
`resume-service` and `jd-service` unit suites pass unchanged.

---

# ADR-030

## Decision

`ResumeContentService` (Stage 2 of the generation pipeline — evidence in, grounded resume
content out) is migrated from a direct `AiChatClient` dependency to `AiProviderRouter`, exactly
like `JdAnalysisService` (ADR-026), `EvidenceSelectionService` (ADR-027), and
`CoverLetterContentService` (ADR-028) before it — `AiProviderRouter.ROUTED_OPERATIONS` widens to
`{JD_ANALYSIS, EVIDENCE_SELECTION, COVER_LETTER_CONTENT, RESUME_CONTENT}`. Both the first
generation attempt and the (at most one) regeneration attempt route through
Gemini-primary/Groq-fallback independently; a Gemini failure on either call falls back to Groq
for that call only, never the other, never a second Gemini attempt.

Nothing else about resume generation changed: the prompt (`prompts/resume-content/v1.txt`,
unversioned by this change), the JSON Schema (`resume-content.schema.json`), how job/evidence
context is built and fenced (`buildUserContent`), `GroundingValidator`'s rules, the
regenerate-once-then-surgically-remove failure policy (`extractStatements`/`removeStatements`,
byte-for-byte unchanged), the response shape (`AiResponses.ResumeContentResponse`), and every
downstream consumer of it (`resume-service`'s persistence of `ResumeVersion`, `document-service`'s
rendering, `assessment-service`'s ATS/JD-fit scoring) are all unaware this migration happened —
none of them call ai-service any differently, and ai-service's own
`POST /internal/ai/resume-content` endpoint contract is byte-for-byte identical. Gemini's output
does not need to be textually identical to what Groq would have produced — it only needs to
satisfy the exact same structural (schema), business (evidence-citation), and grounding rules
Groq's output was already required to satisfy; the validation pipeline is what defines
correctness here, not which provider generated the text.

## Problem

None — this is the fourth application of an already-established, already-proven pattern
(ADR-026/027/028), not a new design problem. The only thing to decide was whether Resume Content
specifically was safe to migrate the same way, given it is the highest-stakes of the five
operations: it is the stage that actually assembles the candidate-facing resume bullets/summary a
real person sends to a real employer, and it is the one the entire grounding/anti-fabrication
system (`GroundingValidator`, this codebase's "one rule everything else serves") exists primarily
to police.

## Options

Identical reasoning to ADR-026/027/028 — widen `AiProviderRouter`'s routed-operation set by one,
rather than building a separate routing mechanism, a resume-content-specific fallback, or any
weakening of the grounding contract to accommodate a second provider. Not re-litigated here.

The one question specific to this operation: should the migration also relax, duplicate, or
provider-condition any part of `GroundingValidator`/the schema, on the theory that a different
model might need different rules? (a) yes — add a Gemini-specific validation path; (b) no — the
exact same `GroundingValidator`/`SchemaValidator` instances run against whichever provider's
output arrives, with zero knowledge of which provider produced it.

## Selected Option

(b).

## Reason

Resume Content is the operation this product's central anti-fabrication guarantee — "the AI may
select, rank, condense and rephrase facts the user supplied — it must never invent an employer,
date, metric, technology, certification, project or achievement" — most directly protects, and
that guarantee is enforced in code (`GroundingValidator`), not in a prompt, precisely so it holds
regardless of which model produced the draft. Provider-conditioning the validation itself would
mean trusting Gemini's output on different, weaker terms than Groq's purely because of which
vendor answered — the opposite of what "enforced in code, not in the prompt" is supposed to
guarantee, and a real risk of quietly widening the fabrication surface for whichever provider
happens to be primary that day. Option (b) — the same validator, unaware of provenance, applied
identically regardless of the winning provider — is the only choice consistent with that rule,
and is exactly what ADR-028 already established works correctly for cover-letter content, the
other content-generation (as opposed to classification/extraction) stage already migrated.

Migrating this operation completes routing four of the five stages; `EmailContentService` remains
directly on Groq — email content is deterministically assembled from already-verified data (job
title/company, the candidate's real name) with ai-service supplying only a grounded highlight
paragraph (ADR-019), a narrower, lower-stakes generation task nothing in this task asked to move.

## Impact

**ai-service**: `AiProviderRouter.ROUTED_OPERATIONS` gains `RESUME_CONTENT`.
`ResumeContentService` constructor parameter `AiChatClient` → `AiProviderRouter`; both
`.complete(...)` call sites (first attempt, regeneration attempt) now go through
`router.complete(AiOperation.RESUME_CONTENT, ...)`. No other line in that class changed — same
`buildUserContent`, `extractStatements`, `removeStatements`, `correctionNotice`. Stale Javadoc
elsewhere referencing "the four business call sites still on Groq" (`GroqClient`,
`GeminiClient`, `AiProviderWiringTest`) or "the other services still do" (`EvidenceSelectionService`,
`JdAnalysisService`) corrected to name `EmailContentService` as the one remaining unrouted
operation — no behavioural change, comment accuracy only.

**resume-service, document-service, assessment-service, application-service**: zero changes.
None of them call ai-service any differently — the `POST /internal/ai/resume-content` request/
response contract is unchanged, so every downstream consumer of resume content (persistence,
rendering, scoring) is unaffected by construction, not merely by testing.

**Explicitly not done**: no change to the resume-content prompt or schema; no change to
`GroundingValidator`'s rules, generically or per-provider; no change to the built-in or custom
template rendering pipelines (Thymeleaf/openhtmltopdf, docx4j, PDFBox — none of PDFBox,
docx4j, or openhtmltopdf's own code was touched); no change to ATS/JD-fit scoring
(`assessment-service` remains entirely deterministic, never asked of any LLM, ADR-008/009/014);
no change to the database model (`ResumeVersion`'s shape, and every collection this touches,
already provider-agnostic — nothing here required a schema/model change).

**Tests**: `ai-service` — `AiProviderRouterTest`'s parameterized cases extended to
`RESUME_CONTENT` (removed from `UnroutedOperations`, which now only asserts `EMAIL_CONTENT` is
rejected); `ResumeContentServiceTest` gains a `Routing` nested class (4 cases: succeeds via
Gemini without ever calling Groq, falls back to Groq when Gemini fails, both-providers-fail
propagates `GroqException` unchanged, a regeneration attempt is also routed) — 131/131 passing
(123 baseline + 5 from the widened parameterization + 4 new, − 1 retired unrouted-operation
case). Full reactor `mvn compile`: BUILD SUCCESS. Regression, run in one combined reactor pass:
`ai-service` (131/131), `document-service` (70/70), `application-service` (45/45),
`resume-service` (14/14), `jd-service` (5/5) — all pass unchanged, proving built-in PDF/DOCX,
custom-template PDF/DOCX, and ATS/JD-fit generation are unaffected. Frontend: `npm run
typecheck` clean, `npm test` (vitest harness, no unit test files yet) exits 0 — no frontend code
required any change for a backend-only provider-routing migration with an unchanged API
contract.

---

# ADR-031

## Decision

A verification-and-hardening pass over the Gemini/Groq architecture built across
ADR-025–030 — no new operation migrated, no routing/prompt/schema/rendering behaviour changed.
Four categories of work:

1. **Router-level observability.** `AiProviderRouter` gains two Micrometer counters at the one
   choke point every routed operation passes through: `careerforge.ai.router.completions`
   (tagged `operation`, `provider` — which provider actually served the request) and
   `careerforge.ai.router.fallbacks` (tagged `operation` — a Gemini failure genuinely triggered
   a Groq attempt). Both are additive to the per-client metrics `GeminiClient`/`GroqClient`
   already recorded on their own (`careerforge.ai.gemini.*`/`careerforge.ai.*` — success,
   failure, latency, token usage); nothing about those changed. Tag values are always one of a
   small fixed set (the operation tag, `"gemini"`, `"groq"`) — never a prompt, a response, or
   any candidate data, proven by a dedicated test that inspects every tag on every meter
   recorded during a call.
2. **Explicit fallback-independence test coverage.** Added tests proving, per content-generation
   operation, that a grounding failure on *every* attempt (ending in surgical removal) never
   calls Groq — only a genuine Gemini transport/API failure does. This property already held by
   construction (schema/grounding validation happens in each `*ContentService`, strictly after
   `AiProviderRouter.complete()` already returned; the router has no visibility into validation
   at all), but it was previously proven only indirectly (regeneration tests where Gemini always
   happened to succeed) rather than asserted directly with `verify(groqClient, never())...`
   against content that genuinely fails validation on both attempts.
3. **Correction of several stale docs/config comments.** `ai.provider`/`AI_PROVIDER` has had no
   effect on routing since ADR-026 (both `GroqClient` and `GeminiClient` became unconditional
   beans; per-operation routing lives entirely in `AiProviderRouter`), but `application.yml`'s
   comment, `.env.example`'s description, and `GeminiProperties`' Javadoc still described the
   superseded ADR-025 `@ConditionalOnProperty` "global on/off switch" behaviour as current —
   actively misleading for anyone configuring a deployment. `GeminiClient`'s missing-key error
   message named the same dead property. `docs/ai-abstraction.md` still read as a snapshot of
   ADR-025/026 only (claimed "two of five operations routed," had a duplicated section heading).
   `docs/EXTERNAL_APIS.md` never gained a Gemini section at all despite it being a real,
   live external integration since ADR-025. `README.md`'s architecture diagram, tech-stack
   summary, and env-var tables never mentioned Gemini, describing the AI boundary as
   Groq-only. All corrected to the actual current state; no behaviour changed by any of this.
4. **Verification, not new code, for the rest of the task's checklist** — reasonable timeouts
   (`GROQ_TIMEOUT_SECONDS`/`GEMINI_TIMEOUT_SECONDS`, both `@Min(5) @Max(300)`, default 60s),
   bounded retries (`GROQ_MAX_RETRIES`/`GEMINI_MAX_RETRIES`, both `@Min(0) @Max(5)`, default 2,
   429/5xx only, never a 4xx the caller caused), no retry loops (`AiProviderRouter` itself makes
   exactly one Gemini attempt and, on failure, exactly one Groq attempt — never a loop; each
   client's own internal retry policy is separately bounded), provider failure isolation
   (stateless clients, independent retry state, a Gemini failure can never affect a concurrent
   or subsequent Groq call), secrets server-side only (`.env` gitignored and never committed,
   `.env.example` ships only blank placeholders, `GroqProperties`/`GeminiProperties` bind from
   environment variables only, both expose `maskedKey()` and never log the real key, ai-service
   is the only process holding either key — document-service reaches Gemini-assisted PDF
   analysis only through an internal Feign call to ai-service, ADR-029), and document-rendering
   failure isolation (`document-service`'s `DocumentRenderService`/`PdfMailMerge`/`DocxMailMerge`
   have zero dependency on ai-service or any AI-generation code path — a render failure like
   `TEMPLATE_CONTENT_OVERFLOW` cannot trigger AI regeneration because no code path connects
   them; content generation and document rendering are separate, independently-triggered
   requests, confirmed by grep across both services' dependency graphs) — all already true by
   the existing architecture, verified here rather than re-implemented.

## Problem

No new problem — this step's job was explicitly to verify the architecture ADR-026 through
ADR-030 built is production-safe, not to extend it. Verification surfaced two real gaps worth
closing: (a) "fallback occurred" was observable only via a log line, not a metric, making "how
often does operation X actually fall back to Groq" require log-scraping rather than a Grafana
query; (b) "grounding failure never triggers fallback" was true by construction but not directly
tested, so a future refactor could silently break that invariant without any test catching it.
Verification also surfaced accumulated documentation drift: several comments and one full
document (`docs/ai-abstraction.md`) had never been updated across four operation migrations
(ADR-027/028/029/030), to the point of describing already-removed mechanisms as current.

## Options

For observability: (a) leave it as-is, since per-client metrics already exist and fallback is
already logged; (b) add router-level metrics for exactly the two things no single client's own
metrics can express (which provider ultimately won, and whether a fallback happened at all).

For fallback-independence: (a) trust the existing architecture without a direct test, since the
router's own code has no path to even see a validation failure; (b) add an explicit test per
content-generation operation that proves it with a real (non-mocked-away) grounding failure.

For documentation: (a) leave the stale comments/doc as historical snapshots, since
`docs/ARCHITECTURE_DECISIONS.md` is the authoritative, current record per `CLAUDE.md`'s own
table; (b) correct the actively-misleading ones (particularly `.env.example`/`application.yml`,
which an operator reads to configure a real deployment) since "authoritative record exists
elsewhere" doesn't stop a wrong comment next to a real config value from being followed.

## Selected Option

(b) for all three.

## Reason

For observability: this task explicitly asked to track "operation, provider, success/failure,
fallback, duration" — operation/duration/success/failure already existed per-client; provider
was only inferable by which metric-name-prefix incremented; fallback existed only as a log line.
Option (b) closes exactly that gap, additively, at the one place (the router) that already sees
every routed call regardless of which provider serves it — no restructuring of either client's
existing, already-tested metrics.

For fallback-independence: option (a) leaves a real, if currently-true, invariant unguarded — a
future change to `AiProviderRouter` or either `*ContentService`'s regeneration logic could
silently start treating a validation failure as fallback-worthy, and nothing would fail until
production. Option (b) turns "true by construction" into "true and proven," at the cost of one
test method per content-generation service using a fixture already established elsewhere in the
same test file (the same "ungrounded" JSON string the existing regeneration test already uses).

For documentation: `.env.example` and `application.yml`'s comment are not "historical record" —
they are read by whoever configures the next deployment, and a wrong comment claiming
`AI_PROVIDER=gemini` "genuinely switches every AI generation call to Gemini" (it does nothing)
is actively dangerous: someone could set it, see no effect, and reasonably conclude Gemini
routing is broken rather than realizing the variable itself is inert. `docs/EXTERNAL_APIS.md`'s
complete silence on Gemini — despite it being a real credential every deployment may configure —
is a gap in exactly the doc `CLAUDE.md` names for "An integration" changes. These are corrected
in place rather than left as-is.

## Impact

**ai-service**: `AiProviderRouter` constructor gains a `MeterRegistry` parameter (all 5 test
construction sites updated); two new counters as described above. `GeminiClient`'s missing-key
error message no longer names the dead `ai.provider` property. `GeminiProperties`' Javadoc
corrected to describe the actual current mechanism (blank key → per-operation Groq fallback, not
a global switch). `application.yml`'s `ai.provider` comment rewritten to state plainly that it
is vestigial and has no effect on routing. No change to any prompt, schema, `GroundingValidator`
rule, retry policy, or persisted shape.

**No change** to resume-service, document-service, assessment-service, application-service,
jd-service — this ADR touched only ai-service code, plus documentation/config comments
repo-wide.

**Documentation**: `.env.example`'s `AI_PROVIDER`/Gemini section rewritten to state the actual
current behaviour; `docs/ai-abstraction.md`'s Status header, routed-operations code snippet,
duplicated heading, and "what was deliberately not done" section brought current through
ADR-030/031; new "Gemini" section added to `docs/EXTERNAL_APIS.md` (mirroring the existing Groq
section's structure — Purpose/Environment variables/Used by/Required/Setup/Rate limits/
Security), and the Groq section's own "Purpose" line corrected (no longer claims to be "the only
LLM provider"); `README.md`'s architecture diagram, tech-stack summary, prerequisites table, env
var table, service table, "Trying the AI service" section, and "rules that are never relaxed"
list all updated to mention Gemini alongside Groq; `docs/CODEBASE.md`'s ai-service section
updated (Purpose line, `AiProviderRouter` entry, security-posture paragraph) to name both
providers and the new router metrics.

**Explicitly not done**: no operation migrated (Email Content remains directly on Groq, per this
step's own confirmed scope — see the task's clarifying answer); no prompt, schema, grounding
rule, timeout, retry-count, or persisted-shape change to either provider or any content
generation stage; no change to template rendering, PDF/DOCX generation, PDFBox, docx4j,
openhtmltopdf, or ATS/JD-fit scoring; no new Gemini "status" health-check endpoint (would cost a
live network call/quota on every check — a real production concern, not something this task
asked for); `.env` (the real, gitignored, locally-populated file) was not read or modified —
only `.env.example`'s placeholder template.

**Tests**: `ai-service` — `AiProviderRouterTest` gains an `Observability` nested class (3 cases:
records a completion tagged by operation/provider on success, records a fallback and the
winning Groq completion on Gemini failure, never records anything beyond the fixed operation-tag/
provider-name vocabulary); `ResumeContentServiceTest$Routing` and
`CoverLetterContentServiceTest$Routing` each gain one fallback-independence case (grounding
fails on every attempt → ends in surgical removal, Groq never called); `JdAnalysisServiceTest`'s
existing schema-validation-failure case strengthened with an explicit `verify(groqClient,
never())` — 136/136 ai-service tests passing (131 baseline + 5 new). Full reactor `mvn compile`
and `mvn test-compile`: BUILD SUCCESS. Full reactor `mvn test` (all 13 services + platform-
common): BUILD SUCCESS, 289 tests passing across auth-service (19), jd-service (5),
resume-service (14), ai-service (136), document-service (70), application-service (45) — every
module with tests green, config-server/discovery-server/api-gateway/profile-service/
assessment-service/notification-service have no unit tests and built cleanly. Frontend: `npm run
typecheck` and `npm run build` both clean (no frontend file touched by this ADR). Playwright
e2e was not run — it requires the full live stack (all services + MongoDB Atlas + MinIO/Redis)
which this environment has no Docker daemon or live credentials to bring up; zero frontend or
API-contract changes in this ADR make a regression there structurally unlikely, but this is a
gap this report states plainly rather than claiming a pass it cannot verify.

---

<a id="adr-032"></a>

# ADR-032

## Decision

The entire Gemini-primary/Groq-fallback routing architecture built across ADR-025–028/030–031
is **reverted**. Groq is once again the sole provider for all five JSON/content-generation
operations — JD Analysis, Evidence Selection, Resume Content, Cover Letter Content, Email
Content — with every call site injecting `AiChatClient` directly and resolving unambiguously to
`GroqClient`, exactly the shape `EmailContentService` never stopped using. Concretely:

- `ai.careerforge.ai.client.AiProviderRouter` and `ai.careerforge.ai.client.AiOperation` are
  **deleted** — no replacement, no dead stub left behind.
- `JdAnalysisService`, `EvidenceSelectionService`, `ResumeContentService`,
  `CoverLetterContentService` revert their constructor from `AiProviderRouter` back to
  `AiChatClient`, and their `.complete(...)` call sites (including each content-generation
  service's at-most-one regeneration attempt) call it directly. Prompt resolution, untrusted-
  content fencing, schema validation, `GroundingValidator`, the regenerate-once-then-surgically-
  remove failure policy, and every response shape are **byte-for-byte unchanged** — only the
  routing layer between "prompt built" and "provider called" is removed.
- `GeminiClient` no longer implements `AiChatClient`. Its `complete()` method, the
  `GenerateFunction` functional interface, the `generateFunction` field, and the
  `buildGenerateFunction()` factory are all deleted. `GeminiClient` now exposes exactly one
  capability — `analyzeDocument(...)`, Gemini-assisted multimodal PDF template layout analysis
  (ADR-029, the one operation this reversal does **not** touch) — and all the shared
  config/schema-dereferencing/retry infrastructure that method depends on is kept in full,
  since `analyzeDocument` still uses every bit of it (`buildConfig`, `callWithSchemaFallback`,
  `loadResponseJsonSchema`'s `$ref`/`$defs` inlining, `callWithRetry`, `mapClientException`,
  `toResult`).
- `GroqClient` drops `@Primary` — meaningless once it is the only `AiChatClient` implementation
  left; unqualified injection resolves to it because nothing else exists to disambiguate from.
- The vestigial `ai.provider` (`AI_PROVIDER`) config key — confirmed, across ADR-026 and
  ADR-031, to have had **no effect on routing since ADR-026** — is deleted outright from
  `application.yml` and `.env.example`, rather than kept bindable-but-inert a second time.

## Problem

The Gemini-routing architecture, while working correctly (ADR-031 verified it production-safe),
consumed Gemini API quota on every JD Analysis / Evidence Selection / Resume Content / Cover
Letter Content request in a deployment with `GEMINI_API_KEY` configured. Gemini's free tier caps
at a low daily request count; a single active user generating one resume can burn through JD
Analysis + Evidence Selection + Resume Content + (optionally) Cover Letter Content in one
sitting, several Gemini calls per generation, well before hitting any Groq-side limit. This is
a cost/quota problem the routing architecture itself cannot solve by construction — it was
built to prefer Gemini, not to conserve it. There is also no product requirement that JSON/
content generation specifically use Gemini rather than Groq; both providers were already proven
schema-compliant and grounding-compatible (ADR-025's own evaluation, unchanged since). Separately,
five ADRs' worth of routing machinery (`AiProviderRouter`, `AiOperation`, per-operation Gemini
wiring across four services, four parallel `Routing`/fallback test suites) is real, ongoing
maintenance surface for a workflow with only one legitimate remaining Gemini use case
(document/layout analysis, ADR-029) once JSON generation moves back to Groq-only.

## Options

(a) Keep the router but flip its default so Groq is primary and Gemini is the fallback for each
operation — preserves the abstraction, changes only which provider is tried first.
(b) Gate Gemini routing behind a feature flag operators can disable per-deployment, defaulting
to off — preserves both code paths, adds a flag to reason about.
(c) Delete the routing layer entirely: revert all four migrated call sites to direct
`AiChatClient` injection (Groq only), delete `AiProviderRouter`/`AiOperation`, and narrow
`GeminiClient` to the one capability with no Groq equivalent at all (document analysis).

## Selected Option

(c).

## Reason

Option (a) still calls Gemini on every request whose Groq attempt happens to fail — under
normal operation that's rare, but "rare" across five operations' worth of production traffic is
not "zero," and the whole point of this reversal is a hard, verifiable "Gemini JSON calls: 0"
guarantee, not "usually zero." Option (b) keeps the entire router/enum/per-service-Gemini-path
surface alive for a mode nobody is meant to use — exactly the "old + new competing paths" this
task explicitly asked to avoid, and it leaves `AiProviderRouterTest`,
`ResumeContentServiceTest$Routing`, `CoverLetterContentServiceTest$Routing`, and equivalent
suites permanently exercising a disabled code path. Option (c) is the only one that makes the
guarantee structural rather than configurational: with `AiProviderRouter` deleted and
`GeminiClient` no longer implementing `AiChatClient` at all, there is no code path by which a
JD Analysis / Evidence Selection / Resume Content / Cover Letter Content / Email Content request
can reach Gemini — not "defaults to off," but "the wiring to do so does not exist." It also
directly serves this task's explicit cleanup mandate (§9–12 of the originating task): one clear
implementation path, not a flag deciding between two.

## Impact

- **Deleted**: `ai.careerforge.ai.client.AiProviderRouter`,
  `ai.careerforge.ai.client.AiOperation`,
  `ai.careerforge.ai.client.AiProviderRouterTest` (router-specific unit tests — no longer
  meaningful once the class is gone).
- **`GeminiClient`**: `complete()`, `GenerateFunction`, the `generateFunction` field, and
  `buildGenerateFunction()` removed; no longer `implements AiChatClient`. `analyzeDocument()`
  and every piece of shared request/retry/schema-dereferencing infrastructure it depends on —
  including the `$ref`/`$defs` inlining fixed for the real Gemini structured-output bug found
  against `resume-content.schema.json`/`cover-letter.schema.json` — is unchanged and still live,
  since `analyzeDocument` uses the identical `loadResponseJsonSchema`/`callWithSchemaFallback`
  path by the same `schemas/<operation>.schema.json` convention.
- **`JdAnalysisService`, `EvidenceSelectionService`, `ResumeContentService`,
  `CoverLetterContentService`**: constructor parameter reverted from `AiProviderRouter` to
  `AiChatClient`; every `.complete(...)` call site (including each content-generation service's
  regeneration attempt) calls it directly, with the `AiOperation` enum constant replaced by the
  same plain `String` operation tag `EmailContentService` always used (e.g. `"resume-content"`).
  No change to prompt resolution, schema names, `GroundingValidator` usage, the regenerate-once-
  then-strip failure policy, or any response DTO shape.
- **`GroqClient`**: `@Primary` removed (nothing left to disambiguate from) — Javadoc updated to
  describe the simpler, final reality.
- **Config**: `application.yml`'s `ai.provider` block and `.env`/`.env.example`'s `AI_PROVIDER`
  line deleted outright (dead since ADR-026, per ADR-031's own finding — this reversal removes
  it rather than re-describing it as vestigial a third time). `careerforge.groq.*` and
  `careerforge.gemini.*` blocks (api-key, model, timeout, tokens, temperature, retries) are
  unchanged and remain both required — Groq for every JSON operation, Gemini for
  `analyzeDocument` — the Gemini comment updated to state plainly that Gemini now backs exactly
  one capability, not five routed operations.
- **Not changed at all**: `PdfTemplateAnalysisService` (ADR-029's still-`Accepted` capability —
  depends on `GeminiClient` directly, never went through `AiChatClient`/`AiProviderRouter` to
  begin with, per that ADR's own design), every prompt file, every schema file, `GroundingValidator`
  and all of its rules, `document-service`'s entirely deterministic rendering pipeline
  (Thymeleaf/jsoup/openhtmltopdf for built-in PDFs, docx4j for custom DOCX mail-merge, PDFBox for
  custom PDF mail-merge/structure analysis — zero AI involvement at generation time, unaffected
  by anything JSON-generation-related), `resume-service`, `application-service`, `jd-service`,
  `assessment-service`'s deterministic ATS/JD-fit scoring, or any frontend file — this reversal
  is entirely internal to `ai-service`'s AI-provider wiring; every downstream HTTP contract is
  unchanged.
- **Superseded ADRs**: ADR-025, ADR-026, ADR-027, ADR-030, ADR-031 (the routing architecture and
  its hardening pass) are marked "Superseded by ADR-032" in the index above. ADR-028 is marked
  superseded for its routing half only — the parallel cover-letter PDF/DOCX rendering pipeline
  it also introduced in document-service is unrelated to AI-provider routing and remains in
  effect, unaffected by this ADR. ADR-029 (Gemini-assisted PDF template analysis) is **not**
  superseded — it is the one Gemini capability this ADR explicitly keeps.
- **Tests**: `AiProviderRouterTest` deleted. `AiProviderWiringTest` rewritten (2 cases: both
  clients always beans; `GroqClient` the only `AiChatClient` bean, no `@Primary` needed).
  `JdAnalysisServiceTest`, `EvidenceSelectionServiceTest` rewritten to a single-mocked-
  `AiChatClient` pattern, dropping every Gemini/fallback-specific case. `ResumeContentServiceTest`
  and `CoverLetterContentServiceTest`'s `Routing` nested classes renamed `Generation` and
  simplified to the same single-mock pattern, keeping regeneration and surgical-removal coverage
  and dropping only the fallback-specific cases (there is no second provider left to fall back
  to). `GeminiClientTest` trimmed to its `Multimodal` nested class only — every scenario now
  exercises `analyzeDocument`, including the `$ref`/`$defs` dereferencing regression test
  (adapted to call `analyzeDocument` with a real `$ref`-bearing schema resource, since that
  dereferencing logic is still shared, live infrastructure even though Gemini no longer receives
  a real resume-content request). ai-service full suite: **99/99 passing**. Full reactor `mvn
  -pl services/platform-common,services/ai-service -am compile` and `test-compile`: BUILD
  SUCCESS, no errors in either module.


---

<a id="adr-033"></a>

# ADR-033

## Decision

CareerForge AI no longer generates a resume or a cover letter. It analyses a confirmed job
description against the candidate's verified profile and produces **JD-optimization data**:
which of the posting's terms matter, which the profile can actually evidence, which requirements
are unmet, and what to lead with. The user takes that structured output to whatever document
tool they prefer.

Concretely:

- **New**: `jd-optimization` prompt/schema, `ai-service`'s `JdOptimizationService`,
  `POST /internal/ai/jd-optimization`, jd-service's orchestration + `JdOptimization` domain
  (`jd_optimizations`, keyed by `jdVersionId`), `POST /api/jd/{id}/optimize`,
  `GET /api/jd/{id}/optimization`, and the frontend optimization result page with
  Copy JSON / Download JSON / Copy AI Prompt.
- **Removed**: `resume-service` and `document-service` (entire modules), `ResumeContentService`,
  `CoverLetterContentService`, their prompts/schemas/DTOs/tests, `CoverLetterGenerationService`,
  `CoverLetterVersion`, all PDF/DOCX rendering (Thymeleaf, openhtmltopdf, docx4j, PDFBox,
  mail-merge, custom templates), and the frontend resume/cover-letter/template features.
- **Assessment re-keyed**: `JdFitAssessment` is now keyed by `jdOptimizationId`. ATS scoring was
  deleted outright — every check read a rendered resume's structure.
- **Gemini removed entirely**: its last consumer was custom-PDF template analysis (ADR-029),
  which died with document rendering. `GeminiClient`, `GeminiProperties`, `GEMINI_API_KEY` and
  the `google-genai` dependency are gone. Groq remains the only AI provider.
- **Email generation is untouched** and remains an active feature end to end.

## Problem

The product's core promise was a grounded, JD-tailored resume. Two things undermined it. The
anti-fabrication machinery could keep generated prose honest, but it could not make a generated
resume *better* than what the candidate could write from the same facts — and every generated
document still needed the user to check it line by line. Meanwhile the document pipeline was the
single largest source of complexity and failure in the platform: two rendering engines, a
template catalogue, custom uploads, a Gemini multimodal analysis path, object storage,
overflow handling, and a fallback for when it all failed anyway.

The valuable part was never the rendered file. It was the analysis underneath: which of this
posting's requirements the candidate can actually evidence, and which they cannot.

## Options

(a) Keep generating documents and continue investing in rendering.
(b) Keep the resume JSON but drop rendering, leaving users to format it themselves.
(c) Stop generating documents entirely and ship the analysis as the product, with a
ready-to-paste prompt so any external tool can produce the document.

## Selected Option

(c).

## Reason

(a) doubles down on the part with the worst effort-to-value ratio. (b) is (c) without the honesty:
a resume JSON is a document that hasn't been rendered yet, so the product would still be promising
a resume while shipping something the user has to finish. (c) changes what is promised. The output
is explicitly *data about the match* — keywords the profile supports, requirements it does not,
evidence to emphasise — which is genuinely more useful than a formatted document the user would
rewrite anyway, and it is honest about the gaps rather than papering over them with prose.

It also collapses the failure surface: no rendering means no render failures, no template
overflow, no storage, no second AI provider. What remains is one Groq call per optimization,
validated against a schema and filtered so every candidate-facing value traces to an
`evidenceId`.

## Impact

- **Services**: 13 → 11. `resume-service` and `document-service` deleted, along with their
  Mongo databases' active use.
- **Grounding**: `GroundingValidator` still guards Email generation. JD optimization has no prose
  to ground; its equivalent guarantee is structural — `JdOptimizationService.stripUnknownIds`
  removes any requirement or evidence id not present in the request, and downgrades a match left
  with no evidence to `NONE` rather than letting it stand.
- **Assessment**: JD fit keeps its weighting (coverage 50%, keywords 20%, seniority 20%,
  recency 10%). Only the keyword sub-score changed meaning: it now asks whether the profile
  backs a term, instead of substring-searching generated prose — the more honest measure.
- **Legacy data**: `resume_versions`, `resume_generations`, `cover_letter_versions`,
  `rendered_documents`, `custom_template_assets` and `ats_assessments` have no remaining reader
  or writer. They are **not dropped** — see docs/DATABASE.md for the backup-then-drop procedure.
- **Superseded**: ADR-013, ADR-016, ADR-018, ADR-020, ADR-023, ADR-029 (all resume/cover-letter/
  document/template decisions) and ADR-032's Gemini retention.

---

# ADR-034

## Decision

Add "My Templates" inside Profile: a user uploads a Resume/Cover Letter file once, and every
subsequent JD-optimization handoff *selects* it — never re-uploads it. Concretely:

- **New collection, new service surface, no new microservice.** `profile-service` gains a
  `templates` collection (`Template` domain, `TemplateRepository`, `TemplateService`,
  `TemplateController` — `/api/profile/templates/**`), following the exact same package shape
  and ownership conventions (`@CallerId`, `ApiException.notOwned()` → 404-not-403) every other
  resource in this platform already uses. This is deliberately *not* a resurrection of
  `document-service` — see "What this explicitly is not," below.
- **Storage**: the same private MinIO/S3 bucket the now-deleted `document-service` used to own
  (docker-compose.yml's `minio`/`minio-init` services were never removed by ADR-033, only their
  one Java consumer was) — `profile-service` becomes its new, and only, consumer. Random UUID
  object keys, no public or presigned URL ever issued; every download streams through
  `TemplateController`'s own ownership-checked endpoint.
- **Validation, not analysis**: extension + magic-byte signature (`%PDF-` / zip `PK`) must
  agree, size ≤ 5 MB, filename sanity-checked, exact-duplicate (same SHA-256, per user)
  rejected. No PDFBox/docx4j parsing, no structural field detection, no AI call of any kind —
  the file is stored and returned exactly as uploaded.
- **One default template per user**, not scoped per `TemplateDocumentType` (`RESUME` /
  `COVER_LETTER` / `BOTH`, which stays purely descriptive metadata). Setting a new default
  unsets whichever one previously held it. The first template a user ever saves becomes their
  default automatically.
- **Frontend**: a new `/profile/templates` page (linked from the Profile header, alongside
  "Edit profile") for upload/list/rename/set-default/delete/preview/download — cards with a
  "⋮" actions menu, matching the account menu's own open/close/focus/click-outside pattern.
  `OptimizationResultPage` (the JD-optimization result/handoff page — the only place a
  "template" concept is relevant now that CareerForge doesn't render documents) gains a "Choose
  your template" selector sourced from this library, defaulting to the user's default template;
  selecting one relabels the existing "Copy AI Prompt" action to **"Create with ChatGPT"** and
  enriches the same modal with a status summary ("Selected template / JD optimization: Ready /
  Generation prompt: Ready") plus a **Download Template** action alongside the existing Copy.
  With no template selected, every existing element (button label, modal content, prompt text)
  is byte-for-byte what it already was — this is a strictly additive UI path.
- **The external AI prompt** (`buildOptimizationPrompt`) gains one optional block — a
  `SELECTED TEMPLATE` reference (name, filename, type) — injected only when a template is
  selected, telling the external tool which layout to apply the drafted content to. Nothing
  else about the prompt, the JD-optimization data it carries, or the grounding rules it states
  changed.

## What this explicitly is not

Not a resurrection of `document-service`, and not a partial rollback of ADR-033:

- **No structural analysis.** The deleted `PdfStructureAnalyzer`/`DocxStructureAnalyzer`
  (placeholder detection, field mapping) are not reintroduced in any form.
- **No mail-merge, no rendering.** `PdfMailMerge`/`DocxMailMerge`/openhtmltopdf/Thymeleaf are
  not reintroduced. CareerForge still produces no PDF/DOCX — a saved template's bytes are
  never combined with generated content inside this platform.
- **No AI template analysis, no Gemini.** The task was explicit on both counts, and ADR-033
  already removed Gemini from the codebase entirely (`GeminiClient`, `GEMINI_API_KEY`,
  `google-genai` are gone) — this feature does not touch `ai-service` at all.
- **No new microservice, no revived `document-service`/`resume-service`.** The feature lives
  entirely inside `profile-service`, an already-existing service, following its existing
  conventions.

## Problem

JD optimization already produces the analysis and an external-generation prompt
(`docs/ARCHITECTURE_DECISIONS.md#adr-033`), but the workflow a user actually follows —
"take this data to ChatGPT/Word/Gemini and produce a real document" — had no way to also carry
*which template* to use without asking the user to attach the same file to every single
generation by hand. That's a real, repeated friction point ADR-033 didn't address, and one this
feature closes without reopening any part of the document-generation surface ADR-033
deliberately removed.

## Options

For where the feature lives: (a) a new microservice; (b) inside an existing service. For which
existing service, if (b): profile-service (owns the candidate's own data, and the feature is
explicitly scoped "Profile → My Templates") vs. jd-service/application-service (own the
optimization/handoff surfaces that eventually *consume* the selection).

For storage: (a) MongoDB GridFS / binary field; (b) the existing MinIO/S3 bucket infrastructure
already provisioned (docker-compose.yml never removed it, only document-service's Java client).

For the default-template model: (a) one default per `TemplateDocumentType` (mirroring the old,
now-superseded resume-generation-vs-cover-letter-generation split); (b) one default per user,
unscoped by type.

## Selected Option

(b) for where the feature lives (profile-service specifically, not jd-service/application-
service); (b) for storage; (b) for the default model.

## Reason

A new microservice for a single collection and five CRUD-ish endpoints would repeat exactly the
complexity ADR-033 just finished cutting away, for a feature far smaller than the one that
prompted that cut. profile-service over jd-service/application-service: a template is the
candidate's own asset — uploaded once, independent of any specific JD or optimization run — the
same relationship personal information and evidence already have to profile-service, and
unlike jd-service/application-service it doesn't already own a "file" concept to extend
awkwardly. Consuming this library (the dropdown on `OptimizationResultPage`) is a frontend
concern that reads from profile-service directly, exactly like the page already reads the
profile itself via `profileApi.ts` — no new backend coupling between jd-service and
profile-service was needed or added.

For storage: "do not store large binary files directly in MongoDB unless the existing
architecture already does so" was an explicit constraint, and it doesn't — Mongo here has
always stored metadata, never binaries. Rebuilding GridFS support from scratch when a private,
already-provisioned, already-battle-tested (via the now-deleted document-service) MinIO/S3
bucket sits right there or unused would be pure waste. Reusing it costs one new dependency
(`io.minio:minio`, already centrally version-managed in the root `pom.xml` from before, never
removed) and one small `ObjectStorageService` mirroring the deleted one almost exactly.

For the default model: ADR-033 already deleted the very distinction (separate resume-generation
vs. cover-letter-generation flows) that would have justified a type-scoped default. Modelling
one anyway would be speculative complexity for a product shape that no longer exists — "my
usual template regardless of type" is simply the more honest model of what CareerForge is today.

## Impact

**profile-service** (new): `domain/Template`, `domain/TemplateFileType`,
`domain/TemplateDocumentType`; `repository/TemplateRepository`; `config/StorageProperties`,
`config/MinioConfig` (mirroring the deleted document-service's identically-named classes);
`service/ObjectStorageService` (same mirror); `service/TemplateService` (validation, ownership,
duplicate detection, single-default-per-user invariant — no structural analysis, no AI);
`api/TemplateController` (`/api/profile/templates/**`, multipart upload, streamed download);
`api/dto/TemplateRequests`/`TemplateResponses`. `pom.xml` gains the `io.minio:minio` dependency.
`application.yml` gains a `careerforge.storage.*` block (env-bound, mirrors the deleted
service's identical block). `MongoIndexInitializer` gains two `templates` indexes
(`userId+createdAt`, `userId+sha256`). No new `ErrorCode` — reuses `FILE_REJECTED` (validation),
`RESOURCE_NOT_FOUND`/`ApiException.notOwned()` (ownership), `UPSTREAM_UNAVAILABLE` (storage
failures).

**Root config**: `.env.example` gains the `S3_*`/`MINIO_ROOT_*` block (previously present only
for the now-deleted document-service, since removed from the template — reinstated here, scoped
to profile-service). `docker-compose.yml`'s `profile-service` block gains
`depends_on: minio: { condition: service_healthy }`.

**No change** to `ai-service`, `jd-service`'s `JdOptimizationService`/`JdOptimization` domain,
`assessment-service`, or `application-service`'s email generation — this feature added zero
backend coupling to any of them. The frontend's template selection is purely client-side state
(`useState` on `OptimizationResultPage`), never persisted onto the `JdOptimization` record
itself, keeping jd-service completely untouched.

**Frontend**: new `services/templateApi.ts`; new `features/profile/MyTemplatesPage.tsx` +
`components/{TemplateCard,UploadTemplateModal,RenameTemplateModal}.tsx`; `router.tsx` gains
`/profile/templates`; `ProfileHeaderCard.tsx` gains a "My Templates" link. `OptimizationResultPage.tsx`
gains the "Choose your template" section and template-aware modal wiring;
`OptimizationPromptModal.tsx`'s `buildOptimizationPrompt` gains an optional, additive
`selectedTemplate` parameter (omitted ⇒ byte-identical output to before this ADR) and the modal
itself gains the optional summary panel + Download Template action, both inert unless a
template is selected.

**Tests**: `profile-service` — new `TemplateServiceTest` (23 cases: valid PDF/DOCX upload,
first-upload-becomes-default, name-falls-back-to-filename, empty/oversized/wrong-extension/
mislabelled-signature/duplicate rejection, ownership 404s for list/rename/set-default/delete/
download, rename validation, default-switching including the same-template-again no-op,
delete/download touching storage only for an owned template). Full reactor `mvn verify`: BUILD
SUCCESS, 142 tests passing across every module with tests (auth-service 19, profile-service 23,
jd-service 13, ai-service 57, application-service 30) — zero regression in JD optimization,
evidence selection, or email generation. Frontend: `npm run typecheck` and `npm run build` both
clean; `npm test` unchanged (no unit test files exist yet, same pre-existing harness-only
state). New Playwright coverage authored (`tests/e2e/templates.spec.ts` — upload/list/rename/
download/delete/default-switching/cross-user-ownership; plus one new case in
`jd-optimization.spec.ts` covering the full upload-once → select-at-handoff →
"Create with ChatGPT" → Download Template → prompt-contains-`SELECTED TEMPLATE` path) but **not
executed** — e2e requires the full live stack (all services + MongoDB Atlas + MinIO/Redis),
unavailable in this environment (no Docker daemon, no live credentials); this is stated
here plainly rather than claimed as a pass that wasn't verified. The existing
`jd-optimization.spec.ts` happy-path assertions (`Copy AI Prompt`, `Copy`, `Download JSON`, the
exact prompt content checks) were verified unchanged by inspection: a fresh test user with no
saved templates exercises every one of them exactly as before, since `selectedTemplate` stays
`null` throughout that flow.

**Explicitly not done**: no PDF/DOCX rendering or mail-merge of any kind; no AI/structural
analysis of an uploaded template; no Gemini involvement (already fully removed by ADR-033); no
change to JD-optimization logic, Groq prompts, email generation, assessment logic, or existing
profile-evidence sections; `document-service` was not recreated.

---

# ADR-035

## Decision

`notification-service` is removed from the platform entirely: the module, its reactor
registration, its Docker/Compose service definition, its Prometheus scrape target, its entries
in the local dev-launcher scripts, and the unused `SMTP_*` environment variables that only it
ever would have consumed.

## Problem

The service existed only as a bootstrap skeleton since it was first scaffolded: a
`@SpringBootApplication` main class, Eureka registration, config-server import, and the standard
`/actuator/health` endpoint — and nothing else. No controller, no service layer, no domain
model, no repository, no Redis Stream consumer (despite its own class-level Javadoc describing
one), no SMTP client wiring. An audit of the entire codebase found:

- Zero Feign clients or Redis Stream producers anywhere targeting it.
- Zero gateway route (by design, ADR-012 — but there was also nothing to route to).
- Zero frontend API client or UI surface referencing it.
- Zero database usage (ADR-002 already recorded this).
- Zero callers, internal or external.

A service with a reserved port, a running container, a Prometheus scrape target, and a line in
every dev-launcher script — contributing zero behavior and costing real attention every time
someone reads the architecture, runs the local stack, or wonders why `logs/notification-
service.log` exists — is pure carrying cost with no offsetting value.

## Options

(a) Leave it as a documented, intentional placeholder for a future "transactional/system
email" milestone (address verification, password reset, security alerts — see the now-removed
SMTP section of `docs/EXTERNAL_APIS.md`). (b) Remove it entirely, and re-scaffold it (or fold
the eventual feature into an existing service) if and when that milestone is actually planned.

## Selected Option

(b).

## Reason

A skeleton with no implementation date and no active dependents is not meaningfully different
from not existing, except that it still shows up in the reactor build, the Compose stack, the
metrics dashboard, and every "what services do I need running" checklist — a tax paid
indefinitely for a feature with no committed timeline. Nothing about *when or how* transactional
email eventually gets built is decided by removing this shell; ADR-002's own reasoning for why
it would own no database, and `docs/EXTERNAL_APIS.md`'s removed SMTP section's setup notes, are
preserved here and in git history if that work is ever picked up, at which point it can be
re-scaffolded fresh against whatever the platform looks like then, rather than resurrecting a
year-old empty shell. This mirrors ADR-033's own reasoning for removing `resume-service`/
`document-service` outright rather than leaving them running unused: an empty, uncalled service
is a liability (attack surface, operational cost, a false signal to any reader that the feature
exists), never a neutral placeholder.

## Impact

**Removed**: `services/notification-service/` (entire module — `NotificationApplication`,
`Dockerfile`, `application.yml`/`application-nodocker.yml`); its `<module>` entry in the root
`pom.xml`; its service block in `docker-compose.yml`; its scrape target in
`infrastructure/prometheus/prometheus.yml`; its entries in `scripts/run-local.ps1`'s
`$allServices`/`$ports` and the `notification-service` port in `scripts/stop-local.ps1`'s
cleanup fallback list; the unused `SMTP_*` block in `.env.example` (confirmed read by zero
Java code anywhere in the reactor); the `POST /internal/notifications/send` planned-endpoint
row from `docs/API_CATALOG.md`'s Internal APIs table; the SMTP section of
`docs/EXTERNAL_APIS.md` (the MongoDB/Redis "used by" lists there and elsewhere no longer name
it either).

**Corrected, not removed**: `ADR-012`'s own decision text is left as an accurate historical
record (it was true when written); only the index table's Status column gets a note that the
notification-service half is now moot. `ADR-002`'s "notification-service owns no database in
v1" bullet is likewise left as an accurate historical record rather than rewritten.

**No change** to `application-service`'s email generation (`EmailContentService`,
`EmailGenerationService`, the email APIs, prompts/schema, persistence, or frontend Emails
page/tests) — that is a completely different, fully-implemented, candidate-facing feature that
never depended on `notification-service` in any way. No notification functionality was
implemented as part of this removal, and no replacement API was added — exactly what removing
dead weight means; the eventual system-email feature, if built, starts from a real design, not
a scaffold nobody had touched.

**Tests**: full reactor `mvn verify` — every remaining module (now 9 Spring Boot services +
`platform-common`, down from 10) builds and passes unchanged, since nothing in the reactor
depended on the removed module. Frontend `npm run typecheck`/`npm run build`/`npm test`
unaffected — zero frontend references existed before or after.
