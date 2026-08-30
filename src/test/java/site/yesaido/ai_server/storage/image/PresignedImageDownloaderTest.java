package site.yesaido.ai_server.storage.image;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.test.web.client.response.DefaultResponseCreator;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import site.yesaido.ai_server.dto.cultivation.DailyCultivationPhotoResponse;
import site.yesaido.ai_server.exception.ImageDownloadException;
import site.yesaido.ai_server.exception.ImageDownloadException.Reason;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withRawStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("Presigned 이미지 다운로드 및 Multipart 변환 테스트")
class PresignedImageDownloaderTest {

    private static final Long CULTIVATION_ID = 7L;
    private static final Long PHOTO_ID = 42L;

    private static final String ALLOWED_ORIGIN =
            "https://storage.test";

    private static final String FAKE_SIGNATURE =
            "fake-signature-for-test";

    private static final String VALID_URL =
            ALLOWED_ORIGIN
                    + "/photos/42?X-Amz-Signature="
                    + FAKE_SIGNATURE;

    private static final String USER_MESSAGE =
            "Vision 분석용 사진을 다운로드하지 못했습니다.";

    private static final String INVALID_ALLOWED_ORIGIN_MESSAGE =
            "이미지 다운로드 허용 origin 설정이 올바르지 않습니다.";

    private static final String MALFORMED_CONTENT_TYPE =
            "malformed-secret-content-type";

    private static final String INVALID_CONTENT_LENGTH =
            "not-a-number-secret";

    private static final String EXTERNAL_IO_MESSAGE =
            "external-secret-io-message";

    private static final String EXPECTED_ACCEPT_HEADER =
            "image/jpeg, image/png, image/webp";

    private static final byte[] IMAGE_CONTENT =
            "fake-image-content".getBytes(StandardCharsets.UTF_8);

    private static final MediaType IMAGE_WEBP =
            MediaType.parseMediaType("image/webp");

    private RestClient restClient;
    private MockRestServiceServer mockServer;
    private PresignedImageDownloader downloader;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();

        mockServer = MockRestServiceServer
                .bindTo(builder)
                .build();

        restClient = builder.build();

