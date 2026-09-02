package site.yesaido.ai_server.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.yesaido.ai_server.dto.client.notification.DailyNotificationEventCountResponse;
import site.yesaido.ai_server.dto.client.notification.DailyNotificationSummariesResponse;
import site.yesaido.ai_server.dto.client.notification.DailyNotificationSummaryRequest;
import site.yesaido.ai_server.dto.client.notification.DailyNotificationSummaryResponse;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClientNotificationDtoTest {

    @Test
    @DisplayName("DailyNotificationSummaryRequest 생성 및 getter 검증")
    void notificationRequest() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        List<Long> ids = List.of(1L, 2L);
        DailyNotificationSummaryRequest request = new DailyNotificationSummaryRequest(date, ids);

        assertThat(request.date()).isEqualTo(date);
        assertThat(request.cultivationIds()).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("DailyNotificationSummariesResponse 계층 구조 생성 검증")
    void notificationSummariesResponse() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        DailyNotificationEventCountResponse event = new DailyNotificationEventCountResponse(
                "ACTUATOR_CONTROL_SUCCEEDED", "성공", 10L
        );
        DailyNotificationSummaryResponse summary = new DailyNotificationSummaryResponse(
                1L, date, 10L, List.of(event)
        );
        DailyNotificationSummariesResponse response = new DailyNotificationSummariesResponse(
                date, "Asia/Seoul", List.of(summary)
        );

        assertThat(response.date()).isEqualTo(date);
        assertThat(response.zoneId()).isEqualTo("Asia/Seoul");
        assertThat(response.summaries()).hasSize(1);
        assertThat(response.summaries().getFirst().cultivationId()).isEqualTo(1L);
        assertThat(response.summaries().getFirst().eventCounts().getFirst().count()).isEqualTo(10L);
    }
}
