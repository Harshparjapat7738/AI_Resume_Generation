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
