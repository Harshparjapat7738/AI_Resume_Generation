package ai.careerforge.document.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds {@code careerforge.storage.*} (application.yml), sourced from the S3_* env vars
 *  shared with the {@code minio}/{@code minio-init} Compose services — see .env.example. */
@ConfigurationProperties(prefix = "careerforge.storage")
public record StorageProperties(
        String endpoint, String region, String accessKey, String secretKey, String bucket, boolean pathStyleAccess) {
}
