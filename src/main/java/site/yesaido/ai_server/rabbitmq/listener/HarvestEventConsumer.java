package site.yesaido.ai_server.rabbitmq.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import site.yesaido.ai_server.dto.ai.insight.InsightCandidateResponse;
import site.yesaido.ai_server.rabbitmq.event.AiEvent.HarvestCompletedEvent;
import site.yesaido.ai_server.service.InsightService;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import static site.yesaido.ai_server.rabbitmq.RabbitMqConstants.AI_HARVEST_QUEUE;

@Slf4j
@Component
@RequiredArgsConstructor
public class HarvestEventConsumer {
    private final InsightService insightService;

    @RabbitListener(queues = AI_HARVEST_QUEUE)
    public void consumeHarvestEvent(@Payload HarvestCompletedEvent event){
        log.info("Cultivation_server로부터 수확 완료 이벤트 수신: cultivationId={}, userId={}", event.cultivationId(), event.userId());
        try{
            InsightCandidateResponse result = insightService.saveHarvestInsight(event.cultivationId(), event.userId());
            if (result == null) {
                log.info("센서 측정 데이터 부재로 AI 인사이트 생성을 건너뛰었습니다: cultivationId={}", event.cultivationId());
            } else {
                log.info("수확 완료 건에 대한 AI 인사이트 DB 적재 성공: cultivationId={}", event.cultivationId());
            }
        } catch (Exception e) {
            log.error("AI 인사이트 적재 실패 (DLQ로 이동): cultivationId={}", event.cultivationId(), e);
            throw new AmqpRejectAndDontRequeueException("AI 인사이트 적재 실패", e);
        }
    }
}
