package ai.careerforge.resume.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.ErrorCode;
import ai.careerforge.resume.domain.Template;
import ai.careerforge.resume.domain.TemplateSource;
import ai.careerforge.resume.domain.TemplateStatus;
import ai.careerforge.resume.domain.TemplateType;
import ai.careerforge.resume.repository.TemplateRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Covers the "template listing", "unauthorized template access" and default-template
 * scenarios described in the template-system task's test list.
 */
@ExtendWith(MockitoExtension.class)
class TemplateServiceTest {

    @Mock
    private TemplateRepository repository;

    private TemplateService service;

    private static Template builtIn(String id, TemplateStatus status) {
        return new Template(id, "Name " + id, "desc", id, TemplateType.RESUME, "1", status,
                TemplateSource.BUILT_IN, null, List.of("PDF", "DOCX"), true);
    }

    @Test
    void listReturnsOnlyActiveTemplatesOfTheRequestedType() {
        service = new TemplateService(repository);
        List<Template> active = List.of(builtIn("classic", TemplateStatus.ACTIVE));
        when(repository.findByTypeAndStatusOrderByNameAsc(TemplateType.RESUME, TemplateStatus.ACTIVE))
                .thenReturn(active);

        List<Template> result = service.list(TemplateType.RESUME);

        assertThat(result).containsExactlyElementsOf(active);
    }

    @Test
    void resolveForGenerationDefaultsToClassicWhenTemplateIdIsBlank() {
        service = new TemplateService(repository);
        Template classic = builtIn("classic", TemplateStatus.ACTIVE);
        when(repository.findById("classic")).thenReturn(Optional.of(classic));

        Template resolved = service.resolveForGeneration("user-1", null);

        assertThat(resolved.id()).isEqualTo("classic");
        assertThat(TemplateService.DEFAULT_TEMPLATE_ID).isEqualTo("classic");
    }

    @Test
    void resolveForGenerationHonorsAnExplicitValidTemplateId() {
        service = new TemplateService(repository);
        Template modern = builtIn("modern-ats", TemplateStatus.ACTIVE);
        when(repository.findById("modern-ats")).thenReturn(Optional.of(modern));

        Template resolved = service.resolveForGeneration("user-1", "modern-ats");

        assertThat(resolved.id()).isEqualTo("modern-ats");
    }

    @Test
    void getThrowsNotFoundForAnUnknownTemplateId() {
        service = new TemplateService(repository);
        when(repository.findById("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("user-1", "nope"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void getThrowsNotFoundForADisabledTemplate() {
        service = new TemplateService(repository);
        Template disabled = builtIn("classic", TemplateStatus.DISABLED);
        when(repository.findById("classic")).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> service.get("user-1", "classic"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /** Unauthorized template access: a custom-uploaded template owned by a different user must
     *  never be selectable, even though upload itself isn't built yet — this proves the guard
     *  works ahead of that feature shipping. */
    @Test
    void getThrowsNotFoundForACustomUploadOwnedByAnotherUser() {
        service = new TemplateService(repository);
        Template someoneElses = new Template("custom-1", "Someone's template", "desc", "custom-1",
                TemplateType.RESUME, "1", TemplateStatus.ACTIVE, TemplateSource.CUSTOM_UPLOAD,
                "owner-user", List.of("PDF"), false);
        when(repository.findById("custom-1")).thenReturn(Optional.of(someoneElses));

        assertThatThrownBy(() -> service.get("intruder-user", "custom-1"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void getAllowsTheOwnerOfACustomUploadTemplate() {
        service = new TemplateService(repository);
        Template mine = new Template("custom-1", "My template", "desc", "custom-1",
                TemplateType.RESUME, "1", TemplateStatus.ACTIVE, TemplateSource.CUSTOM_UPLOAD,
                "owner-user", List.of("PDF"), false);
        when(repository.findById("custom-1")).thenReturn(Optional.of(mine));

        Template resolved = service.get("owner-user", "custom-1");

        assertThat(resolved.id()).isEqualTo("custom-1");
    }
}
