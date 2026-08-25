package site.yesaido.ai_server.rabbitmq.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import site.yesaido.ai_server.service.InsightService;
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
            insightService.saveHarvestInsight(event.cultivationId(), event.userId());
            log.info("수확 완료 건에 대한 AI 인사이트 DB 적재 성공: cultivationId={}", event.cultivationId());
        } catch (Exception e) {
            log.error("AI 인사이트 적재 실패 (DLQ로 이동): cultivationId={}", event.cultivationId(), e);
            throw e; // 예외를 던져야 RabbitMQ가 실패를 인지하고 DLQ로 넘김
        }
    }

    public record HarvestCompletedEvent( // Cultivation이 보내주는 DTO
            Long cultivationId,
            Long userId,
            String cultivationName,
            Double harvestWeight
    ) {}
}
