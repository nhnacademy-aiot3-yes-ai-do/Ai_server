package site.yesaido.ai_server.rabbitmq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import site.yesaido.ai_server.rabbitmq.event.AiEvent;
import static site.yesaido.ai_server.rabbitmq.RabbitMqConstants.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiNotificationProducer {
    private final RabbitTemplate rabbitTemplate;
    /*
    UUID eventId,
    long userId,
    long cultivationId,
    String cultivationName,
    String feedbackUrl,
    String feedbackContent,
    OffsetDateTime occurredAt
    */
    // 일일 피드백 완료 알림 발송(yes-nhn.notification.daily.queue)
    public void sendDailyFeedback(long userId, long cultivationId, String cultivationName,
                                  String feedbackUrl, String feedbackContent) {
        AiEvent.DailyFeedbackGeneratedEvent event = new AiEvent.DailyFeedbackGeneratedEvent(
                UUID.randomUUID(),
                userId,
                cultivationId,
                cultivationName,
                feedbackUrl,
                feedbackContent,
                OffsetDateTime.now(ZoneId.of("Asia/Seoul"))
        );
        try{
            rabbitTemplate.convertAndSend(NOTIFICATION_EXCHANGE, DAILY_FEEDBACK_QUEUE, event);
            log.info("일일 피드백 알림 발행 성공: cultivationId={}, userId={}", cultivationId, userId);
        } catch (AmqpException e) {
            log.error("일일 피드백 알림 발행 실패: cultivationId={}, userId={}, error={}", cultivationId, userId, e.getMessage());
        }
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
