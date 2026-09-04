package ai.careerforge.profile.service;

import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.ErrorCode;
import ai.careerforge.profile.config.StorageProperties;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Thin wrapper around the MinIO/S3 SDK — the private-file-storage half of the "My Templates"
 * feature (ADR-034). Every object key is a random UUID — never derived from a filename, user id
 * or any other guessable input — and the bucket carries no anonymous read policy (set once by
 * the {@code minio-init} Compose service), so a byte is reachable only through this process's
 * own authenticated, ownership-checked download endpoint. Mirrors the now-deleted
 * document-service's identically-named class exactly; this is the only remaining consumer of
 * that shared MinIO infrastructure.
 */
@Service
public class ObjectStorageService {

    private static final Logger log = LoggerFactory.getLogger(ObjectStorageService.class);

    private final MinioClient minioClient;
    private final StorageProperties properties;

    public ObjectStorageService(MinioClient minioClient, StorageProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    public String bucket() {
        return properties.bucket();
    }

    /** Uploads bytes under a fresh random key and returns it. */
    public String upload(byte[] bytes, String contentType) {
        String objectKey = UUID.randomUUID().toString();
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .stream(in, bytes.length, -1)
                    .contentType(contentType)
                    .build());
            return objectKey;
        } catch (Exception ex) {
            log.warn("Template object upload failed: {}", ex.getMessage());
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE,
                    "The template could not be stored right now. Please try again.", ex);
        }
    }

    /** Deletes a previously-uploaded object. Best-effort — failures are logged, not thrown, so
     *  a template row is never left un-deletable by a transient storage error; an orphaned
     *  object can be swept up later without corrupting the user-visible outcome (the metadata
     *  row, which is what the user actually sees, is always removed). */
    public void delete(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .build());
        } catch (Exception ex) {
            log.warn("Template object delete failed for key={}: {}", objectKey, ex.getMessage());
        }
    }

    public byte[] download(String objectKey) {
        try (GetObjectResponse response = minioClient.getObject(GetObjectArgs.builder()
                .bucket(properties.bucket())
                .object(objectKey)
                .build())) {
            return response.readAllBytes();
        } catch (Exception ex) {
            log.warn("Template object download failed for key={}: {}", objectKey, ex.getMessage());
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE,
                    "The template could not be retrieved right now. Please try again.", ex);
        }
    }
}
