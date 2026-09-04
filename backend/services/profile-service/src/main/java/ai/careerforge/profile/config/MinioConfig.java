package ai.careerforge.profile.config;

import io.minio.MinioClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The bucket itself is created and locked to private (no anonymous read/write) once by the
 * {@code minio-init} Compose service — see docker-compose.yml. This service only ever holds
 * credentials to talk to it server-side; a browser is never given them (no public file URL,
 * ever — every download goes through {@code TemplateController}'s own ownership-checked
 * endpoint, streamed through this process).
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class MinioConfig {

    @Bean
    public MinioClient minioClient(StorageProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.endpoint())
                .region(properties.region())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }
}
