package site.yesaido.ai_server.rabbitmq;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import site.yesaido.ai_server.rabbitmq.event.AiEvent.CultivationCompletedEvent;
import site.yesaido.ai_server.rabbitmq.event.AiEvent.DailyFeedbackGeneratedEvent;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static site.yesaido.ai_server.rabbitmq.RabbitMqConstants.DAILY_FEEDBACK_QUEUE;
import static site.yesaido.ai_server.rabbitmq.RabbitMqConstants.NOTIFICATION_CULTIVATION_COMPLETE_QUEUE;
import static site.yesaido.ai_server.rabbitmq.RabbitMqConstants.NOTIFICATION_EXCHANGE;

@ExtendWith(MockitoExtension.class)
class AiNotificationProducerTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private AiNotificationProducer producer;

    @Test
    @DisplayName("완성된 일일 피드백 이벤트를 동일한 객체로 발행한다")
    void sendDailyFeedbackSuccessfully() {
        // 준비
        DailyFeedbackGeneratedEvent event = dailyFeedbackEvent();

        // 실행
        producer.sendDailyFeedback(event);

        // 검증
        verify(rabbitTemplate).convertAndSend(eq(NOTIFICATION_EXCHANGE), eq(DAILY_FEEDBACK_QUEUE), same(event));
    }

    @Test
    @DisplayName("일일 피드백 이벤트가 null이면 RabbitMQ 호출 전에 거부한다")
    void rejectNullDailyFeedbackEvent() {
        // 준비

        // 실행
        NullPointerException exception = catchThrowableOfType(NullPointerException.class,
                () -> producer.sendDailyFeedback(null));

        // 검증
        assertThat(exception).hasMessage("event는 null일 수 없습니다.");

        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    @DisplayName("일일 피드백 이벤트 발행 실패를 동일한 예외로 전파한다")
    void propagateDailyFeedbackPublishingFailure() {
        // 준비
        DailyFeedbackGeneratedEvent event = dailyFeedbackEvent();
        AmqpException publishingFailure =
                new AmqpException("RabbitMQ connection error");

        doThrow(publishingFailure)
                .when(rabbitTemplate)
                .convertAndSend(eq(NOTIFICATION_EXCHANGE), eq(DAILY_FEEDBACK_QUEUE), same(event));

        // 실행
        AmqpException propagatedException = catchThrowableOfType(AmqpException.class,
                () -> producer.sendDailyFeedback(event));

        // 검증
        assertThat(propagatedException).isSameAs(publishingFailure);

        verify(rabbitTemplate).convertAndSend(eq(NOTIFICATION_EXCHANGE), eq(DAILY_FEEDBACK_QUEUE), same(event));
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
    }

    private DailyFeedbackGeneratedEvent dailyFeedbackEvent() {
        return new DailyFeedbackGeneratedEvent(
                UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
                1L,
                10L,
                "테스트 재배지",
                "/cultivations/10/daily-feedbacks/2026-09-01",
                "# 오늘의 재배 환경 요약\n환경이 안정적으로 유지되었습니다.",
                OffsetDateTime.parse("2026-09-02T00:05:00+09:00")
        );
    }
}
