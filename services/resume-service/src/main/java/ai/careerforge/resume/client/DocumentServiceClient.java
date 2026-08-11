package ai.careerforge.resume.client;

import ai.careerforge.resume.client.ClientDtos.CustomTemplateAssetDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * The renderable-asset half of custom templates — see document-service's
 * {@code CustomTemplateAssetService} class comment for the full split rationale. Reached
 * directly (not through the gateway), caller identity forwarded manually — see
 * {@link FeignHeaderForwardingConfig}.
 *
 * <p>{@code store} sends the raw file bytes rather than {@code multipart/form-data}: this is
 * the only caller of that endpoint (the browser always uploads to *this* service's own
 * multipart endpoint first — see {@code TemplateController#uploadCustom}), so there's no need
 * for a Feign multipart encoder just to re-forward a file server-to-server. The filename is
 * carried as a URL-encoded header since HTTP headers don't reliably carry arbitrary Unicode.
 */
@FeignClient(name = "document-service", configuration = FeignHeaderForwardingConfig.class)
public interface DocumentServiceClient {

    @PostMapping(value = "/api/documents/custom-templates", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    CustomTemplateAssetDto store(@RequestHeader("X-Filename") String encodedFilename, @RequestBody byte[] file);

    @PostMapping("/api/documents/custom-templates/{id}/duplicate")
    CustomTemplateAssetDto duplicate(@PathVariable("id") String id);

    @DeleteMapping("/api/documents/custom-templates/{id}")
    void delete(@PathVariable("id") String id);
}
