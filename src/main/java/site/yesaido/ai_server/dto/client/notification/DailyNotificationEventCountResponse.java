package site.yesaido.ai_server.dto.client.notification;

/**
 * Notification Server가 반환하는 일일 통계의 이벤트 유형별 발생 횟수입니다.
 *
 * <p>이 DTO는 일일 알림 통계 응답에 포함되는 한 개의 이벤트 집계 항목이며,
 * 이벤트 코드 선택과 해석은 이후 일일 피드백 조립 계층에서 수행합니다.</p>
 *
 * @param eventTypeCode 서비스 간 계약에서 사용하는 이벤트 유형 코드
 * @param eventTypeName 사용자에게 표시할 이벤트 유형 이름
 * @param count 해당 날짜에 발생한 이벤트 횟수
 */
public record DailyNotificationEventCountResponse(
        String eventTypeCode,
        String eventTypeName,
        Long count
) {

    public DailyNotificationEventCountResponse {
        if (eventTypeCode == null || eventTypeCode.isBlank()) {
            throw new IllegalArgumentException("eventTypeCode는 null이거나 blank일 수 없습니다.");
        }

        if (eventTypeName == null || eventTypeName.isBlank()) {
            throw new IllegalArgumentException("eventTypeName은 null이거나 blank일 수 없습니다.");
        }

        if (count == null) {
            throw new IllegalArgumentException("count는 필수이며 null일 수 없습니다.");
        }

        if (count < 0) {
            throw new IllegalArgumentException("count는 음수일 수 없습니다.");
        }
    }
}
