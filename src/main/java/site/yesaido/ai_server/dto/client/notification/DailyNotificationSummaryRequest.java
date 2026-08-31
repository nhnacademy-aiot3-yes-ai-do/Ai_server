package site.yesaido.ai_server.dto.client.notification;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Notification Server의 일일 요약 알림 내부 API에 전달하는 요청입니다.
 *
 * <p>날짜 경계는 Notification Server가 Asia/Seoul 기준으로 계산하며,
 * AI Server는 피드백이 생성된 경작지 ID만 전달합니다.</p>
 *
 * @param date 일일 요약 알림의 기준 날짜
 * @param cultivationIds 해당 날짜의 피드백 알림 대상 경작지 ID 목록
 */
public record DailyNotificationSummaryRequest(
        LocalDate date,
        List<Long> cultivationIds
) {

    public DailyNotificationSummaryRequest {
        if (date == null) {
            throw new IllegalArgumentException("date는 필수이며 null일 수 없습니다.");
        }

        if (cultivationIds == null || cultivationIds.isEmpty()) {
            throw new IllegalArgumentException("cultivationIds는 필수이며 비어 있을 수 없습니다.");
        }

        LinkedHashSet<Long> uniqueCultivationIds = new LinkedHashSet<>();

        for (Long cultivationId : cultivationIds) {
            if (cultivationId == null || cultivationId <= 0) {
                throw new IllegalArgumentException("cultivationIds에는 null 또는 0 이하의 ID가 포함될 수 없습니다.");
            }

            uniqueCultivationIds.add(cultivationId);
        }

        cultivationIds = List.copyOf(uniqueCultivationIds);
    }
}
