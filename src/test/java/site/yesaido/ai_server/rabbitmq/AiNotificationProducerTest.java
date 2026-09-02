package site.yesaido.ai_server.rabbitmq;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.AmqpTimeoutException;
import org.springframework.amqp.core.AmqpMessageReturnedException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import site.yesaido.ai_server.config.DailyFeedbackOutboxProperties;
import site.yesaido.ai_server.rabbitmq.event.AiEvent.CultivationCompletedEvent;
import site.yesaido.ai_server.rabbitmq.event.AiEvent.DailyFeedbackGeneratedEvent;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static site.yesaido.ai_server.rabbitmq.RabbitMqConstants.DAILY_FEEDBACK_QUEUE;
import static site.yesaido.ai_server.rabbitmq.RabbitMqConstants.NOTIFICATION_CULTIVATION_COMPLETE_QUEUE;
import static site.yesaido.ai_server.rabbitmq.RabbitMqConstants.NOTIFICATION_EXCHANGE;

@ExtendWith(MockitoExtension.class)
class AiNotificationProducerTest {

    private static final String PUBLICATION_ATTEMPT_ID =
            "100:123e4567-e89b-12d3-a456-426614174000:1";

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private DailyFeedbackOutboxProperties
            dailyFeedbackOutboxProperties;

    @InjectMocks
    private AiNotificationProducer producer;

    @Test
    @DisplayName("Broker ACK이고 반환 메시지가 없으면 일일 피드백 발행에 성공한다")
    void sendDailyFeedbackSuccessfullyWhenBrokerAcknowledges() {
        // 준비
        DailyFeedbackGeneratedEvent event = dailyFeedbackEvent();

        when(
                dailyFeedbackOutboxProperties
                        .getPublisherConfirmTimeout()
        ).thenReturn(Duration.ofSeconds(1));

        completeConfirmWhenPublished(
                event,
                new CorrelationData.Confirm(true, null),
                null
        );

        ArgumentCaptor<CorrelationData> correlationDataCaptor =
                ArgumentCaptor.forClass(CorrelationData.class);

        // 실행 및 검증
        assertDoesNotThrow(
                () -> producer.sendDailyFeedbackConfirmed(
                        event,
                        PUBLICATION_ATTEMPT_ID
                )
        );

        verify(rabbitTemplate).convertAndSend(
                eq(NOTIFICATION_EXCHANGE),
                eq(DAILY_FEEDBACK_QUEUE),
                same(event),
                correlationDataCaptor.capture()
        );

        assertThat(correlationDataCaptor.getValue().getId())
                .isEqualTo(PUBLICATION_ATTEMPT_ID);

        verify(dailyFeedbackOutboxProperties)
                .getPublisherConfirmTimeout();
    }

    @Test
    @DisplayName("일일 피드백 이벤트가 null이면 발행 전에 거부한다")
    void rejectNullDailyFeedbackEvent() {
        // 준비

        // 실행
        NullPointerException exception = catchThrowableOfType(
                NullPointerException.class,
                () -> producer.sendDailyFeedbackConfirmed(
                        null,
                        PUBLICATION_ATTEMPT_ID
                )
        );

        // 검증
        assertThat(exception)
                .hasMessage("event는 null일 수 없습니다.");

        verifyNoInteractions(
                rabbitTemplate,
                dailyFeedbackOutboxProperties
        );
    }

    @Test
    @DisplayName("발행 시도 ID가 공백이면 발행 전에 거부한다")
    void rejectBlankPublicationAttemptId() {
        // 준비
        DailyFeedbackGeneratedEvent event = dailyFeedbackEvent();

        // 실행
        IllegalArgumentException exception = catchThrowableOfType(
                IllegalArgumentException.class,
                () -> producer.sendDailyFeedbackConfirmed(
                        event,
                        "   "
                )
        );

        // 검증
        assertThat(exception)
                .hasMessage(
                        "publicationAttemptId는 null이거나 공백일 수 없습니다."
                );

        verifyNoInteractions(
                rabbitTemplate,
                dailyFeedbackOutboxProperties
        );
    }

