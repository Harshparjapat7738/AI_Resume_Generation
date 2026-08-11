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
| [ADR-012](#adr-012) | ai-service and notification-service are not routed through the public gateway | Accepted |
| [ADR-013](#adr-013) | Resume generation runs synchronously — no Redis Streams job queue yet | Accepted |
| [ADR-014](#adr-014) | ATS scoring checks structured resume content, not a rendered document | Accepted |
| [ADR-015](#adr-015) | JD URL extraction: SSRF guard + JSON-LD when present, generic text otherwise | Accepted |
| [ADR-016](#adr-016) | Template system Phase 1: built-in catalogue only, upload/online deferred | Accepted |
| [ADR-017](#adr-017) | The central `Application` aggregate references artifacts; generation lifecycle ≠ tracking lifecycle | Accepted |
| [ADR-018](#adr-018) | document-service renders real Resume PDFs synchronously; download streams through the service rather than a presigned URL | Accepted |
| [ADR-019](#adr-019) | Email generation: deterministic subject/frame + one grounded, model-written highlight paragraph | Accepted |
| [ADR-020](#adr-020) | Cover-letter generation orchestrated from application-service, mirroring resume-service's two-stage pipeline | Accepted |
| [ADR-021](#adr-021) | Google OAuth state/PKCE store is Redis, not an HTTP session; account linking trusts only a Google-verified email | Accepted |
| [ADR-022](#adr-022) | "Generate All" is one `Application`, three independently-tracked outputs generated sequentially through the existing single-output pipelines | Accepted |

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
