package site.yesaido.ai_server.exception;

import site.yesaido.common.exception.server.CustomServerException;
import site.yesaido.common.exception.server.ServerErrorLevel;

import java.time.LocalDate;

/**
 * 일일 피드백 Context를 저장 가능한 형태로 준비하는 과정에서
 * 발생하는 예측 가능한 서버 측 실패입니다.
 *
 * <p>원본 Context JSON, 센서 EUI, Vision 결과와 외부 예외 메시지는
 * 사용자 메시지나 내부 로그 내용에 포함하지 않습니다.</p>
 */
public class DailyFeedbackProcessingException extends CustomServerException {

    private static final String USER_MESSAGE = "일일 피드백을 처리하지 못했습니다.";

    /**
     * 별도의 원본 예외 없이 계약 위반을 나타냅니다.
     *
     * @param cultivationId 처리 대상 경작지 ID
     * @param feedbackDate 피드백 대상 날짜
     * @param reason 안전하게 분류된 실패 원인
     */
    public DailyFeedbackProcessingException(
            Long cultivationId,
            LocalDate feedbackDate,
            Reason reason
    ) {
        this(cultivationId, feedbackDate, reason, null);
    }

    /**
     * 원본 예외의 메시지나 데이터를 노출하지 않고 예외 타입만 기록합니다.
     *
     * @param cultivationId 처리 대상 경작지 ID
     * @param feedbackDate 피드백 대상 날짜
     * @param reason 안전하게 분류된 실패 원인
     * @param cause JSON 변환 과정에서 발생한 원본 예외
     */
    public DailyFeedbackProcessingException(
            Long cultivationId,
            LocalDate feedbackDate,
            Reason reason,
            Throwable cause
    ) {
        super(
                USER_MESSAGE,
                createLogContent(
                        cultivationId,
                        feedbackDate,
                        reason,
                        cause
                ),
                ServerErrorLevel.ERROR_LEVEL
        );
    }

    private static String createLogContent(
            Long cultivationId,
            LocalDate feedbackDate,
            Reason reason,
            Throwable cause
    ) {
        String exceptionType = cause == null ? "NONE" : cause.getClass().getSimpleName();

        return "일일 피드백 처리 실패: cultivationId=%s, feedbackDate=%s, reason=%s, exceptionType=%s"
                .formatted(cultivationId, feedbackDate, reason, exceptionType)
                .strip();
    }

    /**
     * 외부 데이터나 민감한 원문을 저장하지 않는 내부 실패 코드입니다.
     */
    public enum Reason {

        /**
         * ObjectMapper가 원본 Context를 JsonNode로 변환하지 못한 경우입니다.
         */
        CONTEXT_SNAPSHOT_SERIALIZATION_FAILED,

        /**
         * 변환 결과가 null이거나 JSON object가 아닌 경우입니다.
         */
        INVALID_CONTEXT_SNAPSHOT
    }
}
