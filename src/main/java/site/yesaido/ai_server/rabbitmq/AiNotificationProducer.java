package site.yesaido.ai_server.rabbitmq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.AmqpTimeoutException;
import org.springframework.amqp.core.AmqpMessageReturnedException;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import site.yesaido.ai_server.config.DailyFeedbackOutboxProperties;
import site.yesaido.ai_server.rabbitmq.event.AiEvent;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static site.yesaido.ai_server.rabbitmq.RabbitMqConstants.*;

/**
 * 호출자가 완성한 AI 이벤트를 RabbitMQ로 전달하는 전송 어댑터입니다.
 *
 * <p>이 Producer는 일일 피드백 이벤트의 생성자가 아닙니다.
 * 이벤트 ID와 발생 시각을 포함한 이벤트 내용은 호출자가 미리
 * 확정하여 전달해야 합니다.</p>
 *
 * <p>단순히 {@link RabbitTemplate#convertAndSend(String, String, Object)}
 * 호출이 반환된 것만으로는 Broker 수신과 Queue 라우팅 성공이
 * 보장되지 않습니다. 일일 피드백 이벤트는 Broker Confirm이 ACK이고
 * {@link CorrelationData#getReturned()}가 null인 경우에만 성공으로
 * 처리합니다.</p>
 *
 * <p>확인 시간 초과, NACK 또는 반환된 메시지는 Outbox Relay가
 * 재시도할 수 있도록 예외로 전파합니다. 피드백 본문, URL,
 * 반환된 메시지 본문과 Confirm 사유는 로그에 기록하지 않습니다.</p>
 *
 * <p>전달 보장은 Outbox 재시도와 Notification Server의
 * {@code eventId} 멱등 처리를 결합한 {@code at-least-once}
 * 방식입니다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiNotificationProducer {

    private static final String CONFIRM_INTERRUPTED_MESSAGE = "일일 피드백 이벤트 발행 확인 대기가 중단되었습니다.";

    private static final String CONFIRM_TIMEOUT_MESSAGE = "일일 피드백 이벤트 발행 확인 시간이 초과되었습니다.";

    private static final String CONFIRM_FAILED_MESSAGE = "일일 피드백 이벤트 발행 확인에 실패했습니다.";

    private static final String MESSAGE_RETURNED_MESSAGE = "일일 피드백 이벤트가 대상 Queue로 라우팅되지 않았습니다.";

    private static final String BROKER_NOT_ACKNOWLEDGED_MESSAGE = "RabbitMQ Broker가 일일 피드백 이벤트 발행을 확인하지 않았습니다.";

    private final RabbitTemplate rabbitTemplate;

    private final DailyFeedbackOutboxProperties dailyFeedbackOutboxProperties;

    /**
     * 완성된 일일 피드백 이벤트를 발행하고 Broker Confirm과
     * Queue 라우팅 결과를 확인합니다.
     *
     * <p>발행 시도마다 고유한 {@code publicationAttemptId}로
     * {@link CorrelationData}를 생성합니다. 동일 이벤트를 재시도할 때도
     * 이전 timeout 발행의 확인 결과와 충돌하지 않도록 이벤트 ID만
     * Correlation ID로 사용하지 않습니다.</p>
     *
     * <p>ACK과 미반환 조건을 모두 만족해야 정상 반환합니다.
     * timeout, NACK 또는 unroutable return은 Relay가 재시도할 수
     * 있도록 예외로 전파합니다.</p>
     *
     * <p>이 메서드는 DB 트랜잭션을 열거나 Outbox 상태를 변경하지
     * 않습니다.</p>
     *
     * @param event Outbox Payload에서 복원한 일일 피드백 완료 이벤트
     * @param publicationAttemptId 발행 시도별 고유 식별자
     * @throws NullPointerException event가 null인 경우
     * @throws IllegalArgumentException publicationAttemptId가 null 또는 공백인 경우
     * @throws AmqpException RabbitMQ 발행 또는 확인에 실패한 경우
     */
    public void sendDailyFeedbackConfirmed(AiEvent.DailyFeedbackGeneratedEvent event, String publicationAttemptId) {
        Objects.requireNonNull(event, "event는 null일 수 없습니다.");

        if (publicationAttemptId == null || publicationAttemptId.isBlank()) {
            throw new IllegalArgumentException("publicationAttemptId는 null이거나 공백일 수 없습니다.");
        }

        String validatedPublicationAttemptId = publicationAttemptId.strip();

        CorrelationData correlationData = new CorrelationData(validatedPublicationAttemptId);

        rabbitTemplate.convertAndSend(NOTIFICATION_EXCHANGE, DAILY_FEEDBACK_QUEUE, event, correlationData);

        Duration publisherConfirmTimeout = dailyFeedbackOutboxProperties.getPublisherConfirmTimeout();

        CorrelationData.Confirm confirm;

        try {
            confirm = correlationData
                    .getFuture()
                    .get(publisherConfirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new AmqpException(CONFIRM_INTERRUPTED_MESSAGE, exception);
        } catch (TimeoutException exception) {
            throw new AmqpTimeoutException(CONFIRM_TIMEOUT_MESSAGE, exception);
        } catch (ExecutionException exception) {
            throw new AmqpException(CONFIRM_FAILED_MESSAGE, exception.getCause());
        }

        ReturnedMessage returnedMessage = correlationData.getReturned();

        if (returnedMessage != null) {
            throw new AmqpMessageReturnedException(MESSAGE_RETURNED_MESSAGE, returnedMessage);
        }

        if (confirm == null || !confirm.ack()) {
            throw new AmqpException(BROKER_NOT_ACKNOWLEDGED_MESSAGE);
        }

        log.info("일일 피드백 알림 발행 성공: eventId={}, cultivationId={}, userId={}, publicationAttemptId={}",
                event.eventId(), event.cultivationId(), event.userId(), validatedPublicationAttemptId);
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
