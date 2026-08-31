package site.yesaido.ai_server.exception;

import site.yesaido.common.exception.server.CustomServerException;
import site.yesaido.common.exception.server.ServerErrorLevel;

/**
 * Vision 분석용 사진을 다운로드하는 과정에서 발생한 예측 가능한 실패입니다.
 * Presigned URL은 서명 정보를 포함하므로 예외 메시지와 로그에 포함하지 않습니다.
 */
public class ImageDownloadException extends CustomServerException {

    private static final String USER_MESSAGE = "Vision 분석용 사진을 다운로드하지 못했습니다.";

    public ImageDownloadException(Long photoId, Reason reason) {
        super(USER_MESSAGE, "Vision 분석용 사진 다운로드 실패: photoId=%s, reason=%s".formatted(photoId, reason), ServerErrorLevel.WARN_LEVEL);
    }

    /**
     * URL이나 외부 예외 메시지를 기록하지 않고 실패 원인을 구분하기 위한 안전한 코드입니다.
     */
    public enum Reason {
        INVALID_URL,
        EXPIRED_URL,
        HTTP_ERROR,
        UNSUPPORTED_CONTENT_TYPE,
        CONTENT_TOO_LARGE,
        EMPTY_CONTENT,
        NETWORK_ERROR,
        INTERRUPTED
    }
}
