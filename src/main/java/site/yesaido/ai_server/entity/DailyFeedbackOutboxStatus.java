package site.yesaido.ai_server.entity;

/**
 * 일일 피드백 완료 이벤트 Outbox의 발행 상태입니다.
 *
 * <p>이 상태는 RabbitMQ 발행 과정만 나타냅니다.
 * Notification 저장이나 Discord 최종 발송 상태를 의미하지 않습니다.</p>
 */
public enum DailyFeedbackOutboxStatus {

    /**
     * 아직 발행되지 않았으며 발행 대상이 될 수 있는 상태입니다.
     */
    PENDING,

    /**
     * 여러 AI Pod 중 하나가 발행 작업을 선점한 상태입니다.
     */
    SENDING,

    /**
     * RabbitMQ 발행이 성공적으로 완료된 상태입니다.
     */
    PUBLISHED,

    /**
     * 정해진 재시도 횟수를 모두 소진한 상태입니다.
     */
    FAILED
}
