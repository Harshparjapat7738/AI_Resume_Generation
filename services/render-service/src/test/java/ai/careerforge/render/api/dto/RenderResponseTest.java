package ai.careerforge.render.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** DTO validation and Jackson round-trip coverage for {@link RenderResponse}, including its
 *  status/document/pdfBytes/errors cross-field consistency rule. */
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

    private static byte[] fakePdfBytes() {
        return "%PDF-1.7 fake bytes for a test".getBytes(StandardCharsets.US_ASCII);
    }

    @Test
    @DisplayName("a succeeded response has no Jakarta violations")
    void succeededResponseHasNoViolations() {
        assertThat(validator.validate(RenderResponse.succeeded(metadata(), fakePdfBytes()))).isEmpty();
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
        RenderResponse response = new RenderResponse(RenderStatus.SUCCEEDED, null, fakePdfBytes(), List.of());

        assertThat(validator.validate(response)).isNotEmpty();
    }

    @Test
    @DisplayName("SUCCEEDED with no pdfBytes is rejected")
    void succeededWithoutPdfBytesIsRejected() {
        RenderResponse response = new RenderResponse(RenderStatus.SUCCEEDED, metadata(), null, List.of());

        assertThat(validator.validate(response)).isNotEmpty();
    }

    @Test
    @DisplayName("SUCCEEDED with empty pdfBytes is rejected")
    void succeededWithEmptyPdfBytesIsRejected() {
        RenderResponse response = new RenderResponse(RenderStatus.SUCCEEDED, metadata(), new byte[0], List.of());

        assertThat(validator.validate(response)).isNotEmpty();
    }

    @Test
    @DisplayName("SUCCEEDED with errors present is rejected")
    void succeededWithErrorsIsRejected() {
        RenderResponse response = new RenderResponse(RenderStatus.SUCCEEDED, metadata(), fakePdfBytes(),
                List.of(new RenderError("SOME_CODE", "message")));

        assertThat(validator.validate(response)).isNotEmpty();
    }

    @Test
    @DisplayName("FAILED with no errors is rejected")
    void failedWithoutErrorsIsRejected() {
        RenderResponse response = new RenderResponse(RenderStatus.FAILED, null, null, List.of());

        assertThat(validator.validate(response)).isNotEmpty();
    }

    @Test
    @DisplayName("FAILED with document metadata present is rejected")
    void failedWithDocumentIsRejected() {
        RenderResponse response = new RenderResponse(RenderStatus.FAILED, metadata(), null,
                List.of(new RenderError("SOME_CODE", "message")));

        assertThat(validator.validate(response)).isNotEmpty();
    }

    @Test
    @DisplayName("FAILED with pdfBytes present is rejected")
    void failedWithPdfBytesIsRejected() {
        RenderResponse response = new RenderResponse(RenderStatus.FAILED, null, fakePdfBytes(),
                List.of(new RenderError("SOME_CODE", "message")));

        assertThat(validator.validate(response)).isNotEmpty();
    }

    @Test
    @DisplayName("a missing status is rejected")
    void missingStatusIsRejected() {
        RenderResponse response = new RenderResponse(null, metadata(), fakePdfBytes(), List.of());

        assertThat(validator.validate(response)).isNotEmpty();
    }

    @Test
    @DisplayName("pdfBytes is defensively copied — mutating the returned array never affects the instance")
    void pdfBytesIsDefensivelyCopied() {
        byte[] original = fakePdfBytes();
        RenderResponse response = RenderResponse.succeeded(metadata(), original);

        byte[] handle = response.pdfBytes();
        handle[0] = 0;

        assertThat(response.pdfBytes()[0]).isEqualTo(original[0]);
    }

    @Test
    @DisplayName("succeeded response round-trips through Jackson exactly, including pdfBytes")
    void succeededRoundTripsThroughJackson() throws Exception {
        RenderResponse original = RenderResponse.succeeded(metadata(), fakePdfBytes());

        String json = mapper.writeValueAsString(original);
        RenderResponse restored = mapper.readValue(json, RenderResponse.class);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.pdfBytes()).isEqualTo(original.pdfBytes());
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
