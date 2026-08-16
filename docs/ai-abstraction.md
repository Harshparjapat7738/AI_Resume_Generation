# AI Provider Abstraction (`AiChatClient`)

**Status (current as of ADR-033):** one implementation, `GroqClient`. It serves all four AI
operations — JD Analysis, Evidence Selection, JD Optimization, Email Content. Gemini was removed
entirely (its last consumer, custom-PDF template analysis, died with document rendering), so
there is no second provider and no routing.

---

## Why the interface exists

Before ADR-024, the AI generation call sites in `ai-service` depended on `GroqClient` by
concrete type:

| Call site | Endpoint it serves |
|---|---|
| `JdAnalysisService` | `POST /internal/ai/jd-analysis` |
| `EvidenceSelectionService` | `POST /internal/ai/evidence-selection` |
| `JdOptimizationService` | `POST /internal/ai/jd-optimization` |
| `EmailContentService` | `POST /internal/ai/email-content` |

`AiChatClient` removes that coupling: every call site depends on the interface, and
`GroqClient` is the interface's sole implementation, wired automatically by Spring (it's the
only `@Component` of that type — no `@Primary` needed, since there is nothing to disambiguate
from). Nothing about how Groq is called, configured, retried, or logged has ever changed
because of this interface's existence.

`AiController`'s own diagnostic `GET /internal/ai/status` endpoint deliberately keeps its
direct `GroqClient`/`GroqException`/`GroqProperties` dependency — that endpoint's entire
purpose is reporting *Groq's* configuration and reachability (masked key, model, base URL), so
routing it through a provider-neutral interface would add indirection without removing any
coupling that actually mattered.

## The interface

```java
package ai.careerforge.ai.client;

public interface AiChatClient {

    AiChatResult complete(String systemPrompt, String userContent, String operation);

    record AiChatResult(String content, String model, int totalTokens) {
    }
}
```

This is intentionally the exact shape `GroqClient#complete` already had — nothing was added,
nothing was removed:

| Parameter/field | Meaning | Why it's here (and why nothing else is) |
|---|---|---|
| `systemPrompt` | The versioned instruction block, resolved by the caller via `PromptRegistry` before this call | Every call site already builds this the same way |
| `userContent` | The data block — untrusted content already fenced/sanitised (`UntrustedContent`) by the caller | Same — sanitisation happens before this call, not inside an implementation |
| `operation` | Metric/log tag (e.g. `"jd-optimization"`) | Used for token/latency metrics and log correlation |
| `AiChatResult.content` | The model's raw JSON string, still unvalidated | Every caller schema-validates it themselves via `AiGenerationSupport.validateSchema` — this interface doesn't validate anything |
| `AiChatResult.model` | Which model actually served the request | Persisted as provenance on every stored result (`JdOptimization.modelId`, `EmailContent.modelId`) |
| `AiChatResult.totalTokens` | Token usage for this call | Cost/provenance tracking |

**Deliberately excluded, and why:**

- **Schema/structured-output parameter** — schema validation happens *after* `complete()`
  returns, in each caller's own `AiGenerationSupport.validateSchema(...)` call, never inside the
  client. Adding a schema parameter here would duplicate a concern that already has an owner.
- **Temperature / model / timeout / retry-count parameters** — these are uniform,
  implementation-level configuration (`GroqProperties`, `GroqClientConfig`), never varied per call.
- **A shared exception type** — no call site catches `GroqException`; it
  propagates unchanged to `AiController`'s generic `call()` wrapper, which still imports and
  catches `GroqException` directly.

## `GroqClient` — the sole implementation

`implements AiChatClient`, no `@Primary` needed (nothing else implements the interface). Same
`WebClient`, same retry/backoff (`Retry.backoff(maxRetries, 2s)`, 429/5xx only), same circuit
breaker config, same `GroqException`, same "never log request/response body" guarantee it has
always had. Every call site (`JdAnalysisService`, `EvidenceSelectionService`, `JdOptimizationService`,
`EmailContentService`) injects `AiChatClient` unqualified and resolves here.

## Gemini: removed (ADR-033)

Gemini's last consumer was custom-PDF template analysis, which was deleted along with document
rendering. `GeminiClient`, `GeminiProperties`, `GEMINI_API_KEY` and the `google-genai`
dependency are all gone. Groq is the only provider.

## A note on testing: mocking concrete classes on this environment's JDK

Mockito's inline mock maker (Byte Buddy) cannot mock a concrete class — only an interface — on
this environment's JDK (a real, verified constraint: Byte Buddy's own error names the exact
JDK-support gate it fails on). Fixed reactor-wide by adding
`-Dnet.bytebuddy.experimental=true` to the root `pom.xml`'s `maven-surefire-plugin`
configuration. This is why every test in this file's scope mocks `AiChatClient` (an interface)
rather than `GroqClient` directly.
