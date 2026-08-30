package site.yesaido.ai_server.storage.image;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;
import site.yesaido.ai_server.dto.cultivation.DailyCultivationPhotoResponse;
import site.yesaido.ai_server.exception.ImageDownloadException;
import site.yesaido.ai_server.storage.multipart.ByteArrayMultipartFile;

import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Component
public class PresignedImageDownloader {

    static final int MAX_IMAGE_SIZE_BYTES = 8 * 1024 * 1024;

    private static final int DEFAULT_HTTP_PORT = 80;
    private static final int DEFAULT_HTTPS_PORT = 443;
    private static final String MULTIPART_NAME = "image";
    private static final String INVALID_ALLOWED_ORIGIN_MESSAGE =
            "이미지 다운로드 허용 origin 설정이 올바르지 않습니다.";
    private static final MediaType IMAGE_WEBP =
            MediaType.parseMediaType("image/webp");

    private final RestClient restClient;
    private final URI allowedOrigin;

    public PresignedImageDownloader(
            @Qualifier("imageDownloadRestClient") RestClient restClient,
            @Value("${image-download.allowed-origin}") String allowedOrigin
    ) {
        this.restClient = Objects.requireNonNull(restClient);

        // 허용 origin 설정 오류는 첫 다운로드까지 미루지 않고 시작 단계에서 확인합니다.
        this.allowedOrigin = parseAllowedOrigin(allowedOrigin);
    }

    public MultipartFile downloadAsMultipart(
            DailyCultivationPhotoResponse photo
    ) {
        Objects.requireNonNull(photo);

        Long photoId = Objects.requireNonNull(photo.photoId());

        validateExpiration(photo);
        URI uri = parseUri(photoId, photo.presignedUrl());

        try {
            return restClient.get()
                    .uri(uri)
                    .accept(
                            MediaType.IMAGE_JPEG,
                            MediaType.IMAGE_PNG,
                            IMAGE_WEBP
                    )
                    .exchange((request, response) -> {
                        if (response.getStatusCode().value()
                                != HttpStatus.OK.value()) {
                            throw new ImageDownloadException(
                                    photoId,
                                    ImageDownloadException.Reason.HTTP_ERROR
                            );
                        }

                        HttpHeaders headers = response.getHeaders();

                        validateContentEncoding(photoId, headers);

                        long declaredLength =
                                readContentLength(photoId, headers);

                        if (declaredLength > MAX_IMAGE_SIZE_BYTES) {
                            throw new ImageDownloadException(
                                    photoId,
                                    ImageDownloadException.Reason.CONTENT_TOO_LARGE
                            );
                        }

                        MediaType contentType =
                                readContentType(photoId, headers);

                        ImageFormat format = resolveImageFormat(
                                photoId,
                                contentType
                        );

                        byte[] content;

                        try (InputStream inputStream = response.getBody()) {
                            // Content-Length가 없거나 부정확해도 실제 읽기 크기를 제한합니다.
                            content = inputStream.readNBytes(
                                    MAX_IMAGE_SIZE_BYTES + 1
                            );
                        }

                        if (content.length == 0) {
                            throw new ImageDownloadException(
                                    photoId,
                                    ImageDownloadException.Reason.EMPTY_CONTENT
                            );
                        }

                        if (content.length > MAX_IMAGE_SIZE_BYTES) {
                            throw new ImageDownloadException(
                                    photoId,
                                    ImageDownloadException.Reason.CONTENT_TOO_LARGE
                            );
                        }

                        return new ByteArrayMultipartFile(
                                MULTIPART_NAME,
                                "photo-%s%s".formatted(
                                        photoId,
                                        format.extension()
                                ),
                                format.contentType(),
                                content
                        );
                    });
        } catch (ImageDownloadException exception) {
            throw exception;
        } catch (RestClientException exception) {
            ImageDownloadException.Reason reason =
                    Thread.currentThread().isInterrupted()
                            ? ImageDownloadException.Reason.INTERRUPTED
                            : ImageDownloadException.Reason.NETWORK_ERROR;

            throw new ImageDownloadException(photoId, reason);
        }
    }

    private void validateContentEncoding(
            Long photoId,
            HttpHeaders headers
    ) {
        List<String> contentEncodings =
                headers.get(HttpHeaders.CONTENT_ENCODING);

        if (contentEncodings == null) {
            return;
        }

        if (contentEncodings.isEmpty()) {
            throw new ImageDownloadException(
                    photoId,
                    ImageDownloadException.Reason.HTTP_ERROR
            );
        }

        for (String headerValue : contentEncodings) {
            if (headerValue == null) {
                throw new ImageDownloadException(
                        photoId,
                        ImageDownloadException.Reason.HTTP_ERROR
                );
            }

            // -1을 사용해 마지막의 빈 토큰도 보존하고 잘못된 값으로 거부합니다.
            String[] tokens = headerValue.split(",", -1);

            for (String token : tokens) {
                if (!"identity".equalsIgnoreCase(token.trim())) {
                    throw new ImageDownloadException(
                            photoId,
                            ImageDownloadException.Reason.HTTP_ERROR
                    );
                }
            }
        }
    }

