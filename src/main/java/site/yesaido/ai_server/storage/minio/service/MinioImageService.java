package site.yesaido.ai_server.storage.minio.service;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.errors.MinioException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import site.yesaido.ai_server.storage.minio.config.MinioProperties;
import site.yesaido.ai_server.storage.minio.exception.MinioObjectReadException;
import site.yesaido.ai_server.storage.multipart.ByteArrayMultipartFile;
import site.yesaido.common.storage.StorageUrlResolver;

import java.io.IOException;

/**
 * MinIO에서 Vision 분석 대상 이미지를 조회하는 서비스입니다.
 */
@Service
@RequiredArgsConstructor
public class MinioImageService implements StorageUrlResolver {

    private static final String IMAGE_PART_NAME = "image";
    private static final String DEFAULT_CONTENT_TYPE = MediaType.APPLICATION_OCTET_STREAM_VALUE;
    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    public MultipartFile loadImage(String objectKey) {
        try (GetObjectResponse response = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(minioProperties.bucketName())
                        .object(objectKey)
                        .build()
        )) {
            return new ByteArrayMultipartFile(
                    IMAGE_PART_NAME,
                    extractOriginalFilename(objectKey),
                    resolveContentType(response),
                    response.readAllBytes()
            );
        } catch (MinioException | IOException exception) {
            throw new MinioObjectReadException(objectKey, exception);
        }
    }

    private String extractOriginalFilename(String objectKey) {
        int lastSeparatorIndex = objectKey.lastIndexOf('/');
        return objectKey.substring(lastSeparatorIndex + 1);
    }

    private String resolveContentType(GetObjectResponse response) {
        String contentType = response.headers().get(HttpHeaders.CONTENT_TYPE);

        if (contentType == null || contentType.isBlank()) {
            return DEFAULT_CONTENT_TYPE;
        }

        return contentType;
    }
}
