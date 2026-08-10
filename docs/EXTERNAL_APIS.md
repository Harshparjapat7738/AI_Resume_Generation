# External APIs

Every third-party service the application depends on: what it is for, which variables
configure it, which service uses it, how to set it up, and how to handle its credentials.

**This file documents variable names only. It never contains a value.** Real values live
in `.env` (gitignored) locally and in a secrets manager in production.

| Service | Used by | Required for | Credential reaches the browser? |
|---|---|---|---|
| MongoDB Atlas | all data-owning services | everything | **Never** |
| Redis | gateway, auth, jd, resume, ai, document, notification | rate limiting, cache, job queues | **Never** |
| Groq | ai-service only | JD analysis, resume/cover-letter generation | **Never** |
| Google OAuth | auth-service only | Google sign-in | Client ID only |
| MinIO | document-service (dev) | artifact storage | **Never** |
| Amazon S3 | document-service (prod) | artifact storage | **Never** |
| Gmail API | application-service | creating drafts | Client ID only |
| SMTP | notification-service | transactional email | **Never** |

---

## MongoDB Atlas

**Purpose**
Primary persistent database. One cluster, one logical database per service.

**Environment variables**

```env
MONGODB_URI=
MONGODB_DB_AUTH=careerforge_auth
MONGODB_DB_PROFILE=careerforge_profile
MONGODB_DB_JD=careerforge_jd
MONGODB_DB_RESUME=careerforge_resume
MONGODB_DB_ASSESSMENT=careerforge_assessment
MONGODB_DB_DOCUMENT=careerforge_document
MONGODB_DB_APPLICATION=careerforge_application
```

**Used by** auth · profile · jd · resume · assessment · document · application services.
`ai-service` and `notification-service` use no database (ADR-002).

**Required** Yes.

**Setup**

1. Create a free M0 (or M10+ for production) cluster at <https://cloud.mongodb.com>.
2. **Database Access** → add one user per service per environment. Grant
   `readWrite` on that service's database only — never `atlasAdmin`, never a cluster-wide
   grant. A wrongly-scoped query should fail with an authorisation error rather than
   silently succeed against another service's data.
3. **Network Access** → add your development egress IP. For production use private
   endpoints or VPC peering rather than an IP allowlist.
4. **Connect** → *Drivers* → copy the `mongodb+srv://…` string into `MONGODB_URI`.
   URL-encode any special characters in the password.
5. Enable continuous cloud backup and the alerts listed in `docs/DATABASE.md` §7.

**Rate limits** None, but M0 caps concurrent connections at 500. Pool sizes are set in
config-server accordingly.

**Security**

- Never expose the URI to React or embed it in a Docker image.
- TLS is always on; do not disable certificate validation, even locally.
- Rotate credentials quarterly and immediately on suspected exposure.
- The URI contains a password — it must never appear in a log line, an error response, or
  a stack trace shown to a user.

---

## Redis

**Purpose**
Gateway rate limiting, response and evidence caching, short-lived state, and Redis Streams
job queues for AI generation and document rendering (ADR-005).

**Environment variables**

```env
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=
```

**Used by** api-gateway · auth-service · jd-service · resume-service · ai-service ·
document-service · notification-service.

**Required** Yes.

**Setup**
Local development runs the `redis:7.4-alpine` container from `docker-compose.yml` with
AOF persistence — nothing to configure. In production use a managed Redis with TLS,
`REDIS_PASSWORD` set, and no public network exposure.

**Security**
Redis holds rate-limit counters and job payloads containing user IDs. It must never be
reachable from the internet. Do not cache full JD text, resume content or cover letters in
Redis beyond the lifetime of the job that needs them.

---

## Groq

**Purpose**
The only LLM provider. Performs semantic JD analysis, evidence selection, and resume and
cover-letter content generation.

**Environment variables**

```env
GROQ_API_KEY=
GROQ_BASE_URL=https://api.groq.com/openai/v1
GROQ_MODEL=llama-3.3-70b-versatile
GROQ_TIMEOUT_SECONDS=60
GROQ_MAX_OUTPUT_TOKENS=4096
```

**Used by** `ai-service` **only**. No other service may declare `GROQ_API_KEY` (blueprint
§37, ADR-012).

**Required** Yes — the product's core feature does not function without it.

**Setup**

