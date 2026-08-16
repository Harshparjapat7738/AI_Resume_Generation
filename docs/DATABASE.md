# Database

MongoDB Atlas is the only persistent database. Spring Data MongoDB is the only access
layer — no JPA, no Hibernate, no Flyway, no MySQL (ADR-001).

**Current state:** ownership, models and indexes are specified below; collections are
created by their owning service as it is implemented. Real, live in Atlas today: `auth.users`
+ `refresh_tokens` + `oauth_accounts` + `security_events` (Google OAuth sign-in — only
`OAUTH_LINKED` writes to `security_events` today; `LOGIN_SUCCESS`/`LOGIN_FAILURE`/
`REFRESH_REUSE` are documented but not yet wired into the pre-existing password flow),
`profile.profiles` (all six evidence sections — personal info,
education, experience, skills, projects, certifications, achievements — plus
`evidenceSequences`; `profile_versions` is still planned), `jd.job_descriptions` +
`jd_versions` + `jd_analyses` (text and SSRF-guarded URL intake — ADR-015),
`resume.resume_generations` +
`resume_versions` + `templates` (synchronous — ADR-013; built-in template catalogue —
ADR-016), `assessment.ats_assessments` + `jd_fit_assessments` (content-scoped — ADR-014),
`application.applications` + `application_status_history` + `emails` + `cover_letter_versions`
(references only, generation lifecycle — ADR-017; email generation — ADR-019; cover-letter
generation — ADR-020). Everything else below remains a design for a later milestone.

---

## 1. Atlas architecture

One cluster, logical database per service:

```text
MongoDB Atlas Cluster
│
├── careerforge_auth           ← auth-service
│   ├── users
│   ├── oauth_accounts
│   ├── refresh_tokens
│   └── security_events
│
├── careerforge_profile        ← profile-service
│   ├── profiles
│   └── profile_versions
│
├── careerforge_jd             ← jd-service
│   ├── job_descriptions
│   ├── jd_versions
│   └── jd_analyses
│
├── careerforge_resume         ← resume-service
│   ├── resume_generations
│   ├── resume_versions
│   └── templates
│
├── careerforge_assessment     ← assessment-service
│   ├── ats_assessments
│   ├── jd_fit_assessments
│   └── recommendations
│
├── careerforge_document       ← document-service   (ADR-002)
│   └── rendered_documents
│
└── careerforge_application    ← application-service
    ├── applications
    ├── application_status_history
    ├── emails
    └── cover_letter_versions
```

`ai-service` owns no database (ADR-002).

### Ownership rule

A service connects to exactly one database, using an Atlas user scoped to that database
only. Enforcement is at three levels:

1. **Configuration** — each service's `application.yml` names a single database.
2. **Credentials** — the Atlas user has `readWrite` on its own database and no grant
   elsewhere, so a coding mistake fails with an authorisation error rather than corrupting
   another service's data.
3. **Review** — a repository interface referencing a collection outside its service's list
   is rejected in code review.

```text
Profile Service ──✗──► careerforge_auth.users        forbidden
Profile Service ──✓──► GET auth-service /api/auth/me  correct
```

---

## 2. Conventions

Every document carries:

| Field | Type | Purpose |
|---|---|---|
| `_id` | ObjectId | primary key; never a natural key |
| `userId` | String (ObjectId hex) | owner; present on every user-scoped document |
| `createdAt` | Instant (UTC) | set once |
| `updatedAt` | Instant (UTC) | maintained by auditing |
| `version` | Long | `@Version`, optimistic locking |
| `schemaVersion` | int | document-shape version for tolerant reads |

- Timestamps are UTC `Instant`. No local time is stored anywhere.
- `@Document` classes never leave the service — controllers map to DTOs.
- There is no migration tool. Shape changes use tolerant readers plus `schemaVersion`, and
  a backfill job when a rewrite is genuinely required.
- `spring.data.mongodb.auto-index-creation` is **false**. Indexes are created explicitly by
  an `IndexInitialiser` bean per service so index changes are reviewable and auditable.

### Embed vs reference

Embed when the data belongs to one aggregate, is read with it, and is bounded — profile
sections, JD requirement lists, ATS check breakdowns.

Reference when the data is large, independently versioned, independently queried, or
unbounded — resume versions, rendered artifacts, application history, security events.

