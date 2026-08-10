# Database

MongoDB Atlas is the only persistent database. Spring Data MongoDB is the only access
layer — no JPA, no Hibernate, no Flyway, no MySQL (ADR-001).

**Current state:** Milestone 1 — ownership, models and indexes are specified below.
Collections are created by their owning service as it is implemented, milestone by
milestone. No collection exists in Atlas yet.

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
    └── application_status_history
```

`ai-service` and `notification-service` own no database (ADR-002).

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

**Evidence identity is the spine of the whole product.** Every factual item carries an
immutable `evidenceId` (`EXP-004`, `PROJ-002`, `CERT-001`, …), assigned once and never
reused even after deletion. `verificationStatus` (`SELF_REPORTED` · `DOCUMENT_BACKED` ·
`VERIFIED`) travels with it. The AI receives only this ID-labelled inventory, and grounding
validation resolves every generated claim back to these IDs.

**`profile_versions`** — an immutable snapshot of the whole profile: `userId`, `version`,
`snapshot`, `createdAt`, `reason`. A resume records the profile version it was generated
from, so an old resume stays explainable after the profile changes.

### careerforge_jd

**`job_descriptions`** — `userId`, `sourceType` (`TEXT` · `URL` · `FILE`), `sourceUrl`,
`sourceFileKey`, `title`, `company`, `status` (`DRAFT` · `EXTRACTED` · `CONFIRMED` ·
`REJECTED`), `currentVersion`, `confirmedAt`, `confirmedVersion`.

Generation is impossible unless `status = CONFIRMED` (blueprint §4).

**`jd_versions`** — `jobDescriptionId`, `version`, `rawText`, `normalisedText`,
`extractionMethod`, `contentHash`, `createdAt`. Immutable; edits create a new version so
the confirmation the user gave always points at exact bytes.

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
`templateId`, `templateVersion`, `status` (`QUEUED` · `SELECTING_EVIDENCE` · `GENERATING` ·
`VALIDATING` · `RENDERING` · `COMPLETED` · `FAILED`), `currentVersion`, `failureCode`,
`idempotencyKey`, `startedAt`, `completedAt`.

`idempotencyKey` makes at-least-once Redis Stream delivery safe (ADR-005): a redelivered
job finds the existing generation and returns it instead of calling Groq again.

**`resume_versions`** — `resumeGenerationId`, `userId`, `version`, `content` (the validated
resume JSON), `evidenceMap` (bullet → evidenceId[]), `groundingReport`, `promptVersion`,
`modelId`, `tokenUsage`, `createdAt`. Immutable.

`evidenceMap` is what makes the product auditable: every rendered sentence can be traced to
the profile items that justify it.

**`templates`** — catalogue metadata only (ADR-004): `templateId` (`classic` · `modern-ats`
· `professional`), `displayName`, `description`, `thumbnailKey`, `supportedFormats`,
`currentVersion`, `enabled`, `atsSafe`.

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

### careerforge_document

**`rendered_documents`** *(ADR-002)* — `userId`, `resumeVersionId`, `documentType`
(`RESUME` · `COVER_LETTER`), `format` (`PDF` · `DOCX`), `objectKey` (random UUID path, never
guessable), `bucket`, `sha256`, `byteSize`, `pageCount`, `templateId`, `templateVersion`,
`machineReadable`, `renderedAt`, `renderEngineVersion`.

The bucket is private. Only presigned URLs, valid for 300 seconds, are ever issued, and
only after `userId` matches the caller.

### careerforge_application

**`applications`** — `userId`, `jobDescriptionId`, `resumeVersionId`,
`coverLetterVersionId`, `renderedDocumentIds[]`, `company`, `jobTitle`, `status`
(`DRAFT` · `READY` · `APPLIED` · `INTERVIEWING` · `OFFER` · `REJECTED` · `WITHDRAWN`),
`appliedAt`, `emailSubject`, `emailBodyRef`, `gmailDraftId`, `notes`.

**`application_status_history`** — `applicationId`, `userId`, `fromStatus`, `toStatus`,
`changedAt`, `note`. Append-only.

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
| jd | `job_descriptions` | `userId + createdAt` | compound, desc | dashboard listing |
| jd | `job_descriptions` | `userId + status` | compound | "awaiting confirmation" |
| jd | `jd_versions` | `jobDescriptionId + version` | unique | version retrieval |
| jd | `jd_analyses` | `jdVersionId` | unique | one analysis per JD version |
| resume | `resume_generations` | `userId + createdAt` | compound, desc | history |
| resume | `resume_generations` | `idempotencyKey` | unique, sparse | safe redelivery |
| resume | `resume_versions` | `resumeGenerationId + version` | unique | version retrieval |
| resume | `resume_versions` | `userId + createdAt` | compound, desc | recent versions |
| resume | `templates` | `templateId` | unique | catalogue lookup |
| assessment | `ats_assessments` | `resumeVersionId` | unique | one ATS score per version |
| assessment | `jd_fit_assessments` | `resumeVersionId + jdVersionId` | unique | one fit score per pair |
| assessment | `recommendations` | `resumeVersionId` | — | fetch with the score |
| document | `rendered_documents` | `userId + createdAt` | compound, desc | artifact listing |
| document | `rendered_documents` | `resumeVersionId + format` | unique | one artifact per format |
| document | `rendered_documents` | `objectKey` | unique | storage integrity |
| application | `applications` | `userId + createdAt` | compound, desc | history |
| application | `applications` | `userId + status` | compound | status board |
| application | `application_status_history` | `applicationId + changedAt` | compound | timeline |

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
