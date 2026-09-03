package site.yesaido.ai_server.dto.daily_feedback;

import site.yesaido.ai_server.entity.DailyFeedback;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 운영용 일일 피드백 조회 응답입니다.
 *
 * <p>피드백 생성 근거인 Context Snapshot은 외부에 노출하지 않고,
 * 사용자에게 필요한 피드백 정보만 제공합니다.</p>
 *
 * @param dailyFeedbackId 일일 피드백 ID
 * @param cultivationId 재배지 ID
 * @param feedbackDate 피드백 대상 날짜
 * @param hasVisionAnalysis Vision 분석 반영 여부
 * @param content 생성된 일일 피드백 원문
 * @param createdAt 최초 생성 시각
 */
public record DailyFeedbackResponse(
        Long dailyFeedbackId,
        Long cultivationId,
        LocalDate feedbackDate,
        boolean hasVisionAnalysis,
        String content,
        LocalDateTime createdAt
) {

    /**
     * 저장된 일일 피드백 엔티티를 운영용 조회 응답으로 변환합니다.
     *
     * @param feedback 조회된 일일 피드백 엔티티
     * @return Context Snapshot을 포함하지 않는 운영용 응답
     */
    public static DailyFeedbackResponse from(DailyFeedback feedback) {
        return new DailyFeedbackResponse(
                feedback.getId(),
                feedback.getCultivationId(),
                feedback.getFeedbackDate(),
                feedback.isHasVisionAnalysis(),
                feedback.getContent(),
                feedback.getCreatedAt()
        );
    }
}
