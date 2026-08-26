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
}
