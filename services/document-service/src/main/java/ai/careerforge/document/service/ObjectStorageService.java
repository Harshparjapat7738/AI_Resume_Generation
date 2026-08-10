package ai.careerforge.document.service;

import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.ErrorCode;
import ai.careerforge.document.config.StorageProperties;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Thin wrapper around the MinIO/S3 SDK. Every object key is a random UUID — never derived
 * from a filename, user id or any other guessable input — and the bucket carries no
 * anonymous read policy (set once by the {@code minio-init} Compose service), so a byte is
 * reachable only through this service's own authenticated download endpoint.
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
            log.warn("Object upload failed: {}", ex.getMessage());
            throw new ApiException(ErrorCode.DOCUMENT_RENDER_FAILED, ErrorCode.DOCUMENT_RENDER_FAILED.defaultMessage(), ex);
        }
    }

    /** Deletes a previously-uploaded object. Best-effort — failures are logged, not thrown,
     *  so a re-render isn't blocked by an orphaned object that can be swept up later. */
    public void delete(String objectKey) {
        try {
            minioClient.removeObject(io.minio.RemoveObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .build());
        } catch (Exception ex) {
            log.warn("Object delete failed for key={}: {}", objectKey, ex.getMessage());
        }
    }

    public byte[] download(String objectKey) {
        try (GetObjectResponse response = minioClient.getObject(GetObjectArgs.builder()
                .bucket(properties.bucket())
                .object(objectKey)
                .build())) {
            return response.readAllBytes();
        } catch (Exception ex) {
            log.warn("Object download failed for key={}: {}", objectKey, ex.getMessage());
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }
}