        downloader = new PresignedImageDownloader(
                restClient,
                ALLOWED_ORIGIN
        );
    }

    @AfterEach
    void verifyMockServer() {
        mockServer.verify();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("supportedImageFormats")
    @DisplayName("JPEG, PNG, WEBP 이미지를 올바른 MultipartFile로 변환한다")
    void downloadSupportedImageAsMultipartFile(
            MediaType responseContentType,
            String expectedExtension
    ) throws IOException {
        expectResponse(
                VALID_URL,
                withSuccess(IMAGE_CONTENT, responseContentType)
        );

        MultipartFile multipartFile =
                downloader.downloadAsMultipart(validPhoto(VALID_URL));

        assertThat(multipartFile.getName())
                .isEqualTo("image");

        assertThat(multipartFile.getOriginalFilename())
                .isEqualTo("photo-42" + expectedExtension);

        assertThat(multipartFile.getContentType())
                .isEqualTo(responseContentType.toString());

        assertThat(multipartFile.getSize())
                .isEqualTo(IMAGE_CONTENT.length);

        assertThat(multipartFile.getBytes())
                .containsExactly(IMAGE_CONTENT);
    }

    @ParameterizedTest(
            name = "[{index}] 기본 HTTPS 포트와 명시적 443 포트를 동일하게 허용한다"
    )
    @MethodSource("equivalentHttpsUrls")
    @DisplayName("기본 HTTPS 포트와 명시적 443 포트를 같은 origin으로 처리한다")
    void allowDefaultAndExplicitHttpsPort(String url) {
        expectResponse(
                url,
                withSuccess(IMAGE_CONTENT, MediaType.IMAGE_JPEG)
        );

        MultipartFile multipartFile =
                downloader.downloadAsMultipart(validPhoto(url));

        assertThat(multipartFile.getSize())
                .isEqualTo(IMAGE_CONTENT.length);
    }

    @ParameterizedTest(
            name = "[{index}] 잘못되거나 허용되지 않은 URL을 요청 전에 차단한다"
    )
    @MethodSource("invalidPresignedUrls")
    @DisplayName("잘못되거나 허용되지 않은 Presigned URL을 차단한다")
    void rejectInvalidPresignedUrlBeforeHttpRequest(String url) {
        assertFailure(
                validPhoto(url),
                Reason.INVALID_URL
        );
    }

    @Test
    @DisplayName("null 허용 origin은 생성자에서 차단하고 설정값을 노출하지 않는다")
    void rejectNullAllowedOriginWithoutExposure() {
        assertInvalidAllowedOrigin(null);
    }

    @ParameterizedTest(
            name = "[{index}] 올바르지 않은 허용 origin을 생성자에서 차단한다"
    )
    @MethodSource("invalidAllowedOrigins")
    @DisplayName("올바르지 않은 allowed-origin 설정을 생성자에서 차단한다")
    void rejectInvalidAllowedOriginWithoutExposure(
            String configuredOrigin
    ) {
        assertInvalidAllowedOrigin(configuredOrigin);
    }

    @Test
    @DisplayName("이미 만료된 URL은 HTTP 요청 전에 차단한다")
    void rejectExpiredUrlBeforeHttpRequest() {
        DailyCultivationPhotoResponse photo =
                new DailyCultivationPhotoResponse(
                        CULTIVATION_ID,
                        PHOTO_ID,
                        VALID_URL,
                        OffsetDateTime.now().minusMinutes(1)
                );

        assertFailure(
                photo,
                Reason.EXPIRED_URL
        );
    }

    @Test
    @DisplayName("만료 시각이 없는 URL은 HTTP 요청 전에 차단한다")
    void rejectMissingExpirationBeforeHttpRequest() {
        DailyCultivationPhotoResponse photo =
                new DailyCultivationPhotoResponse(
                        CULTIVATION_ID,
                        PHOTO_ID,
                        VALID_URL,
                        null
                );

        assertFailure(
                photo,
                Reason.EXPIRED_URL
        );
    }

    @ParameterizedTest(name = "[{index}] HTTP {0} 응답을 차단한다")
    @ValueSource(ints = {206, 302, 403, 500})
    @DisplayName("200 OK 이외의 HTTP 상태를 모두 차단한다")
    void rejectNonOkStatus(int status) {
        expectResponse(
                VALID_URL,
                withRawStatus(status)
        );

        assertFailure(
                validPhoto(VALID_URL),
                Reason.HTTP_ERROR
        );
    }

    @Test
    @DisplayName("Content-Type이 없는 응답을 차단한다")
    void rejectMissingContentType() {
        expectResponse(
                VALID_URL,
                withSuccess(IMAGE_CONTENT, null)
        );

        assertFailure(
                validPhoto(VALID_URL),
                Reason.UNSUPPORTED_CONTENT_TYPE
        );
    }

    @ParameterizedTest(
            name = "[{index}] 지원하지 않는 Content-Type {0}을 차단한다"
    )
    @ValueSource(
            strings = {
                    "image/gif",
                    "application/octet-stream"
            }
    )
    @DisplayName("지원하지 않는 이미지 Content-Type을 차단한다")
    void rejectUnsupportedContentType(String contentType) {
        DefaultResponseCreator responseCreator =
                withSuccess(IMAGE_CONTENT, null)
                        .header(
                                HttpHeaders.CONTENT_TYPE,
                                contentType
                        );

        expectResponse(
                VALID_URL,
                responseCreator
        );

        assertFailure(
                validPhoto(VALID_URL),
                Reason.UNSUPPORTED_CONTENT_TYPE
        );
    }

    @Test
    @DisplayName("문법이 잘못된 Content-Type을 차단하고 외부 값을 노출하지 않는다")
    void rejectMalformedContentTypeWithoutExposure() {
        DefaultResponseCreator responseCreator =
                withSuccess(IMAGE_CONTENT, null)
                        .header(
                                HttpHeaders.CONTENT_TYPE,
                                MALFORMED_CONTENT_TYPE
                        );

        expectResponse(
                VALID_URL,
                responseCreator
        );

        ImageDownloadException exception =
                assertFailure(
                        validPhoto(VALID_URL),
                        Reason.UNSUPPORTED_CONTENT_TYPE
                );

        assertThat(exception.getMessage())
                .doesNotContain(MALFORMED_CONTENT_TYPE);

        assertThat(exception.getLogContent())
                .doesNotContain(MALFORMED_CONTENT_TYPE);

        assertThat(exception.getCause())
                .isNull();
    }

    @Test
    @DisplayName("숫자가 아닌 Content-Length를 HTTP 오류로 처리한다")
    void rejectNonNumericContentLength() {
        DefaultResponseCreator responseCreator =
                withSuccess(IMAGE_CONTENT, MediaType.IMAGE_JPEG)
                        .header(
                                HttpHeaders.CONTENT_LENGTH,
                                INVALID_CONTENT_LENGTH
                        );

        expectResponse(
                VALID_URL,
                responseCreator
        );

        ImageDownloadException exception =
                assertFailure(
                        validPhoto(VALID_URL),
                        Reason.HTTP_ERROR
                );

        assertThat(exception.getMessage())
                .doesNotContain(INVALID_CONTENT_LENGTH);

        assertThat(exception.getLogContent())
                .doesNotContain(INVALID_CONTENT_LENGTH);
    }

    @Test
    @DisplayName("8 MiB를 초과한다고 선언된 Content-Length를 본문 읽기 전에 차단한다")
    void rejectDeclaredContentLengthBeyondMaximum() {
        DefaultResponseCreator responseCreator =
                withSuccess(IMAGE_CONTENT, MediaType.IMAGE_JPEG)
                        .header(
                                HttpHeaders.CONTENT_LENGTH,
                                String.valueOf(
                                        PresignedImageDownloader
                                                .MAX_IMAGE_SIZE_BYTES + 1
                                )
                        );

        expectResponse(
                VALID_URL,
                responseCreator
        );

        assertFailure(
                validPhoto(VALID_URL),
                Reason.CONTENT_TOO_LARGE
        );
    }

    @Test
    @DisplayName("정확히 8 MiB인 실제 이미지 본문을 허용한다")
    void allowBodyAtExactMaximumSize() {
        byte[] maximumSizeContent =
                new byte[
                        PresignedImageDownloader.MAX_IMAGE_SIZE_BYTES
                        ];

        DefaultResponseCreator responseCreator =
                withSuccess(
                        maximumSizeContent,
                        MediaType.IMAGE_JPEG
                ).header(
                        HttpHeaders.CONTENT_LENGTH,
                        String.valueOf(maximumSizeContent.length)
                );

        expectResponse(
                VALID_URL,
                responseCreator
        );

        MultipartFile multipartFile =
                downloader.downloadAsMultipart(validPhoto(VALID_URL));

        assertThat(multipartFile.getSize())
                .isEqualTo(
                        PresignedImageDownloader.MAX_IMAGE_SIZE_BYTES
                );
    }

    @ParameterizedTest(
            name = "[{index}] Content-Length가 없거나 작아도 실제 크기를 검사한다"
    )
    @NullSource
    @ValueSource(strings = "1")
    @DisplayName("실제 본문이 8 MiB보다 크면 선언 길이와 관계없이 차단한다")
    void rejectActualBodyBeyondMaximum(
            String declaredLength
    ) {
        byte[] oversizedContent =
                new byte[
                        PresignedImageDownloader.MAX_IMAGE_SIZE_BYTES + 1
                        ];

        DefaultResponseCreator responseCreator =
                withSuccess(
                        oversizedContent,
                        MediaType.IMAGE_JPEG
                );

        if (declaredLength != null) {
            responseCreator.header(
                    HttpHeaders.CONTENT_LENGTH,
                    declaredLength
            );
        }

        expectResponse(
                VALID_URL,
                responseCreator
        );

        assertFailure(
                validPhoto(VALID_URL),
                Reason.CONTENT_TOO_LARGE
        );
    }

    @Test
    @DisplayName("빈 이미지 본문을 차단한다")
    void rejectEmptyContent() {
        expectResponse(
                VALID_URL,
                withSuccess(
                        new byte[0],
                        MediaType.IMAGE_JPEG
                )
        );

        assertFailure(
                validPhoto(VALID_URL),
                Reason.EMPTY_CONTENT
        );
    }

    @Test
    @DisplayName("Content-Encoding 헤더가 없는 응답을 허용한다")
    void allowMissingContentEncoding() {
        expectResponse(
                VALID_URL,
                withSuccess(IMAGE_CONTENT, MediaType.IMAGE_JPEG)
        );

        MultipartFile multipartFile =
                downloader.downloadAsMultipart(validPhoto(VALID_URL));

        assertThat(multipartFile.getSize())
                .isEqualTo(IMAGE_CONTENT.length);
    }

    @Test
    @DisplayName("모든 Content-Encoding 값과 토큰이 identity이면 허용한다")
    void allowOnlyIdentityContentEncodingTokens() {
        DefaultResponseCreator responseCreator =
                withSuccess(IMAGE_CONTENT, MediaType.IMAGE_JPEG)
                        .header(
                                HttpHeaders.CONTENT_ENCODING,
                                "identity, IDENTITY"
                        )
                        .header(
                                HttpHeaders.CONTENT_ENCODING,
                                " identity "
                        );

        expectResponse(
                VALID_URL,
                responseCreator
        );

        MultipartFile multipartFile =
                downloader.downloadAsMultipart(validPhoto(VALID_URL));

        assertThat(multipartFile.getSize())
                .isEqualTo(IMAGE_CONTENT.length);
    }

    @Test
    @DisplayName("두 번째 Content-Encoding 헤더가 gzip이면 차단한다")
    void rejectGzipInSecondContentEncodingHeader() {
        DefaultResponseCreator responseCreator =
                withSuccess(IMAGE_CONTENT, MediaType.IMAGE_JPEG)
                        .header(
                                HttpHeaders.CONTENT_ENCODING,
                                "identity"
                        )
                        .header(
                                HttpHeaders.CONTENT_ENCODING,
                                "gzip"
                        );

        expectResponse(
                VALID_URL,
                responseCreator
        );

        assertFailure(
                validPhoto(VALID_URL),
                Reason.HTTP_ERROR
        );
    }

    @ParameterizedTest(
            name = "[{index}] Content-Encoding 값 \"{0}\"을 차단한다"
    )
    @ValueSource(
            strings = {
                    "gzip",
                    "identity, gzip",
                    "identity,"
            }
    )
    @DisplayName("identity 이외의 Content-Encoding과 빈 토큰을 차단한다")
    void rejectInvalidContentEncoding(String contentEncoding) {
        DefaultResponseCreator responseCreator =
                withSuccess(IMAGE_CONTENT, MediaType.IMAGE_JPEG)
                        .header(
                                HttpHeaders.CONTENT_ENCODING,
                                contentEncoding
                        );

        expectResponse(
                VALID_URL,
                responseCreator
        );

        assertFailure(
                validPhoto(VALID_URL),
                Reason.HTTP_ERROR
        );
    }

    @Test
    @DisplayName("I/O 실패를 NETWORK_ERROR로 변환하고 외부 예외 정보를 제거한다")
    void convertIoFailureToNetworkErrorWithoutExposure() {
        expectResponse(
                VALID_URL,
                withException(
                        new IOException(EXTERNAL_IO_MESSAGE)
                )
        );

        ImageDownloadException exception =
                assertFailure(
                        validPhoto(VALID_URL),
                        Reason.NETWORK_ERROR
                );

        assertThat(exception.getMessage())
                .doesNotContain(EXTERNAL_IO_MESSAGE);

        assertThat(exception.getLogContent())
                .doesNotContain(EXTERNAL_IO_MESSAGE);

        assertThat(exception.getCause())
                .isNull();
    }

    @Test
    @DisplayName("인터럽트된 스레드의 I/O 실패를 INTERRUPTED로 변환한다")
    void convertInterruptedIoFailureToInterrupted() {
        expectResponse(
                VALID_URL,
                withException(
                        new IOException(EXTERNAL_IO_MESSAGE)
                )
        );

        try {
            Thread.currentThread().interrupt();

            ImageDownloadException exception =
                    assertFailure(
                            validPhoto(VALID_URL),
                            Reason.INTERRUPTED
                    );

            assertThat(exception.getMessage())
                    .doesNotContain(EXTERNAL_IO_MESSAGE);

            assertThat(exception.getLogContent())
                    .doesNotContain(EXTERNAL_IO_MESSAGE);

            assertThat(exception.getCause())
                    .isNull();
        } finally {
            Thread.interrupted();
        }
    }

    private DailyCultivationPhotoResponse validPhoto(String url) {
        return new DailyCultivationPhotoResponse(
                CULTIVATION_ID,
                PHOTO_ID,
                url,
                OffsetDateTime.now().plusMinutes(5)
        );
    }

    private void expectResponse(
            String url,
            ResponseCreator responseCreator
    ) {
        mockServer.expect(requestTo(url))
                .andExpect(method(HttpMethod.GET))
                .andExpect(
                        header(
                                HttpHeaders.ACCEPT,
                                EXPECTED_ACCEPT_HEADER
                        )
                )
                .andRespond(responseCreator);
    }

    private ImageDownloadException assertFailure(
            DailyCultivationPhotoResponse photo,
            Reason expectedReason
    ) {
        ImageDownloadException exception =
                catchThrowableOfType(
                        () -> downloader.downloadAsMultipart(photo),
                        ImageDownloadException.class
                );

        assertExceptionReason(
                exception,
                expectedReason
        );

        return exception;
    }

    private void assertExceptionReason(
            ImageDownloadException exception,
            Reason expectedReason
    ) {
        String expectedLogContent =
                "Vision 분석용 사진 다운로드 실패: "
                        + "photoId=%s, reason=%s"
                        .formatted(PHOTO_ID, expectedReason);

        assertThat(exception.getMessage())
                .isEqualTo(USER_MESSAGE)
                .doesNotContain(
                        VALID_URL,
                        FAKE_SIGNATURE,
                        "X-Amz-Signature"
                );

        assertThat(exception.getLogContent())
                .isEqualTo(expectedLogContent)
                .doesNotContain(
                        VALID_URL,
                        FAKE_SIGNATURE,
                        "X-Amz-Signature"
                );

        assertThat(exception.getCause())
                .isNull();
    }

    private void assertInvalidAllowedOrigin(
            String configuredOrigin
    ) {
        IllegalStateException exception =
                catchThrowableOfType(
                        () -> new PresignedImageDownloader(
                                restClient,
                                configuredOrigin
                        ),
                        IllegalStateException.class
                );

        assertThat(exception.getMessage())
                .isEqualTo(INVALID_ALLOWED_ORIGIN_MESSAGE);

        assertThat(exception.getCause())
                .isNull();

        if (configuredOrigin != null
                && !configuredOrigin.isBlank()) {
            assertThat(exception.getMessage())
                    .doesNotContain(configuredOrigin);
        }
    }

    private static Stream<Arguments> supportedImageFormats() {
        return Stream.of(
                Arguments.of(
                        MediaType.IMAGE_JPEG,
                        ".jpg"
                ),
                Arguments.of(
                        MediaType.IMAGE_PNG,
                        ".png"
                ),
                Arguments.of(
                        IMAGE_WEBP,
                        ".webp"
                )
        );
    }

    private static Stream<String> equivalentHttpsUrls() {
        return Stream.of(
                "https://storage.test/photos/42"
                        + "?X-Amz-Signature="
                        + FAKE_SIGNATURE,
                "https://storage.test:443/photos/42"
                        + "?X-Amz-Signature="
                        + FAKE_SIGNATURE
        );
    }

    private static Stream<String> invalidPresignedUrls() {
        return Stream.of(
                "http://storage.test/photos/42"
                        + "?X-Amz-Signature="
                        + FAKE_SIGNATURE,
                "https://other.test/photos/42"
                        + "?X-Amz-Signature="
                        + FAKE_SIGNATURE,
                "https://storage.test.evil/photos/42"
                        + "?X-Amz-Signature="
                        + FAKE_SIGNATURE,
                "https://storage.test:444/photos/42"
                        + "?X-Amz-Signature="
                        + FAKE_SIGNATURE,
                "https://user@storage.test/photos/42"
                        + "?X-Amz-Signature="
                        + FAKE_SIGNATURE,
                "https://storage.test/photos/42"
                        + "?X-Amz-Signature="
                        + FAKE_SIGNATURE
                        + "#fragment",
                "ftp://storage.test/photos/42"
                        + "?X-Amz-Signature="
                        + FAKE_SIGNATURE,
                "https://[storage.test/photos/42"
                        + "?X-Amz-Signature="
                        + FAKE_SIGNATURE,
                "   "
        );
    }

    private static Stream<String> invalidAllowedOrigins() {
        return Stream.of(
                "   ",
                "storage.test",
                "ftp://storage.test",
                "https://user@storage.test",
                "https://storage.test/images",
                "https://storage.test?bucket=test",
                "https://storage.test#fragment",
                "https://storage.test:65536"
        );
    }
}
