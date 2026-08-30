package site.yesaido.ai_server.exception;

import site.yesaido.common.exception.server.CustomServerException;
import site.yesaido.common.exception.server.ServerErrorLevel;

/**
 * Vision 응답 검증, JSON 변환 또는 분석 결과의 멱등성 처리 중 발생한 실패입니다.
 *
 * 외부 예외 메시지, Vision 응답 내용, Presigned URL과 query는
 * 사용자 메시지나 내부 로그에 포함하지 않습니다.
 */
public class VisionAnalysisException extends CustomServerException {

    private static final String USER_MESSAGE =
            "Vision 분석 결과를 처리하지 못했습니다.";

    public VisionAnalysisException(Long photoId, Reason reason) {
        super(USER_MESSAGE, "Vision 분석 결과 처리 실패: photoId=%s, reason=%s"
                .formatted(photoId, reason), ServerErrorLevel.ERROR_LEVEL);
    }

    /**
     * 외부 데이터나 예외 메시지를 저장하지 않고 Vision 결과 처리 실패를
     * 안전하게 구분하기 위한 내부 원인 코드입니다.
     */
    public enum Reason {

        /**
         * Cultivation 사진 DTO의 ID 등 분석에 필요한 필수 계약이 잘못된 경우입니다.
         */
        INVALID_PHOTO_CONTRACT,

        /**
         * Vision 응답의 버전, 상태 또는 결과 구조가 약속된 계약과 다른 경우입니다.
         */
        INVALID_RESPONSE_CONTRACT,

        /**
         * Vision 응답 전체를 JSONB 저장용 JsonNode로 변환하지 못한 경우입니다.
         */
        SERIALIZATION_FAILED,

        /**
         * UNIQUE 제약 충돌이 발생했지만 기존 growth_record를 다시 찾지 못한 경우입니다.
         */
        IDEMPOTENCY_CONFLICT
    }
}