    private long readContentLength(
            Long photoId,
            HttpHeaders headers
    ) {
        try {
            return headers.getContentLength();
        } catch (NumberFormatException exception) {
            throw new ImageDownloadException(
                    photoId,
                    ImageDownloadException.Reason.HTTP_ERROR
            );
        }
    }

    private MediaType readContentType(
            Long photoId,
            HttpHeaders headers
    ) {
        try {
            return headers.getContentType();
        } catch (IllegalArgumentException exception) {
            throw new ImageDownloadException(
                    photoId,
                    ImageDownloadException.Reason.UNSUPPORTED_CONTENT_TYPE
            );
        }
    }

    private URI parseAllowedOrigin(String configuredOrigin) {
        if (configuredOrigin == null || configuredOrigin.isBlank()) {
            throw invalidAllowedOrigin();
        }

        try {
            URI uri = URI.create(configuredOrigin);
            String path = uri.getRawPath();
            int port = uri.getPort();

            boolean supportedPath =
                    path == null || path.isEmpty() || "/".equals(path);
            boolean validPort =
                    port == -1 || (port >= 1 && port <= 65_535);

            if (!isSupportedScheme(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null
                    || !supportedPath
                    || !validPort) {
                throw new IllegalArgumentException();
            }

            return uri;
        } catch (IllegalArgumentException exception) {
            throw invalidAllowedOrigin();
        }
    }

    private IllegalStateException invalidAllowedOrigin() {
        return new IllegalStateException(
                INVALID_ALLOWED_ORIGIN_MESSAGE
        );
    }

    private void validateExpiration(
            DailyCultivationPhotoResponse photo
    ) {
        if (photo.expiresAt() == null
                || !photo.expiresAt().toInstant().isAfter(Instant.now())) {
            throw new ImageDownloadException(
                    photo.photoId(),
                    ImageDownloadException.Reason.EXPIRED_URL
            );
        }
    }

    private URI parseUri(Long photoId, String presignedUrl) {
        if (presignedUrl == null || presignedUrl.isBlank()) {
            throw new ImageDownloadException(
                    photoId,
                    ImageDownloadException.Reason.INVALID_URL
            );
        }

        try {
            URI uri = URI.create(presignedUrl);

            if (!isSupportedScheme(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getRawUserInfo() != null
                    || uri.getRawFragment() != null
                    || !matchesAllowedOrigin(uri)) {
                throw new ImageDownloadException(
                        photoId,
                        ImageDownloadException.Reason.INVALID_URL
                );
            }

            return uri;
        } catch (IllegalArgumentException exception) {
            throw new ImageDownloadException(
                    photoId,
                    ImageDownloadException.Reason.INVALID_URL
            );
        }
    }

    private boolean matchesAllowedOrigin(URI uri) {
        return allowedOrigin.getScheme().equalsIgnoreCase(uri.getScheme())
                && allowedOrigin.getHost().equalsIgnoreCase(uri.getHost())
                && effectivePort(allowedOrigin) == effectivePort(uri);
    }

    private boolean isSupportedScheme(String scheme) {
        return "http".equalsIgnoreCase(scheme)
                || "https".equalsIgnoreCase(scheme);
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }

        return "https".equalsIgnoreCase(uri.getScheme())
                ? DEFAULT_HTTPS_PORT
                : DEFAULT_HTTP_PORT;
    }

    private ImageFormat resolveImageFormat(
            Long photoId,
            MediaType contentType
    ) {
        if (contentType == null) {
            throw new ImageDownloadException(
                    photoId,
                    ImageDownloadException.Reason.UNSUPPORTED_CONTENT_TYPE
            );
        }

        String normalizedContentType = (
                contentType.getType() + "/" + contentType.getSubtype()
        ).toLowerCase(Locale.ROOT);

        return switch (normalizedContentType) {
            case MediaType.IMAGE_JPEG_VALUE ->
                    new ImageFormat(MediaType.IMAGE_JPEG_VALUE, ".jpg");
            case MediaType.IMAGE_PNG_VALUE ->
                    new ImageFormat(MediaType.IMAGE_PNG_VALUE, ".png");
            case "image/webp" ->
                    new ImageFormat("image/webp", ".webp");
            default -> throw new ImageDownloadException(
                    photoId,
                    ImageDownloadException.Reason.UNSUPPORTED_CONTENT_TYPE
            );
        };
    }

    private record ImageFormat(
            String contentType,
            String extension
    ) {
    }
}
