package site.yesaido.ai_server.exception;

import site.yesaido.common.exception.client.BadRequestException;

/**
 * 일일 피드백 조회 요청값이 유효하지 않음을 나타냅니다.
 *
 * <p>사용자 ID 또는 재배지 ID가 null이거나 0 이하인 경우와
 * 피드백 날짜가 null인 경우에 사용하며, 잘못된 입력값 자체는
 * 예외 메시지에 포함하지 않습니다.</p>
 */
public class InvalidDailyFeedbackRequestException extends BadRequestException {

    /**
     * 안전한 고정 메시지를 사용하는 잘못된 조회 요청 예외를 생성합니다.
     */
    public InvalidDailyFeedbackRequestException() {
        super("일일 피드백 조회 요청값이 올바르지 않습니다.");
    }
}
