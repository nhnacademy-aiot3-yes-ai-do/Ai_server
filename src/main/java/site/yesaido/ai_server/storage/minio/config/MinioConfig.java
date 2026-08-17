package site.yesaido.ai_server.storage.minio.config;

import io.minio.MinioClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 클라이언트 관련 Spring 설정입니다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MinioProperties.class)
public class MinioConfig {

    @Bean(destroyMethod = "close")
    public MinioClient minioClient(MinioProperties minioProperties) {
        return MinioClient.builder()
                .endpoint(minioProperties.endpoint())
                .credentials(
                        minioProperties.accessKey(),
                        minioProperties.secretKey()
                )
                .build();
    }
}
