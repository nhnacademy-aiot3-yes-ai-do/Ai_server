package site.yesaido.ai_server.dto.daily_feedback;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.yesaido.ai_server.entity.DailyFeedback;

import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class DailyFeedbackResponseTest {

    private static final Long DAILY_FEEDBACK_ID = 1001L;
    private static final Long CULTIVATION_ID = 10L;
    private static final LocalDate FEEDBACK_DATE =
            LocalDate.of(2026, 9, 1);
    private static final LocalDateTime CREATED_AT =
            LocalDateTime.of(2026, 9, 2, 0, 5);
    private static final String CONTENT =
            "# 오늘의 재배 환경 요약\n환경이 안정적으로 유지되었습니다.";

    @Test
    @DisplayName("저장된 일일 피드백을 운영 조회 응답으로 정확히 변환한다")
    void convertStoredDailyFeedbackToResponse() {
        // 준비
        DailyFeedback feedback = mock(DailyFeedback.class);

        given(feedback.getId()).willReturn(DAILY_FEEDBACK_ID);
        given(feedback.getCultivationId()).willReturn(CULTIVATION_ID);
        given(feedback.getFeedbackDate()).willReturn(FEEDBACK_DATE);
        given(feedback.isHasVisionAnalysis()).willReturn(true);
        given(feedback.getContent()).willReturn(CONTENT);
        given(feedback.getCreatedAt()).willReturn(CREATED_AT);

        // 실행
        DailyFeedbackResponse response =
                DailyFeedbackResponse.from(feedback);

        // 검증
        assertThat(response.dailyFeedbackId())
                .isEqualTo(DAILY_FEEDBACK_ID);
        assertThat(response.cultivationId())
                .isEqualTo(CULTIVATION_ID);
        assertThat(response.feedbackDate())
                .isEqualTo(FEEDBACK_DATE);
        assertThat(response.hasVisionAnalysis()).isTrue();
        assertThat(response.content()).isEqualTo(CONTENT);
        assertThat(response.createdAt()).isEqualTo(CREATED_AT);
    }

    @Test
    @DisplayName("운영 조회 응답은 Context Snapshot을 노출하지 않는다")
    void excludeContextSnapshotFromResponseContract() {
        // 준비
        RecordComponent[] recordComponents =
                DailyFeedbackResponse.class.getRecordComponents();

        // 실행
        List<String> componentNames = Arrays.stream(recordComponents)
                .map(RecordComponent::getName)
                .toList();

        // 검증
        assertThat(componentNames).containsExactly(
                "dailyFeedbackId",
                "cultivationId",
                "feedbackDate",
                "hasVisionAnalysis",
                "content",
                "createdAt"
        ).doesNotContain("contextSnapshot");
    }
}
