# Cross-service tests

Unit and slice tests live beside the code they cover, in each service's
`src/test/java`. This directory is for tests that span services:

| Path | Purpose | Added in |
|---|---|---|
| `e2e/` | Playwright journey: signup → profile → JD → confirm → generate → assess → preview → download | Milestone 5+ |
| `contract/` | OpenAPI contract validation of each service against `docs/API_CATALOG.md` | Milestone 2+ |
| `ai-eval/` | Golden dataset for AI grounding: normal JDs, sparse profiles, unrelated JDs, career changes, missing skills, prompt injection, fabricated metrics, non-job text | Milestone 5 |
| `security/` | SSRF payloads, malicious file uploads, IDOR/BOLA probes, expired-token handling | Milestone 2+ |

No test in this directory may depend on production credentials. Infrastructure comes
from Testcontainers (MongoDB, Redis, MinIO) or from the local Docker Compose stack.
