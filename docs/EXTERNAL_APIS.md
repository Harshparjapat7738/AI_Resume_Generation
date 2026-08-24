# External APIs

Every third-party service the application depends on: what it is for, which variables
configure it, which service uses it, how to set it up, and how to handle its credentials.

**This file documents variable names only. It never contains a value.** Real values live
in `.env` (gitignored) locally and in a secrets manager in production.

| Service | Used by | Required for | Credential reaches the browser? |
|---|---|---|---|
| MongoDB Atlas | all data-owning services | everything | **Never** |
| Redis | gateway, auth, jd, ai | rate limiting, cache | **Never** |
| Groq | ai-service only | JD analysis, evidence selection, JD optimization, email content | **Never** |
| Google OAuth | auth-service only | Google sign-in | Client ID only |
| Gmail API | application-service | creating drafts | Client ID only |

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
MONGODB_DB_ASSESSMENT=careerforge_assessment
MONGODB_DB_APPLICATION=careerforge_application
```

**Used by** auth · profile · jd · assessment · application services.
`ai-service` uses no database (ADR-002).

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

**Used by** api-gateway · auth-service · jd-service · ai-service.

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
The primary LLM provider ([ADR-033](ARCHITECTURE_DECISIONS.md#adr-033)) — JD analysis, evidence
selection, JD optimization and email content generation all depend on Groq being reachable.
Since [ADR-039](ARCHITECTURE_DECISIONS.md#adr-039), JD analysis and JD-optimization adjudication
fall back to Gemini (see below) when Groq fails in a way another provider could plausibly
succeed at; evidence selection and email content have no fallback and behave exactly as before.

**Environment variables**

```env
GROQ_API_KEY=
GROQ_BASE_URL=https://api.groq.com/openai/v1
GROQ_MODEL=openai/gpt-oss-120b
GROQ_TIMEOUT_SECONDS=60
GROQ_MAX_OUTPUT_TOKENS=4096
```

**Used by** `ai-service` **only**. No other service may declare `GROQ_API_KEY` (blueprint
§37, ADR-012).

**Required** Yes — `ai-service` fails to start without it (`GroqProperties` hard-fails on a
blank key); the product's core feature does not function without it.

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
- retries `429` and `5xx` with exponential backoff and jitter, at most twice
  (`GROQ_MAX_RETRIES`, default 2 — never retried for a 4xx it caused);
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

## Gemini

**Purpose**
The fallback provider for exactly two operations ([ADR-039](ARCHITECTURE_DECISIONS.md#adr-039)):
JD analysis and JD-optimization adjudication. `ai-service`'s `AiProviderRouter` tries Groq
first, always; Gemini is only ever called when Groq fails in a way another provider could
plausibly succeed at (a temporary rate limit, a timeout, Groq unavailable), for one of those two
operations, and Gemini is actually configured. Groq succeeding never calls Gemini. Evidence
selection and email content have no fallback at all.

**Environment variables**

```env
GEMINI_ENABLED=true
GEMINI_API_KEY=
GEMINI_BASE_URL=https://generativelanguage.googleapis.com
GEMINI_MODEL=gemini-3.6-flash
GEMINI_TIMEOUT_SECONDS=60
GEMINI_MAX_OUTPUT_TOKENS=1200
AI_FALLBACK_OPERATIONS=jd-analysis,jd-optimization
```

**Used by** `ai-service` **only** — same isolation as Groq (ADR-012): no other service may
declare `GEMINI_API_KEY`, and `ai-service` still has no gateway route.

**Required** No. Entirely optional: leave `GEMINI_API_KEY` blank, or set
`GEMINI_ENABLED=false`, and `ai-service` starts normally with Groq-only behaviour — a missing
key is checked at first fallback *attempt*, not at startup (unlike `GroqProperties`, which
fails fast).

**Setup**

1. Create/select a project in [Google AI Studio](https://aistudio.google.com/) or Google Cloud.
2. Generate an API key for the Gemini API.
3. Put it in `.env` as `GEMINI_API_KEY`.
4. Confirm `GEMINI_MODEL` is a current, stable model that supports structured JSON output
   (`responseMimeType`/`responseSchema`) — Google renames/deprecates models on its own
   schedule, same caution as Groq's `GROQ_MODEL`. **Live-verified (ADR-039):** the default,
   `gemini-3.6-flash`, reasons by default, and that reasoning is billed against the same
   `maxOutputTokens` as the visible answer — an unconfigured call spent ~290 hidden tokens
   before writing 5 visible ones, and the model rejects `thinkingBudget:0` outright. `GeminiClient`
   sends `thinkingConfig:{thinkingLevel:"LOW"}` on every request, which brought that same call's
   hidden-token spend to effectively zero without raising the output budget. If `GEMINI_MODEL` is
   ever changed, re-verify this — a model without a "thinking level" option may not need or
   accept the same field, and one with a different reasoning default could reintroduce the
   original truncation risk.

**Rate limits**
Gemini has its own RPM/TPM/RPD limits, enforced per Google Cloud project — creating additional
API keys does **not** multiply the quota. `ai-service` therefore:

- never retries a Gemini call internally (it is already the fallback path; retrying it would
  spend more of Gemini's own limited quota for a call whose only remaining escalation is a
  controlled failure anyway);
- classifies a 429 the same way Groq's is (a "this request alone exceeds the limit" wording is
  never retried/escalated further; a temporary one is reported as such);
- marks a short (45s) cooldown after a Groq rate-limit failure (`GroqCooldown`, Redis-backed,
  best-effort) so a burst of requests doesn't re-attempt an already-known-bad Groq call before
  falling back — reducing load on both providers, not just Groq;
- records token usage and rate-limit-relevant response headers the same way Groq's are.

**Security**

- Same rules as Groq: never exposed to React, exists in exactly one process (`ai-service`),
  revoke-then-rotate if leaked.
- The API key is sent as the `x-goog-api-key` request header, never as a URL query parameter —
  it cannot appear in access logs that record request paths.
- Every JD/evidence payload reaching Gemini has already been fenced/sanitised and deterministically
  filtered exactly as Groq's is (ADR-038) — Gemini never sees the caller's full evidence
  inventory, and structured output (`responseSchema`) constrains it to the same shape Groq's
  output is validated against.
- Request and response bodies are not logged — only token counts, latency, model ID, finish
  reason and status.

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
S3-compatible private storage for user-uploaded "My Templates" files (ADR-034) — the one
remaining consumer of this bucket since the document-rendering pipeline that originally
justified it (`document-service`) was removed by ADR-033.

**Environment variables**

```env
S3_ENDPOINT=http://minio:9000
S3_REGION=us-east-1
S3_ACCESS_KEY=
S3_SECRET_KEY=
S3_BUCKET=careerforge-templates
S3_PATH_STYLE_ACCESS=true

