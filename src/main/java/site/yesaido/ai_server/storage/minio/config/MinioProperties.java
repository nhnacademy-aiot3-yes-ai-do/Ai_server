package site.yesaido.ai_server.storage.minio.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * MinIO 서버 연결과 이미지 저장소 설정입니다.
 *
 * @param endpoint      MinIO 서버 주소
 * @param accessKey     MinIO 접근 키
 * @param secretKey     MinIO 비밀 키
 * @param bucketName    버섯 이미지가 저장된 버킷 이름
 */
@ConfigurationProperties(prefix = "minio")
@Validated
public record MinioProperties(
        @NotBlank String endpoint,
        @NotBlank String accessKey,
        @NotBlank String secretKey,
        @NotBlank String bucketName
) {
}