The blueprint's rule holds: **never build one giant user document** containing every
resume, cover letter and application.

---

## 3. Document models

### careerforge_auth

**`users`**

| Field | Type | Notes |
|---|---|---|
| `_id` | ObjectId | |
| `email` | String | lowercased, trimmed; unique |
| `passwordHash` | String | BCrypt cost 12; null for OAuth-only accounts |
| `displayName` | String | |
| `emailVerified` | boolean | |
| `roles` | String[] | `["ROLE_USER"]` |
| `status` | enum | `ACTIVE` · `LOCKED` · `DISABLED` · `PENDING_DELETION` |
| `failedLoginAttempts` | int | reset on success |
| `lockedUntil` | Instant | null when not locked |
| `lastLoginAt` | Instant | |

`passwordHash` is never selected into a DTO and never logged.

**`oauth_accounts`** — `userId`, `provider` (`GOOGLE`), `providerUserId`, `emailAtProvider`,
`linkedAt`. Refresh/access tokens from the provider are stored encrypted only where a
long-lived integration requires them (Gmail); plain sign-in stores none.

**`refresh_tokens`** — `userId`, `tokenHash` (SHA-256 — the raw token is never stored),
`familyId`, `previousTokenHash`, `issuedAt`, `expiresAt`, `revokedAt`, `revokedReason`,
`userAgentHash`, `ipHash`.

Rotation: each refresh issues a successor in the same `familyId` and revokes the presented
token. Presenting an already-revoked token means theft — the whole family is revoked and a
`REFRESH_REUSE` security event is written.

**`security_events`** — `userId`, `type` (`LOGIN_SUCCESS`, `LOGIN_FAILURE`,
`PASSWORD_CHANGED`, `REFRESH_REUSE`, `ACCOUNT_LOCKED`, `OAUTH_LINKED`, `EXPORT_REQUESTED`,
`DELETION_REQUESTED`), `ipHash`, `userAgentHash`, `correlationId`, `occurredAt`. Append-only.

### careerforge_profile

**`profiles`** — one per user, embedding the bounded sections:

```text
profiles
├── userId (unique)
├── personalInformation { fullName, headline, email, phone, links[] }
├── address            { city, region, country }
├── education[]        { evidenceId, institution, degree, field, start, end, grade }
├── experiences[]      { evidenceId, company, title, employmentType, start, end,
│                        current, location, bullets[], technologies[], metrics[] }
├── skills[]           { evidenceId, name, category, proficiency, yearsOfExperience }
├── certifications[]   { evidenceId, name, issuer, issuedOn, expiresOn, credentialId }
├── projects[]         { evidenceId, name, description, role, technologies[],
│                        metrics[], start, end }
├── achievements[]     { evidenceId, title, description, date }
├── completionScore
└── currentVersion
```

**Implementation delta from the shape above** (all six sections are real, live in Atlas):
`education[]` and `certifications[]` each carry one small additive field beyond what's
listed here (`description`, `credentialUrl`) for context a bare degree/institution or
credential ID can't carry; `projects[]` additionally carries `githubUrl`/`liveUrl`. `address`,
`completionScore` and `currentVersion` as *stored* fields are not implemented — completion
is instead computed client-side from real section data
(`computeProfileCompletion()`, `docs/API_INTEGRATION.md`) rather than persisted, and
`verificationStatus` is not implemented (every item is implicitly self-reported today).

**Evidence identity is the spine of the whole product.** Every factual item carries an
immutable `evidenceId` (`EXP-004`, `PROJ-002`, `CERT-001`, …), assigned once and never
reused even after deletion — implemented via `Profile.nextEvidenceId(prefix)`, a per-user,
per-prefix counter (`EXP`, `EDU`, `SKILL`, `PROJ`, `CERT`, `ACH`). `verificationStatus`
(`SELF_REPORTED` · `DOCUMENT_BACKED` · `VERIFIED`) is designed but not yet implemented. The
AI receives only this ID-labelled inventory (`GET /api/profile/evidence`, combined across
all six sections), and grounding validation resolves every generated claim back to these IDs.

**`profile_versions`** — an immutable snapshot of the whole profile: `userId`, `version`,
`snapshot`, `createdAt`, `reason`. A resume records the profile version it was generated
from, so an old resume stays explainable after the profile changes.

