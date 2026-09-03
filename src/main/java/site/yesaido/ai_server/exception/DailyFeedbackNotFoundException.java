package site.yesaido.ai_server.exception;

import site.yesaido.common.exception.client.NotFoundException;

import java.time.LocalDate;

/**
 * 요청한 재배지와 날짜에 대응하는 일일 피드백을 찾을 수 없음을 나타냅니다.
 *
 * <p>일일 피드백이 저장되지 않았거나 Cultivation Server에서 해당 재배지를
 * 찾을 수 없는 경우를 동일한 HTTP 404 응답으로 표현합니다.</p>
 */
public class DailyFeedbackNotFoundException extends NotFoundException {

    /**
     * 조회 대상 재배지와 피드백 날짜를 포함한 안전한 예외를 생성합니다.
     *
     * @param cultivationId 조회한 재배지 ID
     * @param feedbackDate 조회한 일일 피드백 날짜
     */
    public DailyFeedbackNotFoundException(Long cultivationId, LocalDate feedbackDate) {
        super("해당 재배지와 날짜의 일일 피드백이 존재하지 않습니다. cultivationId: %s, feedbackDate: %s"
                .formatted(cultivationId, feedbackDate));
    }
}