1. Create an account at <https://console.groq.com>.
2. **API Keys** → *Create API Key*. Copy it immediately; it is shown once.
3. Put it in `.env` as `GROQ_API_KEY`.
4. Confirm the model in `GROQ_MODEL` is currently available on your account — Groq
   deprecates and renames models, so pin it in configuration rather than in code and
   verify it during upgrades.

**Rate limits**
Groq enforces per-model requests-per-minute, tokens-per-minute and requests-per-day limits
that vary by tier. `ai-service` therefore:

- applies Resilience4j rate limiting and a circuit breaker around the client;
- retries `429` and `5xx` with exponential backoff and jitter, at most twice;
- returns `AI_GENERATION_FAILED` after exhausting retries rather than hanging the request;
- records token usage per generation so cost and quota are observable in Grafana.

**Security**

- **Never expose this key to React.** No `VITE_` variable may reference it — `VITE_`
  values are compiled into the public bundle.
- The key exists in exactly one process. If it leaks, revoke it in the Groq console first,
  then rotate.
- Every JD is untrusted input. It is passed inside a delimited block labelled as data, the
  system prompt states that its contents are never instructions, and output is constrained
  to a JSON schema so instruction-following prose cannot survive parsing.
- System prompts are never returned to a client and never logged.
- Request and response bodies are not persisted — only token counts, latency, model ID and
  prompt version.

---

## Google OAuth 2.0

**Purpose**
"Sign in with Google" as an alternative to email and password.

**Environment variables**

```env
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
GOOGLE_REDIRECT_URI=http://localhost:8080/api/auth/oauth2/callback/google
```

**Used by** `auth-service` only.

**Required** Yes for Google sign-in; the application still works with email/password if
Google sign-in is disabled.

**Setup**

1. <https://console.cloud.google.com> → create or select a project.
2. **APIs & Services → OAuth consent screen**: choose *External*, fill in the app name,
   support email and privacy-policy URL. Add the `openid`, `email` and `profile` scopes —
   nothing more.
3. **Credentials → Create credentials → OAuth client ID → Web application.**
4. Authorised redirect URIs: add exactly the value of `GOOGLE_REDIRECT_URI`. Google
   matches this string exactly — a trailing slash difference fails the exchange.
5. Copy the client ID and client secret into `.env`.
6. For production, add the production redirect URI and complete consent-screen
   verification.

**Flow**
Authorization Code with PKCE, executed entirely server-side:

```text
Browser → GET /api/auth/oauth2/authorize/google      (auth-service creates state + PKCE)
        → Google consent screen
        → GET /api/auth/oauth2/callback/google?code=…&state=…
          auth-service validates state, exchanges the code for tokens,
          verifies the ID token, links or creates the account,
          issues CareerForge's own JWT + refresh cookie
        → redirect back to the frontend
```

**Security**

- `GOOGLE_CLIENT_SECRET` must never appear in React. The implicit flow is not used.
- `state` is single-use and bound to the session; a mismatch aborts with `AUTH_REQUIRED`.
- The Google ID token's signature, `aud`, `iss` and `exp` are all verified before any
  account is created or linked.
- Google's access token is not stored for plain sign-in — CareerForge issues its own
  session immediately after.

---

## MinIO (development object storage)

**Purpose**
S3-compatible private storage for rendered PDFs/DOCX, thumbnails and quarantined uploads
during development.

**Environment variables**

```env
S3_ENDPOINT=http://minio:9000
S3_REGION=us-east-1
S3_ACCESS_KEY=
S3_SECRET_KEY=
S3_BUCKET=careerforge-documents
S3_PRESIGNED_URL_TTL_SECONDS=300
S3_PATH_STYLE_ACCESS=true

MINIO_ROOT_USER=
MINIO_ROOT_PASSWORD=
```

**Used by** `document-service`.

**Required** Yes in development.

**Setup**
`docker compose up -d` starts MinIO and the `minio-init` job, which creates the bucket and
explicitly sets its anonymous policy to `none`. Console: <http://localhost:9001>.
Set `S3_ACCESS_KEY`/`S3_SECRET_KEY` to the same values as `MINIO_ROOT_USER`/
`MINIO_ROOT_PASSWORD` locally; in production they are separate, least-privilege
credentials.

**Security**
The bucket is private and must stay private. Objects use random UUID keys, are never
served from a static directory, and are reachable only through a presigned URL issued
after an ownership check.

