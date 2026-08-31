package site.yesaido.ai_server.dto.daily_feedback;

import site.yesaido.ai_server.dto.client.notification.DailyNotificationEventCountResponse;
import site.yesaido.ai_server.dto.client.notification.DailyNotificationSummaryResponse;

import java.time.LocalDate;

/**
 * Notification Server의 정상 일일 통계 응답에서 추출한 일일 피드백용 지표입니다.
 *
 * <p>{@code totalNotificationCount}는 모든 Notification 원본 이벤트의 합계이므로
 * 특정 이벤트 유형의 발생 횟수로 사용하면 안 됩니다.</p>
 *
 * <p>{@code thresholdBreachAlertCount}는 원시 센서 측정값의 이탈 횟수가 아니라,
 * RuleEngine의 5분 cooldown을 거쳐 Notification에 실제 저장된
 * 환경 임계값 이탈 알림 횟수입니다.</p>
 *
 * <p>{@code actuatorControlSucceededCount}는 현재 Notification에
 * {@code ACTUATOR_CONTROL_SUCCEEDED} 코드로 저장된 이벤트 수입니다.
 * RuleEngine의 거절 이벤트 분류 문제는 별도 계약 수정 대상으로 두며,
 * 이 모델에서는 값을 임의로 보정하지 않습니다.</p>
 *
 * @param cultivationId 통계 대상 경작지 ID
 * @param date Notification 통계의 기준 날짜
 * @param totalNotificationCount 모든 이벤트 유형을 합한 Notification 원본 이벤트 수
 * @param thresholdBreachAlertCount cooldown 적용 후 저장된 환경 임계값 이탈 알림 수
 * @param actuatorControlSucceededCount 액추에이터 제어 성공 이벤트 수
 * @param actuatorControlFailedCount 액추에이터 제어 실패 이벤트 수
 */
public record DailyNotificationMetrics(
        Long cultivationId,
        LocalDate date,
        long totalNotificationCount,
        long thresholdBreachAlertCount,
        long actuatorControlSucceededCount,
        long actuatorControlFailedCount
) {

    private static final String ENVIRONMENT_THRESHOLD_BREACHED = "ENVIRONMENT_THRESHOLD_BREACHED";
    private static final String ACTUATOR_CONTROL_SUCCEEDED = "ACTUATOR_CONTROL_SUCCEEDED";
    private static final String ACTUATOR_CONTROL_FAILED = "ACTUATOR_CONTROL_FAILED";

    public DailyNotificationMetrics {
        if (cultivationId == null || cultivationId <= 0) {
            throw new IllegalArgumentException("cultivationId는 필수이며 0보다 커야 합니다.");
        }

        if (date == null) {
            throw new IllegalArgumentException("date는 필수이며 null일 수 없습니다.");
        }

        if (totalNotificationCount < 0 || thresholdBreachAlertCount < 0
                || actuatorControlSucceededCount < 0 || actuatorControlFailedCount < 0) {
            throw new IllegalArgumentException("Notification count 지표는 음수일 수 없습니다.");
        }

        long detailedMetricCount;

        try {
            detailedMetricCount = Math.addExact(thresholdBreachAlertCount, actuatorControlSucceededCount);
            detailedMetricCount = Math.addExact(detailedMetricCount, actuatorControlFailedCount);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("세부 Notification 지표 합계가 long 범위를 초과했습니다.");
        }

        if (detailedMetricCount > totalNotificationCount) {
            throw new IllegalArgumentException("세부 Notification 지표 합계는 전체 이벤트 수보다 클 수 없습니다.");
        }
    }

    /**
     * 성공적으로 수신하고 계약 검증을 통과한 Notification 일일 통계를
     * 일일 피드백용 지표로 변환합니다.
     *
     * <p>목록에 없는 필수 이벤트 코드는 {@code 0}으로 처리하고,
     * 일일 피드백에서 사용하지 않는 다른 이벤트 코드는 무시합니다.
     * 통신 실패를 빈 지표로 변환하는 용도로는 사용하지 않습니다.</p>
     *
     * @param response 정상적으로 수신한 경작지별 일일 Notification 통계
     * @return 일일 피드백에서 사용할 Notification 지표
     */
    public static DailyNotificationMetrics fromSuccessfulResponse(DailyNotificationSummaryResponse response) {
        if (response == null) {
            throw new IllegalArgumentException("response는 필수이며 null일 수 없습니다.");
        }

        long thresholdBreachAlertCount = 0L;
        long actuatorControlSucceededCount = 0L;
        long actuatorControlFailedCount = 0L;

        for (DailyNotificationEventCountResponse eventCount : response.eventCounts()) {
            switch (eventCount.eventTypeCode()) {
                case ENVIRONMENT_THRESHOLD_BREACHED -> thresholdBreachAlertCount = eventCount.count();
                case ACTUATOR_CONTROL_SUCCEEDED -> actuatorControlSucceededCount = eventCount.count();
                case ACTUATOR_CONTROL_FAILED -> actuatorControlFailedCount = eventCount.count();
                default -> {
                    // 일일 피드백에서 사용하지 않는 이벤트 유형은 무시한다.
                }
            }
        }

        return new DailyNotificationMetrics(
                response.cultivationId(),
                response.date(),
                response.totalCount(),
                thresholdBreachAlertCount,
                actuatorControlSucceededCount,
                actuatorControlFailedCount
        );
    }
}
