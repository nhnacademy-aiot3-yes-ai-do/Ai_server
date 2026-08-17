package site.yesaido.ai_server.storage.minio.service;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.errors.MinioException;
import okhttp3.Headers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import site.yesaido.ai_server.storage.minio.config.MinioProperties;
import site.yesaido.ai_server.storage.minio.exception.MinioObjectReadException;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class MinioImageServiceTest {

    private static final String BUCKET_NAME = "test-bucket";
    private static final String OBJECT_KEY = "cultivation-1/mushroom.png";

    @Mock
    private MinioClient minioClient;

    private MinioImageService minioImageService;

    @BeforeEach
    void setUp() {
        MinioProperties minioProperties = new MinioProperties(
                "http://minio-test.invalid",
                "test-access-key",
                "test-secret-key",
                BUCKET_NAME
        );

        minioImageService = new MinioImageService(
                minioClient,
                minioProperties
        );
    }

    @Test
    @DisplayName("이미지를 조회하면 MinIO 객체를 MultipartFile로 반환 테스트")
    void loadImageReturnsMultipartFile() throws Exception {
        byte[] imageContent = {1, 2, 3, 4};
        Headers headers = new Headers.Builder()
                .add(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE)
                .build();

        GetObjectResponse response = new GetObjectResponse(
                headers,
                BUCKET_NAME,
                "us-east-1",
                OBJECT_KEY,
                new ByteArrayInputStream(imageContent)
        );

        given(minioClient.getObject(any(GetObjectArgs.class)))
                .willReturn(response);

        MultipartFile result = minioImageService.loadImage(OBJECT_KEY);

        assertThat(result.getName()).isEqualTo("image");
        assertThat(result.getOriginalFilename()).isEqualTo("mushroom.png");
        assertThat(result.getContentType()).isEqualTo(MediaType.IMAGE_PNG_VALUE);
        assertThat(result.getBytes()).containsExactly(imageContent);

        ArgumentCaptor<GetObjectArgs> argumentCaptor =
                ArgumentCaptor.forClass(GetObjectArgs.class);

        then(minioClient).should().getObject(argumentCaptor.capture());

        assertThat(argumentCaptor.getValue().bucket()).isEqualTo(BUCKET_NAME);
        assertThat(argumentCaptor.getValue().object()).isEqualTo(OBJECT_KEY);
    }

    @Test
    @DisplayName("Content-Type 헤더가 없으면 application/octet-stream을 사용 테스트")
    void loadImageUsesDefaultContentTypeWhenHeaderIsMissing() throws Exception {
        byte[] imageContent = {1, 2, 3, 4};
        GetObjectResponse response = new GetObjectResponse(
                new Headers.Builder().build(),
                BUCKET_NAME,
                "us-east-1",
                OBJECT_KEY,
                new ByteArrayInputStream(imageContent)
        );

        given(minioClient.getObject(any(GetObjectArgs.class)))
                .willReturn(response);

        MultipartFile result = minioImageService.loadImage(OBJECT_KEY);

        assertThat(result.getContentType())
                .isEqualTo(MediaType.APPLICATION_OCTET_STREAM_VALUE);
    }

    @Test
    @DisplayName("MinIO 조회 예외를 MinioObjectReadException으로 변환 테스트")
    void loadImageWrapsMinioException() throws Exception {
        MinioException cause = new MinioException("MinIO connection failed");

        given(minioClient.getObject(any(GetObjectArgs.class)))
                .willThrow(cause);

        assertThatThrownBy(() -> minioImageService.loadImage(OBJECT_KEY))
                .isInstanceOf(MinioObjectReadException.class)
                .hasMessageContaining(OBJECT_KEY)
                .hasCause(cause);
    }
}
