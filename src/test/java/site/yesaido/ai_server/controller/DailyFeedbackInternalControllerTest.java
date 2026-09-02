package site.yesaido.ai_server.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import site.yesaido.ai_server.dto.daily_feedback.DailyFeedbackBatchResult;
import site.yesaido.ai_server.dto.daily_feedback.DailyFeedbackBatchResult.CultivationResult;
import site.yesaido.ai_server.service.DailyFeedbackScheduledBatchService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DailyFeedbackInternalController.class)
class DailyFeedbackInternalControllerTest {

    private static final String ENDPOINT =
            "/api/v1/internal/daily-feedbacks/run-previous-day";

    private static final LocalDate FEEDBACK_DATE =
            LocalDate.of(2026, 9, 2);

    private static final OffsetDateTime SNAPSHOT_AT =
            OffsetDateTime.parse("2026-09-03T00:05:00+09:00");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DailyFeedbackInternalController controller;

    @MockitoBean
    private DailyFeedbackScheduledBatchService
            dailyFeedbackScheduledBatchService;

    @Test
    @DisplayName("전날 일일 피드백 배치를 실행하고 성공 응답을 반환한다")
    void runPreviousDayReturnsSuccessfulBatchResult() throws Exception {
        // 준비
        DailyFeedbackBatchResult result =
                DailyFeedbackBatchResult.from(
                        FEEDBACK_DATE,
                        SNAPSHOT_AT,
                        List.of(
                                CultivationResult.created(10L),
                                CultivationResult.existing(20L)
                        )
                );

        given(dailyFeedbackScheduledBatchService.executePreviousDay())
                .willReturn(result);

        // 실행 및 검증
        mockMvc.perform(post(ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(
                        jsonPath("$.message").value(
                                "요청이 성공적으로 처리되었습니다."
                        )
                )
                .andExpect(
                        jsonPath("$.data.feedbackDate")
                                .value("2026-09-02")
                )
                .andExpect(jsonPath("$.data.targetCount").value(2))
                .andExpect(jsonPath("$.data.createdCount").value(1))
                .andExpect(jsonPath("$.data.existingCount").value(1))
                .andExpect(jsonPath("$.data.failedCount").value(0))
                .andExpect(
                        jsonPath("$.data.results").value(hasSize(2))
                );

        verify(dailyFeedbackScheduledBatchService, times(1))
                .executePreviousDay();
    }

    @Test
    @DisplayName("서비스 실패를 HTTP 500 안전 응답으로 전파한다")
    void runPreviousDayReturnsInternalServerErrorWhenServiceFails()
            throws Exception {
        // 준비
        IllegalStateException failure =
                new IllegalStateException("batch failure");

        given(dailyFeedbackScheduledBatchService.executePreviousDay())
                .willThrow(failure);

        // 실행 및 검증
        mockMvc.perform(post(ENDPOINT))
                .andExpect(status().isInternalServerError())
                .andExpect(
                        jsonPath("$.detail")
                                .value("서버 내부에 오류가 발생했습니다.")
                )
                .andExpect(jsonPath("$.success").doesNotExist());

        verify(dailyFeedbackScheduledBatchService, times(1))
                .executePreviousDay();
    }

    @Test
    @DisplayName("Controller는 서비스 예외를 감싸거나 숨기지 않고 그대로 전파한다")
    void runPreviousDayPropagatesSameServiceException() {
        // 준비
        IllegalStateException expectedException =
                new IllegalStateException("batch failure");

        given(dailyFeedbackScheduledBatchService.executePreviousDay())
                .willThrow(expectedException);

        // 실행
        IllegalStateException actualException = catchThrowableOfType(
                IllegalStateException.class,
                controller::runPreviousDay
        );

        // 검증
        assertThat(actualException).isSameAs(expectedException);
        verify(dailyFeedbackScheduledBatchService, times(1))
                .executePreviousDay();
    }
}
