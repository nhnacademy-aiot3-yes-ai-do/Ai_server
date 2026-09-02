package site.yesaido.ai_server.dto.client.notification;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Notification Server가 반환하는 일일 알림 통계의 최상위 응답입니다.
 *
 * <p>각 경작지의 통계 날짜와 최상위 기준 날짜가 일치해야 하며,
 * 모든 날짜는 {@code Asia/Seoul} 시간대 기준으로 해석합니다.</p>
 *
 * <p>이 DTO는 원래 요청한 경작지 ID 목록을 알지 못하므로 응답이 요청 대상을
 * 모두 포함하는지는 검증하지 않습니다. 요청과 응답의 ID 비교는 이후
 * Notification 통계 조회 서비스에서 수행합니다.</p>
 *
 * @param date 일일 알림 통계의 최상위 기준 날짜
 * @param zoneId 날짜 경계를 계산할 때 사용한 시간대
 * @param summaries 경작지별 하루치 알림 통계 목록
 */
public record DailyNotificationSummariesResponse(
        LocalDate date,
        String zoneId,
        List<DailyNotificationSummaryResponse> summaries
) {

    public DailyNotificationSummariesResponse {
        if (date == null) {
            throw new IllegalArgumentException("date는 필수이며 null일 수 없습니다.");
        }

        if (zoneId == null || zoneId.isBlank()) {
            throw new IllegalArgumentException("zoneId는 null이거나 blank일 수 없습니다.");
        }

        if (!"Asia/Seoul".equals(zoneId)) {
            throw new IllegalArgumentException("zoneId는 Asia/Seoul이어야 합니다.");
        }

        if (summaries == null || summaries.isEmpty()) {
            throw new IllegalArgumentException("summaries는 필수이며 비어 있을 수 없습니다.");
        }

        Set<Long> cultivationIds = new HashSet<>();

        for (DailyNotificationSummaryResponse summary : summaries) {
            if (summary == null) {
                throw new IllegalArgumentException("summaries에는 null 요소가 포함될 수 없습니다.");
            }

            if (!cultivationIds.add(summary.cultivationId())) {
                throw new IllegalArgumentException("summaries에는 동일한 cultivationId가 중복될 수 없습니다.");
            }

            if (!date.equals(summary.date())) {
                throw new IllegalArgumentException("summary의 date가 최상위 date와 일치하지 않습니다.");
            }
        }

        summaries = List.copyOf(summaries);
    }
}