MINIO_ROOT_USER=
MINIO_ROOT_PASSWORD=
```

**Used by** `profile-service` only.

**Required** Yes in development — profile-service will not start without a reachable MinIO
endpoint (`careerforge.storage.*`, application.yml).

**Setup**
`docker compose up -d` starts MinIO and the `minio-init` job, which creates the bucket and
explicitly sets its anonymous policy to `none`. Console: <http://localhost:9001>.
Set `S3_ACCESS_KEY`/`S3_SECRET_KEY` to the same values as `MINIO_ROOT_USER`/
`MINIO_ROOT_PASSWORD` locally; in production they are separate, least-privilege
credentials.

**Security**
The bucket is private and must stay private. Objects use random UUID keys — never a
filename, user id or anything else guessable — are never served from a static directory,
and have no public or presigned URL of any kind: every download streams the bytes through
`TemplateController`'s own ownership-checked endpoint (`GET /api/profile/templates/{id}/download`),
`404` for a template that exists but belongs to someone else.

---

## Amazon S3 (production object storage)

**Purpose**
Production artifact storage. Same code path as MinIO — only configuration differs.

**Environment variables** Same `S3_*` variables. In production set
`S3_ENDPOINT=https://s3.<region>.amazonaws.com`, `S3_PATH_STYLE_ACCESS=false`, and prefer
an IAM role over static keys.

**Used by** `profile-service` only.

**Setup**

1. Create a bucket with **Block Public Access** fully enabled.
2. Enable default encryption (SSE-S3 or SSE-KMS) and versioning.
3. Create an IAM policy granting `s3:PutObject`, `s3:GetObject` and `s3:DeleteObject` on
   `arn:aws:s3:::<bucket>/*` and nothing else. Attach it to the service's role.
4. Enable access logging.

**Security**
Never make the bucket or any object public. Never issue a public or presigned URL for a
template file — every download goes through `profile-service`'s own ownership-checked
endpoint, exactly as in development.

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