    @Test
    @DisplayName("RabbitTemplate의 즉시 발행 실패를 동일한 예외로 전파한다")
    void propagateImmediatePublishingFailure() {
        // 준비
        DailyFeedbackGeneratedEvent event = dailyFeedbackEvent();
        AmqpException publishingFailure =
                new AmqpException("RabbitMQ connection error");

        doThrow(publishingFailure)
                .when(rabbitTemplate)
                .convertAndSend(
                        eq(NOTIFICATION_EXCHANGE),
                        eq(DAILY_FEEDBACK_QUEUE),
                        same(event),
                        any(CorrelationData.class)
                );

        // 실행
        AmqpException propagatedException = catchThrowableOfType(
                AmqpException.class,
                () -> producer.sendDailyFeedbackConfirmed(
                        event,
                        PUBLICATION_ATTEMPT_ID
                )
        );

        // 검증
        assertThat(propagatedException)
                .isSameAs(publishingFailure);

        verify(rabbitTemplate).convertAndSend(
                eq(NOTIFICATION_EXCHANGE),
                eq(DAILY_FEEDBACK_QUEUE),
                same(event),
                any(CorrelationData.class)
        );

        verifyNoInteractions(dailyFeedbackOutboxProperties);
    }

    @Test
    @DisplayName("Broker가 NACK을 반환하면 발행 실패로 처리한다")
    void rejectBrokerNegativeAcknowledgement() {
        // 준비
        DailyFeedbackGeneratedEvent event = dailyFeedbackEvent();
        String sensitiveReason = "sensitive broker reason";

        when(
                dailyFeedbackOutboxProperties
                        .getPublisherConfirmTimeout()
        ).thenReturn(Duration.ofSeconds(1));

        completeConfirmWhenPublished(
                event,
                new CorrelationData.Confirm(
                        false,
                        sensitiveReason
                ),
                null
        );

        // 실행
        AmqpException exception = catchThrowableOfType(
                AmqpException.class,
                () -> producer.sendDailyFeedbackConfirmed(
                        event,
                        PUBLICATION_ATTEMPT_ID
                )
        );

        // 검증
        assertThat(exception)
                .hasMessage(
                        "RabbitMQ Broker가 일일 피드백 이벤트 발행을 확인하지 않았습니다."
                );

        assertThat(exception.getMessage())
                .doesNotContain(sensitiveReason);

        verify(rabbitTemplate).convertAndSend(
                eq(NOTIFICATION_EXCHANGE),
                eq(DAILY_FEEDBACK_QUEUE),
                same(event),
                any(CorrelationData.class)
        );

        verify(dailyFeedbackOutboxProperties)
                .getPublisherConfirmTimeout();
    }

    @Test
    @DisplayName("메시지가 Queue로 라우팅되지 않으면 반환 예외를 발생시킨다")
    void rejectReturnedMessage() {
        // 준비
        DailyFeedbackGeneratedEvent event = dailyFeedbackEvent();

        ReturnedMessage returnedMessage = new ReturnedMessage(
                new Message(new byte[0]),
                312,
                "NO_ROUTE",
                NOTIFICATION_EXCHANGE,
                DAILY_FEEDBACK_QUEUE
        );

        when(
                dailyFeedbackOutboxProperties
                        .getPublisherConfirmTimeout()
        ).thenReturn(Duration.ofSeconds(1));

        completeConfirmWhenPublished(
                event,
                new CorrelationData.Confirm(true, null),
                returnedMessage
        );

        // 실행
        AmqpMessageReturnedException exception =
                catchThrowableOfType(
                        AmqpMessageReturnedException.class,
                        () -> producer.sendDailyFeedbackConfirmed(
                                event,
                                PUBLICATION_ATTEMPT_ID
                        )
                );

        // 검증
        assertThat(exception)
                .hasMessage(
                        "일일 피드백 이벤트가 대상 Queue로 라우팅되지 않았습니다."
                );

        verify(rabbitTemplate).convertAndSend(
                eq(NOTIFICATION_EXCHANGE),
                eq(DAILY_FEEDBACK_QUEUE),
                same(event),
                any(CorrelationData.class)
        );

        verify(dailyFeedbackOutboxProperties)
                .getPublisherConfirmTimeout();
    }

