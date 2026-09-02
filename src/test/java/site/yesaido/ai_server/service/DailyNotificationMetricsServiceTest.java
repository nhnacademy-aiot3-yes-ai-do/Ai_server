package site.yesaido.ai_server.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.yesaido.ai_server.client.NotificationClient;
import site.yesaido.ai_server.dto.client.notification.DailyNotificationEventCountResponse;
import site.yesaido.ai_server.dto.client.notification.DailyNotificationSummariesResponse;
import site.yesaido.ai_server.dto.client.notification.DailyNotificationSummaryRequest;
import site.yesaido.ai_server.dto.client.notification.DailyNotificationSummaryResponse;
import site.yesaido.ai_server.dto.daily_feedback.DailyNotificationMetrics;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@SuppressWarnings("ConstantConditions")
@ExtendWith(MockitoExtension.class)
class DailyNotificationMetricsServiceTest {

    @Mock
    private NotificationClient notificationClient;

    @InjectMocks
    private DailyNotificationMetricsService service;

    @Test
    @DisplayName("정상 조회: 요청한 경작지들의 알림 지표 반환")
    void fetchDailyMetrics_success() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        List<Long> cultivationIds = List.of(1L);
        DailyNotificationSummariesResponse response = createSampleSummariesResponse(date);

        given(notificationClient.getDailySummaries(new DailyNotificationSummaryRequest(date, cultivationIds)))
                .willReturn(response);

        Map<Long, DailyNotificationMetrics> result = service.fetchDailyMetrics(date, cultivationIds);

        assertThat(result).containsKey(1L);
        assertThat(result.get(1L).cultivationId()).isEqualTo(1L);
        assertThat(result.get(1L).actuatorControlSucceededCount()).isEqualTo(4L);
        assertThat(result.get(1L).actuatorControlFailedCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("빈 목록 요청 시 빈 Map 반환")
    void fetchDailyMetrics_emptyList() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        Map<Long, DailyNotificationMetrics> result = service.fetchDailyMetrics(date, List.of());
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("예외: 응답 계약 위반 (null 응답, 날짜 불일치, 중복 경작지, ID 불일치)")
    void fetchDailyMetrics_responseViolations() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        List<Long> ids = List.of(1L);

        // 1. null 응답
        given(notificationClient.getDailySummaries(any())).willReturn(null);
        assertThatThrownBy(() -> service.fetchDailyMetrics(date, ids))
                .isInstanceOf(IllegalStateException.class);

        // 2. 날짜 불일치
        DailyNotificationSummaryResponse summaryOtherDate = new DailyNotificationSummaryResponse(
                1L, LocalDate.of(2026, 9, 2), 0L, List.of()
        );
        DailyNotificationSummariesResponse mismatchedDateResponse = new DailyNotificationSummariesResponse(
                LocalDate.of(2026, 9, 2), "Asia/Seoul", List.of(summaryOtherDate)
        );
        given(notificationClient.getDailySummaries(any())).willReturn(mismatchedDateResponse);
        assertThatThrownBy(() -> service.fetchDailyMetrics(date, ids))
                .isInstanceOf(IllegalStateException.class);

        // 3. 경작지 ID 불일치 (2L 응답 수신)
        DailyNotificationSummaryResponse wrongIdSummary = new DailyNotificationSummaryResponse(
                2L, date, 0L, List.of()
        );
        DailyNotificationSummariesResponse wrongIdResponse = new DailyNotificationSummariesResponse(
                date, "Asia/Seoul", List.of(wrongIdSummary)
        );
        given(notificationClient.getDailySummaries(any())).willReturn(wrongIdResponse);
        assertThatThrownBy(() -> service.fetchDailyMetrics(date, ids))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("예외: date 또는 cultivationIds가 null일 때")
    void fetchDailyMetrics_nullParams() {
        List<Long> ids = List.of(1L);
        LocalDate date = LocalDate.of(2026, 9, 1);

        assertThatThrownBy(() -> service.fetchDailyMetrics(null, ids))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.fetchDailyMetrics(date, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private DailyNotificationSummariesResponse createSampleSummariesResponse(LocalDate date) {
        DailyNotificationEventCountResponse event1 =
                new DailyNotificationEventCountResponse("ACTUATOR_CONTROL_SUCCEEDED", "액추에이터 성공", 4L);
        DailyNotificationEventCountResponse event2 =
                new DailyNotificationEventCountResponse("ACTUATOR_CONTROL_FAILED", "액추에이터 실패", 1L);

        DailyNotificationSummaryResponse summary = new DailyNotificationSummaryResponse(
                1L, date, 5L, List.of(event1, event2)
        );
        return new DailyNotificationSummariesResponse(date, "Asia/Seoul", List.of(summary));
    }
}
