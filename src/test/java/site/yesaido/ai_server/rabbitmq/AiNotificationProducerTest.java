package site.yesaido.ai_server.rabbitmq;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import site.yesaido.ai_server.rabbitmq.event.AiEvent.*;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static site.yesaido.ai_server.rabbitmq.RabbitMqConstants.*;
import org.springframework.amqp.AmqpException;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
@ExtendWith(MockitoExtension.class)
class AiNotificationProducerTest {
    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private AiNotificationProducer producer;

    @Test
    @DisplayName("일일 피드백 알림 이벤트 발행 성공 테스트")
    void sendDailyFeedbackNotificationSuccess() {
        producer.sendDailyFeedback(1L, 10L, "양송이", "/feedback/10", "오늘 생육 상태가 좋습니다.");

        verify(rabbitTemplate).convertAndSend(
                eq(NOTIFICATION_EXCHANGE),
                eq(DAILY_FEEDBACK_QUEUE),
                any(DailyFeedbackGeneratedEvent.class)
        );
    }

    @Test
    @DisplayName("재배 완료 알림 이벤트 발행 성공 테스트")
    void sendCultivationCompletedNotificationSuccess() {
        producer.sendCultivationCompleted(1L, 10L, "양송이", new BigDecimal("85.5"), "/cultivations/10");

        verify(rabbitTemplate).convertAndSend(
                eq(NOTIFICATION_EXCHANGE),
                eq(NOTIFICATION_CULTIVATION_COMPLETE_QUEUE),
                any(CultivationCompletedEvent.class)
        );
    }

    @Test
    @DisplayName("일일 피드백 알림 이벤트 발행 중 AmqpException 발생 시 예외를 로깅하고 정상 처리")
    void sendDailyFeedbackNotificationFailure() {
        // [Given] RabbitMQ 발송 시 AmqpException 강제 발생 설정
        doThrow(new AmqpException("RabbitMQ connection error"))
                .when(rabbitTemplate).convertAndSend(eq(NOTIFICATION_EXCHANGE), eq(DAILY_FEEDBACK_QUEUE), any(DailyFeedbackGeneratedEvent.class));

        // [When & Then] 예외를 밖으로 던지지 않고 내부에서 안전하게 로깅 후 정상 복귀하는지 검증
        assertDoesNotThrow(() ->
                producer.sendDailyFeedback(1L, 10L, "양송이", "/feedback/10", "오늘 생육 상태가 좋습니다.")
        );
    }

    @Test
    @DisplayName("재배 완료 알림 이벤트 발행 중 예외 발생 시 로깅하고 정상 처리")
    void sendCultivationCompletedNotificationFailure() {
        // [Given] RabbitMQ 발송 시 일반 Exception 강제 발생 설정
        doThrow(new RuntimeException("RabbitMQ error"))
                .when(rabbitTemplate).convertAndSend(eq(NOTIFICATION_EXCHANGE), eq(NOTIFICATION_CULTIVATION_COMPLETE_QUEUE), any(CultivationCompletedEvent.class));

        // [When & Then] 서버가 중단되지 않고 안전하게 처리되는지 검증
        assertDoesNotThrow(() ->
                producer.sendCultivationCompleted(1L, 10L, "양송이", new BigDecimal("85.5"), "/cultivations/10")
        );
    }
}