    @Test
    @DisplayName("Broker Confirm이 제한 시간 안에 도착하지 않으면 timeout으로 처리한다")
    void failWhenPublisherConfirmTimesOut() {
        // 준비
        DailyFeedbackGeneratedEvent event = dailyFeedbackEvent();

        when(
                dailyFeedbackOutboxProperties
                        .getPublisherConfirmTimeout()
        ).thenReturn(Duration.ofMillis(1));

        // 실행
        AmqpTimeoutException exception = catchThrowableOfType(
                AmqpTimeoutException.class,
                () -> producer.sendDailyFeedbackConfirmed(
                        event,
                        PUBLICATION_ATTEMPT_ID
                )
        );

        // 검증
        assertThat(exception)
                .hasMessage(
                        "일일 피드백 이벤트 발행 확인 시간이 초과되었습니다."
                );

        verify(rabbitTemplate).convertAndSend(
                eq(NOTIFICATION_EXCHANGE),
                eq(DAILY_FEEDBACK_QUEUE),
                same(event),
                any(CorrelationData.class)
        );

        verify(dailyFeedbackOutboxProperties)
                .getPublisherConfirmTimeout();
    }

    @Test
    @DisplayName("Confirm Future가 비동기로 실패하면 원인을 보존한 발행 예외를 발생시킨다")
    void failWhenPublisherConfirmFutureCompletesExceptionally() {
        // 준비
        DailyFeedbackGeneratedEvent event = dailyFeedbackEvent();
        RuntimeException confirmFailure =
                new RuntimeException("asynchronous confirm failure");

        when(
                dailyFeedbackOutboxProperties
                        .getPublisherConfirmTimeout()
        ).thenReturn(Duration.ofSeconds(1));

        failConfirmWhenPublished(
                event,
                confirmFailure
        );

        // 실행
        AmqpException exception = catchThrowableOfType(
                AmqpException.class,
                () -> producer.sendDailyFeedbackConfirmed(
                        event,
                        PUBLICATION_ATTEMPT_ID
                )
        );

        // 검증
        assertThat(exception)
                .hasMessage(
                        "일일 피드백 이벤트 발행 확인에 실패했습니다."
                );

        assertThat(exception.getCause())
                .isSameAs(confirmFailure);

        verify(rabbitTemplate).convertAndSend(
                eq(NOTIFICATION_EXCHANGE),
                eq(DAILY_FEEDBACK_QUEUE),
                same(event),
                any(CorrelationData.class)
        );

        verify(dailyFeedbackOutboxProperties)
                .getPublisherConfirmTimeout();
    }