**`templates`** — implemented (ADR-034), a separate collection from `profiles`: many rows per
user (unlike the one-per-user profile document), one per uploaded Resume/Cover Letter file.

```text
templates
├── userId
├── name              display name — falls back to the filename minus its extension
├── originalFilename
├── fileType          PDF | DOCX
├── documentType      RESUME | COVER_LETTER | BOTH — descriptive only, never branches behaviour
├── objectKey         MinIO/S3 object key (random UUID) — never serialised over HTTP
├── bucket
├── byteSize
├── sha256            duplicate-upload detection, scoped per user
├── isDefault         one true per user at a time, not scoped per documentType (ADR-034)
├── createdAt
└── updatedAt
```

The file bytes themselves live in the same private MinIO/S3 bucket the now-deleted
document-service used to own — profile-service is its only remaining consumer. No structural
analysis, mail-merge mapping, or AI involvement of any kind touches an uploaded file; what a
user downloads back is byte-for-byte what they uploaded.

### careerforge_jd

**`job_descriptions`** — `userId`, `sourceType` (`TEXT` and `URL` implemented; `FILE`
planned), `sourceUrl`, `sourceFileKey` (planned, `FILE` only), `title`, `company`, `status`
(`DRAFT` · `EXTRACTED` · `CONFIRMED` · `REJECTED`), `currentVersion`, `confirmedAt`,
`confirmedVersion`, plus three fields additive to the originally documented shape —
**`location`**, **`skillsSummary`**, **`experienceSummary`** — populated only for a
`sourceType = URL` document whose page embedded schema.org `JobPosting` JSON-LD (see
ARCHITECTURE_DECISIONS.md ADR-015); null otherwise, never guessed from unstructured HTML.

Generation is impossible unless `status = CONFIRMED` (blueprint §4).

**`jd_versions`** — `jobDescriptionId`, `version`, `rawText`, `normalisedText`,
`extractionMethod`, `contentHash`, `createdAt`. Immutable; edits create a new version so
the confirmation the user gave always points at exact bytes.