---

## Amazon S3 (production object storage)

**Purpose**
Production artifact storage. Same code path as MinIO — only configuration differs.

**Environment variables** Same `S3_*` variables. In production set
`S3_ENDPOINT=https://s3.<region>.amazonaws.com`, `S3_PATH_STYLE_ACCESS=false`, and prefer
an IAM role over static keys.

**Used by** `document-service`.

**Setup**

1. Create a bucket with **Block Public Access** fully enabled.
2. Enable default encryption (SSE-S3 or SSE-KMS) and versioning.
3. Create an IAM policy granting `s3:PutObject`, `s3:GetObject` and `s3:DeleteObject` on
   `arn:aws:s3:::<bucket>/*` and nothing else. Attach it to the service's role.
4. Add a lifecycle rule deleting `quarantine/` objects after 24 hours.
5. Enable access logging and, where required, Object Lock for audit retention.

**Security**
Never make the bucket or any object public. Never return raw bytes through the service —
issue a presigned URL, valid for `S3_PRESIGNED_URL_TTL_SECONDS` (300), and only after the
requester is confirmed to be the owner.

---

## Gmail API

**Purpose**
Create a draft application email in the user's own Gmail account, with the resume and
cover letter attached. Optional feature.

**Environment variables**

```env
GMAIL_CLIENT_ID=
GMAIL_CLIENT_SECRET=
GMAIL_REDIRECT_URI=http://localhost:8080/api/applications/gmail/callback
```

**Used by** `application-service`.

**Required** No. If unset, the feature is hidden and the user copies the email content
manually.

**Setup**

1. In the same Google Cloud project, enable the **Gmail API**.
2. Create a separate OAuth client for this integration — do not reuse the sign-in client,
   so a user can revoke drafting access without losing the ability to sign in.
3. Request the single scope `https://www.googleapis.com/auth/gmail.compose`. Do **not**
   request `gmail.send` or any read scope.
4. Gmail scopes require Google verification before general availability; expect a review.

**Rate limits** 1,000,000,000 quota units/day, 250 units/user/second. Draft creation is
inexpensive; the client backs off on `429`/`403 rateLimitExceeded`.

**Security**

- **Drafts only. The application never sends an email on the user's behalf** — the user
  reviews and sends from Gmail (blueprint §19).
- The Gmail refresh token is a long-lived credential: store it encrypted at rest with a
  key held outside MongoDB, never log it, and delete it on disconnect.
- Provide an explicit "Disconnect Gmail" action that deletes the stored token and calls
  Google's revocation endpoint.

---

## SMTP (transactional email)

**Purpose**
Platform email only: address verification, password reset, security alerts. Never job
applications.

**Environment variables**

```env
SMTP_HOST=
SMTP_PORT=587
SMTP_USERNAME=
SMTP_PASSWORD=
SMTP_FROM=no-reply@careerforge.local
```

**Used by** `notification-service`.

**Required** Yes for email verification and password reset.

**Setup**
Any provider works (SES, SendGrid, Postmark, Mailgun). Use port 587 with STARTTLS, verify
the sending domain, and publish SPF, DKIM and DMARC records — without them, verification
emails land in spam and users cannot complete sign-up.

**Security**
Credentials stay server-side. Email bodies must not contain resume content, cover-letter
content, or any JD text. Rate-limit password-reset requests per address to prevent using
the endpoint as a spam relay.

---

## Observability endpoints (not third-party)

```env
OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317
GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=
```

Prometheus, Grafana and the OpenTelemetry Collector run locally in Docker. The collector
config strips `Authorization`, `Cookie` and `db.statement` attributes before export, so
traces cannot become a side channel for credentials or query contents.

---

## Adding a new external service

Blueprint §36 — complete every step in the same pull request:

1. State why it is required and what breaks without it.
2. Add its variables to `.env.example` with empty values.
3. Add a section to this file: purpose, variables, consumer, required?, setup, rate
   limits, security.
4. Document its authentication mechanism.
5. Confirm no credential reaches the frontend; no `VITE_`-prefixed secret, ever.
6. Add timeout, retry and circuit-breaker policy.
7. Add error handling that maps failures to the standard error envelope.
8. Add tests using a mock or recorded fixture — never a live call in CI.
9. Confirm Gitleaks passes.
10. Note where it appears in `docker-compose.yml`, if anywhere.
