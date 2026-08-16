package ai.careerforge.profile.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds {@code careerforge.storage.*} (application.yml), sourced from the S3_* env vars
 *  shared with the {@code minio}/{@code minio-init} Compose services — see .env.example.
 *  Mirrors the identical record the now-deleted document-service used for the same MinIO/S3
 *  bucket (ADR-034) — this is the only remaining consumer of that infrastructure. */
@ConfigurationProperties(prefix = "careerforge.storage")
public record StorageProperties(
        String endpoint, String region, String accessKey, String secretKey, String bucket, boolean pathStyleAccess) {
}
