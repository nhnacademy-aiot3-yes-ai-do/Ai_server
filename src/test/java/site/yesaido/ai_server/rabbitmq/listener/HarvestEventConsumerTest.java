package site.yesaido.ai_server.rabbitmq.listener;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import site.yesaido.ai_server.rabbitmq.event.AiEvent.HarvestCompletedEvent;
import site.yesaido.ai_server.service.InsightService;

import java.math.BigDecimal;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HarvestEventConsumerTest {
    @Mock
    private InsightService insightService;

    @InjectMocks
    private HarvestEventConsumer consumer;

    @Test
    @DisplayName("수확 완료 이벤트 수신 시 insightService.saveHarvestInsight 정상 호출")
    void consumeHarvestEventSuccess() {
        HarvestCompletedEvent event = new HarvestCompletedEvent(10L, 1L, "양송이 1번", BigDecimal.valueOf(1200));

        consumer.consumeHarvestEvent(event);

        verify(insightService, times(1)).saveHarvestInsight(10L, 1L);
    }

    @Test
    @DisplayName("인사이트 적재 실패 시 AmqpRejectAndDontRequeueException을 던져 무한 재시도 없이 DLQ로 전달되도록 함")
    void consumeHarvestEventThrowsExceptionOnFailure() {
        // given
        HarvestCompletedEvent event = new HarvestCompletedEvent(10L, 1L, "양송이 1번", BigDecimal.valueOf(1200));
        RuntimeException cause = new RuntimeException("DB 적재 실패");
        doThrow(cause)
                .when(insightService).saveHarvestInsight(10L, 1L);

        // when & then
        assertThatThrownBy(() -> consumer.consumeHarvestEvent(event))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessage("AI 인사이트 적재 실패")
                .hasCause(cause);
    }
}
