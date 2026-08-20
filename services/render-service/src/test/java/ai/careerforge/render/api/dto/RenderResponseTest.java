package ai.careerforge.render.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** DTO validation and Jackson round-trip coverage for {@link RenderResponse}, including its
 *  status/document/errors cross-field consistency rule. */
class RenderResponseTest {

    // findAndRegisterModules() picks up jackson-datatype-jsr310 (on the classpath transitively
    // via spring-boot-starter-web) for Instant, matching how Spring Boot's own autoconfigured
    // ObjectMapper is wired at runtime — the plain `new ObjectMapper()` other DTO tests use is
    // enough for them since they have no java.time field.
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static DocumentMetadata metadata() {
        return new DocumentMetadata("render-job-1", OutputFormat.PDF, 512_000, 1, Instant.parse("2026-08-20T10:00:00Z"));
    }

    @Test
    @DisplayName("a succeeded response has no Jakarta violations")
    void succeededResponseHasNoViolations() {
        assertThat(validator.validate(RenderResponse.succeeded(metadata()))).isEmpty();
    }

    @Test
    @DisplayName("a failed response has no Jakarta violations")
    void failedResponseHasNoViolations() {
        RenderResponse response = RenderResponse.failed(
                List.of(new RenderError("UNSUPPORTED_SCHEMA_VERSION", "schemaVersion 2.0.0 is not recognised")));

        assertThat(validator.validate(response)).isEmpty();
    }

    @Test
    @DisplayName("SUCCEEDED with no document metadata is rejected")
    void succeededWithoutDocumentIsRejected() {
        RenderResponse response = new RenderResponse(RenderStatus.SUCCEEDED, null, List.of());

        assertThat(validator.validate(response)).isNotEmpty();
    }

    @Test
    @DisplayName("SUCCEEDED with errors present is rejected")
    void succeededWithErrorsIsRejected() {
        RenderResponse response = new RenderResponse(RenderStatus.SUCCEEDED, metadata(),
                List.of(new RenderError("SOME_CODE", "message")));

        assertThat(validator.validate(response)).isNotEmpty();
    }

    @Test
    @DisplayName("FAILED with no errors is rejected")
    void failedWithoutErrorsIsRejected() {
        RenderResponse response = new RenderResponse(RenderStatus.FAILED, null, List.of());

        assertThat(validator.validate(response)).isNotEmpty();
    }

    @Test
    @DisplayName("FAILED with document metadata present is rejected")
    void failedWithDocumentIsRejected() {
        RenderResponse response = new RenderResponse(RenderStatus.FAILED, metadata(),
                List.of(new RenderError("SOME_CODE", "message")));

        assertThat(validator.validate(response)).isNotEmpty();
    }

    @Test
    @DisplayName("a missing status is rejected")
    void missingStatusIsRejected() {
        RenderResponse response = new RenderResponse(null, metadata(), List.of());

        assertThat(validator.validate(response)).isNotEmpty();
    }

    @Test
    @DisplayName("succeeded response round-trips through Jackson exactly")
    void succeededRoundTripsThroughJackson() throws Exception {
        RenderResponse original = RenderResponse.succeeded(metadata());

        String json = mapper.writeValueAsString(original);
        RenderResponse restored = mapper.readValue(json, RenderResponse.class);

        assertThat(restored).isEqualTo(original);
    }

    @Test
    @DisplayName("failed response round-trips through Jackson exactly")
    void failedRoundTripsThroughJackson() throws Exception {
        RenderResponse original = RenderResponse.failed(
                List.of(new RenderError("TEMPLATE_NOT_FOUND", "no such template")));

        String json = mapper.writeValueAsString(original);
        RenderResponse restored = mapper.readValue(json, RenderResponse.class);

        assertThat(restored).isEqualTo(original);
    }
}
