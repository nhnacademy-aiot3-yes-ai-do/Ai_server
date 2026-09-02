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
    @DisplayName("fromSuccessfulResponse 팩토리 메서드로 이벤트 파싱 및 기타 이벤트 무시 검증")
    void fromSuccessfulResponse_success() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        DailyNotificationEventCountResponse breach = new DailyNotificationEventCountResponse("ENVIRONMENT_THRESHOLD_BREACHED", "이탈", 2L);
        DailyNotificationEventCountResponse success = new DailyNotificationEventCountResponse("ACTUATOR_CONTROL_SUCCEEDED", "성공", 3L);
        DailyNotificationEventCountResponse failed = new DailyNotificationEventCountResponse("ACTUATOR_CONTROL_FAILED", "실패", 1L);
        DailyNotificationEventCountResponse other = new DailyNotificationEventCountResponse("OTHER_EVENT_TYPE", "기타", 4L);

        DailyNotificationSummaryResponse summary = new DailyNotificationSummaryResponse(
                1L, date, 10L, List.of(breach, success, failed, other)
        );

        DailyNotificationMetrics metrics = DailyNotificationMetrics.fromSuccessfulResponse(summary);

        assertThat(metrics.cultivationId()).isEqualTo(1L);
        assertThat(metrics.date()).isEqualTo(date);
        assertThat(metrics.totalNotificationCount()).isEqualTo(10L);
        assertThat(metrics.thresholdBreachAlertCount()).isEqualTo(2L);
        assertThat(metrics.actuatorControlSucceededCount()).isEqualTo(3L);
        assertThat(metrics.actuatorControlFailedCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("생성자 및 fromSuccessfulResponse 유효성 검증 실패 케이스들")
    void validationFailures() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        Long nullCultivationId = null;
        LocalDate nullDate = null;
        DailyNotificationSummaryResponse nullResponse = null;

        // 1. response null
        assertThatThrownBy(() -> DailyNotificationMetrics.fromSuccessfulResponse(nullResponse))
                .isInstanceOf(IllegalArgumentException.class);

        // 2. cultivationId <= 0 / null
        assertThatThrownBy(() -> new DailyNotificationMetrics(nullCultivationId, date, 5L, 1L, 1L, 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DailyNotificationMetrics(0L, date, 5L, 1L, 1L, 1L))
                .isInstanceOf(IllegalArgumentException.class);

        // 3. date null
        assertThatThrownBy(() -> new DailyNotificationMetrics(1L, nullDate, 5L, 1L, 1L, 1L))
                .isInstanceOf(IllegalArgumentException.class);

        // 4. 음수 count들
        assertThatThrownBy(() -> new DailyNotificationMetrics(1L, date, -1L, 0L, 0L, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DailyNotificationMetrics(1L, date, 5L, -1L, 0L, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DailyNotificationMetrics(1L, date, 5L, 0L, -1L, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DailyNotificationMetrics(1L, date, 5L, 0L, 0L, -1L))
                .isInstanceOf(IllegalArgumentException.class);

        // 5. 세부 지표 합계가 totalCount보다 클 때
        assertThatThrownBy(() -> new DailyNotificationMetrics(1L, date, 5L, 3L, 3L, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
