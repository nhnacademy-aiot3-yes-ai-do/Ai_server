package site.yesaido.ai_server.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.yesaido.ai_server.dto.client.notification.DailyNotificationEventCountResponse;
import site.yesaido.ai_server.dto.client.notification.DailyNotificationSummaryResponse;
import site.yesaido.ai_server.dto.daily_feedback.DailyNotificationMetrics;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("ConstantConditions")
class DailyNotificationMetricsTest {

    @Test
    @DisplayName("fromSuccessfulResponse 팩토리 메서드로 이벤트 파싱 검증")
    void fromSuccessfulResponse_success() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        DailyNotificationSummaryResponse summary = createSampleSummary(date);

        DailyNotificationMetrics metrics = DailyNotificationMetrics.fromSuccessfulResponse(summary);

        assertThat(metrics.cultivationId()).isEqualTo(1L);
        assertThat(metrics.date()).isEqualTo(date);
        // 💡 2 + 3 + 1 = 6건 검증
        assertThat(metrics.totalNotificationCount()).isEqualTo(6L);
        assertThat(metrics.thresholdBreachAlertCount()).isEqualTo(2L);
        assertThat(metrics.actuatorControlSucceededCount()).isEqualTo(3L);
        assertThat(metrics.actuatorControlFailedCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("세부 지표 합계가 totalCount보다 클 때 예외 검증")
    void detailedCountGreaterThanTotal() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        assertThatThrownBy(() -> new DailyNotificationMetrics(1L, date, 5L, 3L, 3L, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // 개별 카운트(2 + 3 + 1 = 6L)와 totalCount(6L)를 일치시킨 헬퍼 메서드
    private DailyNotificationSummaryResponse createSampleSummary(LocalDate date) {
        DailyNotificationEventCountResponse breach = new DailyNotificationEventCountResponse(
                "ENVIRONMENT_THRESHOLD_BREACHED", "임계값 이탈", 2L
        );
        DailyNotificationEventCountResponse success = new DailyNotificationEventCountResponse(
                "ACTUATOR_CONTROL_SUCCEEDED", "제어 성공", 3L
        );
        DailyNotificationEventCountResponse failed = new DailyNotificationEventCountResponse(
                "ACTUATOR_CONTROL_FAILED", "제어 실패", 1L
        );

        return new DailyNotificationSummaryResponse(
                1L, date, 6L, List.of(breach, success, failed)
        );
    }
}
