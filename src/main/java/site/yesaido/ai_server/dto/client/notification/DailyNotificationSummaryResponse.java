package site.yesaido.ai_server.dto.client.notification;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Notification Server가 반환하는 특정 경작지의 하루치 알림 통계입니다.
 *
 * <p>{@code totalCount}는 모든 이벤트 유형의 발생 횟수를 합한 전체 알림 수입니다.
 * 환경 임계값 이탈이나 액추에이터 제어처럼 특정 이벤트의 발생 횟수로
 * 직접 사용하면 안 되며, 해당 값은 {@code eventCounts}에서 정확한
 * {@code eventTypeCode}를 기준으로 조회해야 합니다.</p>
 *
 * @param cultivationId 알림 통계를 집계한 경작지 ID
 * @param date 알림 통계의 기준 날짜
 * @param totalCount 모든 이벤트 유형의 발생 횟수를 합한 전체 알림 수
 * @param eventCounts 이벤트 유형별 발생 횟수 목록
 */
public record DailyNotificationSummaryResponse(
        Long cultivationId,
        LocalDate date,
        Long totalCount,
        List<DailyNotificationEventCountResponse> eventCounts
) {

    public DailyNotificationSummaryResponse {
        if (cultivationId == null || cultivationId <= 0) {
            throw new IllegalArgumentException("cultivationId는 필수이며 0보다 커야 합니다.");
        }

        if (date == null) {
            throw new IllegalArgumentException("date는 필수이며 null일 수 없습니다.");
        }

        if (totalCount == null || totalCount < 0) {
            throw new IllegalArgumentException("totalCount는 필수이며 음수일 수 없습니다.");
        }

        if (eventCounts == null) {
            throw new IllegalArgumentException("eventCounts는 필수이며 null일 수 없습니다.");
        }

        Set<String> eventTypeCodes = new HashSet<>();
        long calculatedTotalCount = 0L;

        for (DailyNotificationEventCountResponse eventCount : eventCounts) {
            if (eventCount == null) {
                throw new IllegalArgumentException("eventCounts에는 null 요소가 포함될 수 없습니다.");
            }

            if (!eventTypeCodes.add(eventCount.eventTypeCode())) {
                throw new IllegalArgumentException("eventCounts에는 동일한 eventTypeCode가 중복될 수 없습니다.");
            }

            try {
                calculatedTotalCount = Math.addExact(calculatedTotalCount, eventCount.count());
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("eventCounts의 count 합계가 long 범위를 초과했습니다.");
            }
        }

        if (calculatedTotalCount != totalCount) {
            throw new IllegalArgumentException(
                    "eventCounts의 count 합계가 totalCount와 일치하지 않습니다.");
        }

        eventCounts = List.copyOf(eventCounts);
    }
}