    @Test
    @DisplayName("Confirm 대기 중 interrupt가 발생하면 interrupt 상태를 복구한다")
    void restoreInterruptStatusWhenConfirmWaitIsInterrupted() {
        // 준비
        DailyFeedbackGeneratedEvent event = dailyFeedbackEvent();

        when(
                dailyFeedbackOutboxProperties
                        .getPublisherConfirmTimeout()
        ).thenReturn(Duration.ofSeconds(1));

        try {
            Thread.currentThread().interrupt();

            // 실행
            AmqpException exception = catchThrowableOfType(
                    AmqpException.class,
                    () -> producer.sendDailyFeedbackConfirmed(
                            event,
                            PUBLICATION_ATTEMPT_ID
                    )
            );

            // 검증
            assertThat(exception)
                    .hasMessage(
                            "일일 피드백 이벤트 발행 확인 대기가 중단되었습니다."
                    );

            assertThat(Thread.currentThread().isInterrupted())
                    .isTrue();

            verify(rabbitTemplate).convertAndSend(
                    eq(NOTIFICATION_EXCHANGE),
                    eq(DAILY_FEEDBACK_QUEUE),
                    same(event),
                    any(CorrelationData.class)
            );

            verify(dailyFeedbackOutboxProperties)
                    .getPublisherConfirmTimeout();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    @DisplayName("재배 완료 알림 이벤트 발행에 성공한다")
    void sendCultivationCompletedNotificationSuccessfully() {
        // 준비
        long userId = 1L;
        long cultivationId = 10L;
        String cultivationName = "양송이";
        BigDecimal growthRate = new BigDecimal("85.5");
        String cultivationUrl = "/cultivations/10";

        // 실행
        producer.sendCultivationCompleted(
                userId,
                cultivationId,
                cultivationName,
                growthRate,
                cultivationUrl
        );

        // 검증
        verify(rabbitTemplate).convertAndSend(
                eq(NOTIFICATION_EXCHANGE),
                eq(NOTIFICATION_CULTIVATION_COMPLETE_QUEUE),
                any(CultivationCompletedEvent.class)
        );

        verifyNoInteractions(dailyFeedbackOutboxProperties);
    }

    @Test
    @DisplayName("재배 완료 알림 이벤트 발행 실패를 내부에서 처리하고 정상 복귀한다")
    void handleCultivationCompletedNotificationFailure() {
        // 준비
        doThrow(new RuntimeException("RabbitMQ error"))
                .when(rabbitTemplate)
                .convertAndSend(
                        eq(NOTIFICATION_EXCHANGE),
                        eq(NOTIFICATION_CULTIVATION_COMPLETE_QUEUE),
                        any(CultivationCompletedEvent.class)
                );

        // 실행 및 검증
        assertDoesNotThrow(
                () -> producer.sendCultivationCompleted(
                        1L,
                        10L,
                        "양송이",
                        new BigDecimal("85.5"),
                        "/cultivations/10"
                )
        );

        verify(rabbitTemplate).convertAndSend(
                eq(NOTIFICATION_EXCHANGE),
                eq(NOTIFICATION_CULTIVATION_COMPLETE_QUEUE),
                any(CultivationCompletedEvent.class)
        );

        verifyNoInteractions(dailyFeedbackOutboxProperties);
    }

    private void completeConfirmWhenPublished(
            DailyFeedbackGeneratedEvent event,
            CorrelationData.Confirm confirm,
            ReturnedMessage returnedMessage
    ) {
        doAnswer(invocation -> {
            CorrelationData correlationData =
                    invocation.getArgument(
                            3,
                            CorrelationData.class
                    );

            if (returnedMessage != null) {
                correlationData.setReturned(returnedMessage);
            }

            correlationData.getFuture().complete(confirm);

            return null;
        })
                .when(rabbitTemplate)
                .convertAndSend(
                        eq(NOTIFICATION_EXCHANGE),
                        eq(DAILY_FEEDBACK_QUEUE),
                        same(event),
                        any(CorrelationData.class)
                );
    }

    private void failConfirmWhenPublished(
            DailyFeedbackGeneratedEvent event,
            Throwable failure
    ) {
        doAnswer(invocation -> {
            CorrelationData correlationData =
                    invocation.getArgument(
                            3,
                            CorrelationData.class
                    );

            correlationData
                    .getFuture()
                    .completeExceptionally(failure);

            return null;
        })
                .when(rabbitTemplate)
                .convertAndSend(
                        eq(NOTIFICATION_EXCHANGE),
                        eq(DAILY_FEEDBACK_QUEUE),
                        same(event),
                        any(CorrelationData.class)
                );
    }

    private DailyFeedbackGeneratedEvent dailyFeedbackEvent() {
        return new DailyFeedbackGeneratedEvent(
                UUID.fromString(
                        "123e4567-e89b-12d3-a456-426614174000"
                ),
                1L,
                10L,
                "테스트 재배지",
                "/cultivations/10/daily-feedbacks/2026-09-01",
                "# 오늘의 재배 환경 요약\n환경이 안정적으로 유지되었습니다.",
                OffsetDateTime.parse(
                        "2026-09-02T00:05:00+09:00"
                )
        );
    }
}
