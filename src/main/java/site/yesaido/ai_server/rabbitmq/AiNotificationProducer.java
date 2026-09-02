package site.yesaido.ai_server.rabbitmq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import site.yesaido.ai_server.rabbitmq.event.AiEvent;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

import static site.yesaido.ai_server.rabbitmq.RabbitMqConstants.*;

/**
 * 호출자가 완성한 AI 이벤트를 RabbitMQ로 전달하는 전송 어댑터입니다.
 *
 * <p>이 Producer는 일일 피드백 이벤트의 생성자가 아닙니다.
 * 이벤트 ID와 발생 시각을 포함한 이벤트 내용은 호출자가 미리
 * 확정하여 전달해야 합니다.</p>
 *
 * <p>일일 피드백 이벤트 발행 실패는 이후 재시도 계층이 인식할 수
 * 있도록 상위 호출자에게 그대로 전파합니다. 피드백 본문과 URL,
 * 전체 이벤트 객체는 로그에 기록하지 않습니다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiNotificationProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 완성된 일일 피드백 생성 이벤트를 RabbitMQ로 전송합니다.
     *
     * <p>{@code eventId}와 {@code occurredAt}은 호출자가 확정한 값을
     * 그대로 사용합니다. 전송 중 발생한 예외는 이 메서드에서
     * 처리하거나 숨기지 않고 재시도를 담당할 상위 계층으로
     * 전파합니다.</p>
     *
     * <p>성공 로그에는 이벤트 ID, 경작지 ID와 사용자 ID만 기록하며,
     * 피드백 본문과 URL은 기록하지 않습니다.</p>
     *
     * @param event 호출자가 생성하고 확정한 일일 피드백 이벤트
     * @throws NullPointerException event가 null인 경우
     * @throws RuntimeException RabbitMQ 전송에 실패한 경우
     */
    public void sendDailyFeedback(AiEvent.DailyFeedbackGeneratedEvent event) {
        Objects.requireNonNull(event, "event는 null일 수 없습니다.");

        rabbitTemplate.convertAndSend(NOTIFICATION_EXCHANGE, DAILY_FEEDBACK_QUEUE, event);

        log.info("일일 피드백 알림 발행 성공: eventId={}, cultivationId={}, userId={}", event.eventId(), event.cultivationId(), event.userId());
    }

    /*
    UUID eventId,
    long userId,
    long cultivationId,
    String cultivationName,
    BigDecimal growthRate,
    String cultivationUrl,
    OffsetDateTime occurredAt
     */
    // 재배 완료, 수확기 전환 알림 발송(yes-nhn.notification.cultivation-complete.queue)
    public void sendCultivationCompleted(long userId, long cultivationId, String cultivationName,
                                         BigDecimal growthRate, String cultivationUrl) {
        AiEvent.CultivationCompletedEvent event = new AiEvent.CultivationCompletedEvent(
                UUID.randomUUID(),
                userId,
                cultivationId,
                cultivationName,
                growthRate,
                cultivationUrl,
                OffsetDateTime.now(ZoneId.of("Asia/Seoul"))
        );

        try {
            rabbitTemplate.convertAndSend(NOTIFICATION_EXCHANGE, NOTIFICATION_CULTIVATION_COMPLETE_QUEUE, event);
            log.info("재배 완료/수확기 전환 알림 이벤트 발행 성공: cultivationId={}, userId={}", cultivationId, userId);
        } catch (Exception e) {
            log.error("재배 완료/수확기 전환 알림 이벤트 발행 실패: cultivationId={}, userId={}, error={}", cultivationId, userId, e.getMessage());
        }
    }
}