**`jd_optimizations`** (ADR-033) — `userId`, `jobDescriptionId`, `jdVersionId`,
`optimisation` (ai-service's validated JSON, stored verbatim), `citedEvidenceIds[]`,
`promptVersion`, `modelId`, `createdAt`. Unique index on `jdVersionId` (one current result per
JD version, replaced in place on refresh rather than accumulating), plus `userId + createdAt`.
Holds no copy of the JD text or the profile — only ids pointing back at both.

**`jd_analyses`** — `jobDescriptionId`, `jdVersionId`, `title`, `company`, `location`,
`employmentType`, `seniority`, `yearsOfExperience`, `keywords[]`, `analysedAt`,
`promptVersion`, `modelId`, and:

```text
requirements[]
├── requirementId      REQ-001
├── text
├── type               HARD_REQUIRED | PREFERRED | RESPONSIBILITY | SKILL |
│                      TECHNOLOGY | EDUCATION | CERTIFICATION
├── weight
└── normalisedTerms[]
```

### careerforge_resume

**`resume_generations`** — `userId`, `jobDescriptionId`, `jdVersionId`, `profileVersion`,
`templateId`, `templateVersion` (the latter two implemented — resolved from the `templates`
catalogue at generation time, defaulting to `classic` when unspecified, see ADR-016), `status`
(`QUEUED` · `SELECTING_EVIDENCE` · `GENERATING` · `VALIDATING` · `RENDERING` · `COMPLETED` ·
`FAILED` — simplified to `GENERATING`/`COMPLETED`/`FAILED` today per ADR-013), `currentVersion`,
`failureCode`, `idempotencyKey`, `startedAt`, `completedAt`.

`idempotencyKey` makes at-least-once Redis Stream delivery safe (ADR-005): a redelivered
job finds the existing generation and returns it instead of calling Groq again.

**`resume_versions`** — `resumeGenerationId`, `userId`, `version`, `content` (the validated
resume JSON), `evidenceMap` (bullet → evidenceId[]), `groundingReport`, `promptVersion`,
`modelId`, `tokenUsage`, `createdAt`. Immutable.

`evidenceMap` is what makes the product auditable: every rendered sentence can be traced to
the profile items that justify it.

**`templates`** — catalogue metadata only (ADR-004), implemented (Phase 1 — built-in only, see
ADR-016): `_id` = `templateId` (`classic` · `modern-ats` · `professional`), `name`,
`description`, `previewKey` (frontend maps this to a real local preview component — no static
thumbnail-asset pipeline exists, so this deliberately isn't a `thumbnailKey`/image reference),
`type` (`RESUME` · `COVER_LETTER` · `EMAIL` — only `RESUME` has rows today), `version`,
`status` (`ACTIVE` · `DISABLED`, supersedes the originally-sketched boolean `enabled`),
`source` (`BUILT_IN` · `CUSTOM_UPLOAD` · `ONLINE` — only `BUILT_IN` has rows today),
`ownerUserId` (nullable; reserved for `CUSTOM_UPLOAD`), `supportedFormats`, `atsSafe`,
`createdAt`.

`resume_generations.templateId`/`templateVersion` and `resume_versions.templateId`/
`templateVersion` (both implemented) record which template a generation used — the selection
is denormalised onto the version too so it survives independent of the generation row.

### careerforge_assessment

**`ats_assessments`** — `resumeVersionId` (unique), `userId`, `renderedDocumentId`,
`totalScore` (0–100, one decimal), `checks[]`, `assessedAt`, `engineVersion`.

```text
checks[]
├── checkId            MACHINE_READABLE_TEXT | ATS_SAFE_LAYOUT | STANDARD_HEADINGS |
│                      CONTACT_PARSING | DATE_CONSISTENCY | NO_INFO_ONLY_IMAGES |
│                      STANDARD_FONTS | HEADER_FOOTER_SAFETY | FILENAME |
│                      LENGTH_SUITABILITY
├── weight             20 15 12 10 10 10 8 5 5 5   (sums to 100)
├── passRatio          0.0–1.0 — fractional, so 99.9 is reachable (ADR-008)
├── subChecks[]        { name, passed, detail }
└── earned             weight × passRatio
```

`engineVersion` is stored so a score can be reproduced exactly after the engine changes.

**`jd_fit_assessments`** — `resumeVersionId` + `jdVersionId` (unique together), `userId`,
`compatibilityScore`, `coverage`, `keywordMatch`, `seniorityMatch`, `recency`,
`requirementMatches[]` (`requirementId`, `matchStrength` `STRONG|PARTIAL|NONE`,
`evidenceIds[]`, `reason`), `unmetHardRequirements[]`, `readinessBand`, `bandRule`.

`bandRule` records which ordered rule fired (ADR-009), so the UI can always answer "why did
I get this band?".

**`recommendations`** — `resumeVersionId`, `userId`, `items[]` (`type`, `severity`,
`message`, `relatedRequirementId`). Recommendations describe gaps; they never suggest
adding a fact the candidate does not have.

### careerforge_document — implemented (ADR-018)

**`rendered_documents`** *(ADR-002)* — `userId`, `resumeVersionId`, `documentType`
(`RESUME` implemented · `COVER_LETTER` reserved, unused until application-service produces
one), `format` (`PDF` implemented · `DOCX` reserved, docx4j dependency present but unused),
`objectKey` (random UUID path, never guessable), `bucket`, `sha256`, `byteSize`, `pageCount`,
`templateId`, `templateVersion`, `renderedAt`, `renderEngineVersion`. One row per
`(resumeVersionId, format)` — re-rendering replaces it rather than accumulating history.

Deviation from the original sketch: `machineReadable` is not implemented — every PDF this
service produces has a real text layer (openhtmltopdf never rasterises), so the field would
be a constant `true` with no query or business purpose yet; adding it is additive whenever a
second, genuinely different production path (e.g. a scanned upload) exists to distinguish.

The bucket is private (no anonymous read or write — see `minio-init` in `docker-compose.yml`).
Deviation from the original sketch's presigned-URL download: bytes are streamed through
document-service's own authenticated endpoint rather than a presigned MinIO URL, so the
browser never talks to object storage directly and no storage endpoint or credential is ever
part of a response — see ADR-018.

### careerforge_application — implemented (ADR-017, ADR-019, ADR-020)

**`applications`** — the central aggregate: `userId`, `jobDescriptionId`, `jobTitle`,
`company` (the latter two denormalised from jd-service at creation, same pattern as
`resume_versions.jobTitle`/`company`), `generationType`
(`RESUME_ONLY` · `COVER_LETTER_ONLY` · `EMAIL_ONLY` · `ALL` — `RESUME_ONLY`, `EMAIL_ONLY` and
`COVER_LETTER_ONLY` can each reach `COMPLETED` today; `ALL` cannot until a combined pipeline
exists), `templateId`
(validated against resume-service's catalogue when supplied, ADR-016), `resumeVersionId`
(reference — never a copy of resume content), `coverLetterVersionId` (reference into this
service's own `cover_letter_versions` collection below, ADR-020), `emailId`
(reference into this service's own `emails` collection below, ADR-019), `assessed` (boolean —
whether assessment-service had a scored assessment for `resumeVersionId` the last time it was
attached; there is no separate assessment ID to store, since assessment-service keys both
scores uniquely by `resumeVersionId + userId`, ADR-010), `status`
(`DRAFT` · `PROCESSING` · `COMPLETED` · `FAILED` — the **generation** lifecycle, not the
job-tracking lifecycle the original sketch above described; see ADR-017), `failureCode`,
`createdAt`, `updatedAt`.

Deviation from the original sketch: `READY` · `APPLIED` · `INTERVIEWING` · `OFFER` ·
`REJECTED` · `WITHDRAWN`, `appliedAt`, `emailSubject`, `emailBodyRef`, `gmailDraftId`, `notes`
and `renderedDocumentIds[]` are **not implemented** — they describe a job-search tracking
board layered on top of a completed application, a separate later feature from "has
generation finished" (ADR-017). Adding them is additive whenever that feature is built.

**`application_status_history`** — `applicationId`, `userId`, `fromStatus`, `toStatus`,
`changedAt`, `note`. Append-only; one row per `PATCH /api/applications/{id}/status` call.

**`emails`** *(ADR-019)* — immutable, one per generation, versioned per application, mirroring
`resume_versions`: `applicationId`, `userId`, `version`, `subject`, `body` (the single field
the UI renders — the fully assembled text), `highlights[]` (the model-generated
`{text, evidenceIds}` paragraph(s), kept for audit traceability), `groundingReport`,
`removedParagraphs` (paragraphs the grounding degrade path dropped, replaced with a
deterministic fallback sentence in `body`), `promptVersion`, `modelId`, `createdAt`.

`subject` and the greeting/closing/sign-off frame of `body` are never model output — they're
assembled from `applications.jobTitle`/`company` and the candidate's own stated name
(profile-service), so the two facts most load-bearing for "is this the right email" can never
be hallucinated. Only the highlight paragraph(s) are generated, and only after passing the
same grounding check every other generated statement in this product passes.

**`cover_letter_versions`** *(ADR-020)* — immutable, one per generation, versioned per
application, mirroring `resume_versions`/`emails`: `applicationId`, `userId`,
`jobDescriptionId`, `jobTitle`, `company` (both denormalised from the confirmed JD analysis
at generation time, not from `applications.jobTitle`/`company`, since the analysis is the
fresher source), `version`, `content` (the validated JSON —
`greeting`/`openingParagraph`/`bodyParagraphs[]`/`closingParagraph`/`signOff`; only
`greeting`/`signOff` are ungrounded boilerplate, everything else carries `{text,
evidenceIds}`), `groundingReport`, `removedParagraphs` (paragraphs the grounding degrade path
dropped entirely — unlike email, a letter paragraph has no safe deterministic fallback to
substitute), `promptVersion`, `modelId`, `createdAt`.

Unlike `emails`, every paragraph here is model-generated and grounded — there is no
deterministic assembly step, because a letter has no fixed subject-line-equivalent fact to
protect the same way email's subject does; the job title and company are instead added to
`GroundingValidator`'s allowed-context set (ADR-020) so they can be named honestly without a
citation, while every other claim in the letter is checked exactly like a resume bullet.

---

## 4. Indexes

Created explicitly at startup by each service's index initialiser.

| Database | Collection | Index | Type | Why |
|---|---|---|---|---|
| auth | `users` | `email` | unique | login lookup; prevents duplicate accounts |
| auth | `oauth_accounts` | `provider + providerUserId` | unique | OAuth identity resolution |
| auth | `oauth_accounts` | `userId` | — | list linked providers |
| auth | `refresh_tokens` | `tokenHash` | unique | rotation lookup |
| auth | `refresh_tokens` | `familyId` | — | family-wide revocation on reuse |
| auth | `refresh_tokens` | `expiresAt` | **TTL, expireAfterSeconds 0** | expired tokens self-purge |
| auth | `security_events` | `userId + occurredAt` | compound, desc | audit timeline |
| profile | `profiles` | `userId` | unique | one profile per user |
| profile | `profile_versions` | `userId + version` | unique | version history |
| profile | `templates` | `userId + createdAt` | compound, desc | list-by-owner, newest first (ADR-034) — distinct from the legacy `resume.templates` rows below |
| profile | `templates` | `userId + sha256` | compound | duplicate-upload detection |
| jd | `job_descriptions` | `userId + createdAt` | compound, desc | dashboard listing |
| jd | `job_descriptions` | `userId + status` | compound | "awaiting confirmation" |
| jd | `jd_versions` | `jobDescriptionId + version` | unique | version retrieval |
| jd | `jd_analyses` | `jdVersionId` | unique | one analysis per JD version |
| resume | `resume_generations` | `userId + createdAt` | compound, desc | history |
| resume | `resume_generations` | `idempotencyKey` | unique, sparse | safe redelivery |
| resume | `resume_versions` | `resumeGenerationId + version` | unique | version retrieval |
| resume | `resume_versions` | `userId + createdAt` | compound, desc | recent versions |
| resume | `templates` | `templateId` | unique | catalogue lookup (the Mongo `_id`, unique by construction) |
| resume | `templates` | `type + status` | compound | active-catalogue listing |
| resume | `templates` | `source + ownerUserId` | compound | future custom-upload ownership lookup |
| assessment | `ats_assessments` | `resumeVersionId` | unique | one ATS score per version |
| assessment | `jd_fit_assessments` | `resumeVersionId + jdVersionId` | unique | one fit score per pair |
| assessment | `recommendations` | `resumeVersionId` | — | fetch with the score |
| document | `rendered_documents` | `userId + renderedAt` | compound, desc | artifact listing |
| document | `rendered_documents` | `resumeVersionId + format` | unique | one artifact per format |
| document | `rendered_documents` | `objectKey` | unique | storage integrity |
| application | `applications` | `userId + createdAt` | compound, desc | history |
| application | `applications` | `userId + status` | compound | status board |
| application | `application_status_history` | `applicationId + changedAt` | compound | timeline |
| application | `emails` | `applicationId + version` | compound, desc | latest version lookup |
| application | `emails` | `userId + createdAt` | compound, desc | listing |
| application | `cover_letter_versions` | `applicationId + version` | compound, desc | latest version lookup |
| application | `cover_letter_versions` | `userId + createdAt` | compound, desc | listing |

Every user-scoped index leads with `userId`, so the index that serves the query is also the
one that enforces tenant scoping.

**TTL indexes** are used only for genuinely temporary data: `refresh_tokens.expiresAt`.
They are deliberately *not* used on JDs, resumes or applications — that data is the user's
and is removed only on explicit request.

---

## 5. Transactions

Default: **no transactions.** Aggregates are designed so a single-document write is
atomic and sufficient.

Transactions are permitted in exactly two places:

1. **Refresh-token rotation** — revoke the presented token and insert its successor
   atomically, so a crash mid-rotation cannot leave the user without a valid token or
   leave two live tokens in one family.
2. **Account deletion** — remove a user's documents within one database atomically.

Explicitly **not** transactional: the AI generation workflow. It is asynchronous,
long-running and idempotent; wrapping it would hold a transaction open across an external
API call (blueprint §7).

Note that Atlas transactions require a replica set — always true on Atlas, and true for the
`mongodb-atlas-local` test image, but not for a bare single-node `mongod`.

---

## 6. Data retention and privacy

| Data | Retention | Mechanism |
|---|---|---|
| Refresh tokens | until expiry | TTL index |
| Security events | 12 months | scheduled purge job (M9) |
| Job descriptions, resumes, applications | until the user deletes them | explicit user action |
| Rendered artifacts | with their resume version | cascade on deletion |
| Uploaded source files | 24 hours after extraction | storage lifecycle rule |
| Groq request/response bodies | never persisted | metrics and token counts only |

**GDPR / DPDP (Milestone 9).** Export produces a single archive assembled by asking each
service for that user's data over its own API. Erasure fans out the same way and is
verified per service; artifacts are removed from object storage before the metadata that
locates them. Both actions write a `security_event`.

---

## 7. Connection, backup and monitoring

**Connection settings** (per service, via config-server):

```text
maxPoolSize                 20      per service instance
minPoolSize                  5
maxIdleTimeMS            60000
connectTimeoutMS         10000
socketTimeoutMS          30000
serverSelectionTimeoutMS  5000      fail fast rather than hang a request thread
retryWrites               true
w                       majority
readPreference     primaryPreferred
tls                       true      always; never disabled, including locally
```

**Credentials** — one Atlas database user per service per environment, scoped `readWrite`
to that service's database only. Development, staging and production use separate clusters
or at minimum separate users and databases. Rotation is quarterly and on any suspected
exposure. The connection string lives only in `.env` / the secrets manager, never in Git
and never in a Dockerfile.

**Network** — Atlas IP access list restricted to known egress addresses; production uses
private endpoints / VPC peering. MongoDB is never reachable from the browser.

**Backups** — Atlas continuous cloud backup with point-in-time recovery, 7-day PITR window
and 30-day snapshot retention. Restores are rehearsed into a scratch cluster quarterly;
an unrehearsed backup is not a backup.

**Monitoring and alerts** — Atlas alerts on connection saturation, replication lag,
slow queries (>100 ms), disk utilisation and authentication failures, routed to the team
channel. Application-side, Micrometer exposes MongoDB command latency to Prometheus.

---

## 8. Definition of done for the database layer

Tracks blueprint §30A:

- [ ] Atlas development environment configured
- [ ] Development and production credentials separated
- [x] Each service has explicit collection ownership *(documented above)*
- [ ] Required indexes defined and tested against real query patterns
- [ ] Repository tests run against an isolated MongoDB test environment (ADR-003)
- [x] No service directly accesses another service's collections *(enforced by config,
      credentials and review)*
- [x] Object files stored in MinIO/S3 rather than MongoDB
- [x] Backup and recovery strategy documented
- [ ] Atlas monitoring and alerts configured for production
- [x] Connection timeouts and pooling specified
- [x] Connection strings never committed *(`.env` gitignored; Gitleaks in CI)*


---

## Legacy collections (ADR-033) — retained, not dropped

Resume and cover-letter generation, document rendering and ATS scoring were removed. These
collections have **no remaining reader or writer** in the codebase, verified repository-wide:

| Collection | Former owner | Reads | Writes |
|---|---|---|---|
| `resume_versions` | resume-service | NONE | NONE |
| `resume_generations` | resume-service | NONE | NONE |
| `cover_letter_versions` | application-service | NONE | NONE |
| `rendered_documents` | document-service | NONE | NONE |
| `custom_template_assets` | document-service | NONE | NONE |
| `ats_assessments` | assessment-service | NONE | NONE |

They are deliberately **not dropped** — they hold real user history. Procedure when you choose
to remove them:

```bash
# 1. Back up first — this is the only copy.
mongodump --uri "$MONGODB_URI" --db careerforge_resume     --out ./backup-adr033
mongodump --uri "$MONGODB_URI" --db careerforge_document   --out ./backup-adr033
mongodump --uri "$MONGODB_URI" --db careerforge_application --collection cover_letter_versions --out ./backup-adr033
mongodump --uri "$MONGODB_URI" --db careerforge_assessment --collection ats_assessments        --out ./backup-adr033

# 2. Verify the dump restores into a scratch database before deleting anything.

# 3. Then, and only then:
#    careerforge_resume and careerforge_document can be dropped whole (no live collections).
#    In the other two, drop only the named collections — the databases are still in use.
mongosh "$MONGODB_URI" --eval 'db.getSiblingDB("careerforge_application").cover_letter_versions.drop()'
mongosh "$MONGODB_URI" --eval 'db.getSiblingDB("careerforge_assessment").ats_assessments.drop()'
```

`MONGODB_DB_RESUME` and `MONGODB_DB_DOCUMENT` can be removed from `.env`/`.env.example` at the
same time.
