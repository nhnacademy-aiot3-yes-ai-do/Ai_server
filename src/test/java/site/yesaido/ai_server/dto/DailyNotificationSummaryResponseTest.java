package site.yesaido.ai_server.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.yesaido.ai_server.dto.client.notification.DailyNotificationEventCountResponse;
import site.yesaido.ai_server.dto.client.notification.DailyNotificationSummaryResponse;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("ConstantConditions")
class DailyNotificationSummaryResponseTest {

    @Test
    @DisplayName("정상 생성 및 합계 검증")
    void create_success() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        DailyNotificationEventCountResponse event1 = new DailyNotificationEventCountResponse("ACTUATOR_CONTROL_SUCCEEDED", "성공", 3L);
        DailyNotificationEventCountResponse event2 = new DailyNotificationEventCountResponse("ACTUATOR_CONTROL_FAILED", "실패", 2L);
        List<DailyNotificationEventCountResponse> events = List.of(event1, event2);

        DailyNotificationSummaryResponse response = new DailyNotificationSummaryResponse(
                1L, date, 5L, events
        );

        assertThat(response.cultivationId()).isEqualTo(1L);
        assertThat(response.date()).isEqualTo(date);
        assertThat(response.totalCount()).isEqualTo(5L);
        assertThat(response.eventCounts()).hasSize(2);
    }

    @Test
    @DisplayName("유효성 검증 실패 케이스들 (합계 불일치, null 요소, 중복 이벤트 등)")
    void create_validationFailures() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        DailyNotificationEventCountResponse event1 = new DailyNotificationEventCountResponse("ACTUATOR_CONTROL_SUCCEEDED", "성공", 3L);
        List<DailyNotificationEventCountResponse> validEvents = List.of(event1);

        Long nullCultivationId = null;
        Long zeroCultivationId = 0L;
        LocalDate nullDate = null;
        Long nullTotalCount = null;
        Long negativeTotalCount = -1L;
        Long mismatchedTotalCount = 10L;
        List<DailyNotificationEventCountResponse> nullEvents = null;

        // 1. cultivationId <= 0 / null
        assertThatThrownBy(() -> new DailyNotificationSummaryResponse(nullCultivationId, date, 3L, validEvents))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DailyNotificationSummaryResponse(zeroCultivationId, date, 3L, validEvents))
                .isInstanceOf(IllegalArgumentException.class);

        // 2. date null
        assertThatThrownBy(() -> new DailyNotificationSummaryResponse(1L, nullDate, 3L, validEvents))
                .isInstanceOf(IllegalArgumentException.class);

        // 3. totalCount null / 음수
        assertThatThrownBy(() -> new DailyNotificationSummaryResponse(1L, date, nullTotalCount, validEvents))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DailyNotificationSummaryResponse(1L, date, negativeTotalCount, validEvents))
                .isInstanceOf(IllegalArgumentException.class);

        // 4. eventCounts null
        assertThatThrownBy(() -> new DailyNotificationSummaryResponse(1L, date, 3L, nullEvents))
                .isInstanceOf(IllegalArgumentException.class);

        // 5. null 요소 포함
        List<DailyNotificationEventCountResponse> nullList = Collections.singletonList(null);
        assertThatThrownBy(() -> new DailyNotificationSummaryResponse(1L, date, 0L, nullList))
                .isInstanceOf(IllegalArgumentException.class);

        // 6. 중복 eventTypeCode
        DailyNotificationEventCountResponse dup1 = new DailyNotificationEventCountResponse("EVENT", "설명", 1L);
        DailyNotificationEventCountResponse dup2 = new DailyNotificationEventCountResponse("EVENT", "설명", 2L);
        List<DailyNotificationEventCountResponse> dupList = List.of(dup1, dup2);
        assertThatThrownBy(() -> new DailyNotificationSummaryResponse(1L, date, 3L, dupList))
                .isInstanceOf(IllegalArgumentException.class);

        // 7. count 합계 불일치 (실제 합 3인데 totalCount 10)
        assertThatThrownBy(() -> new DailyNotificationSummaryResponse(1L, date, mismatchedTotalCount, validEvents))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
