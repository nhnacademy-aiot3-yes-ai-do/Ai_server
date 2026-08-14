package site.yesaido.ai_server.rabbitmq.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class AiEvent {
    public record DailyFeedbackGeneratedEvent(
            UUID eventId,
            long userId,
            long cultivationId,
            String cultivationName,
            String feedbackUrl,
            String feedbackContent,
            OffsetDateTime occurredAt
    ) {}

    public record CultivationCompletedEvent(
            UUID eventId,
            long userId,
            long cultivationId,
            String cultivationName,
            BigDecimal growthRate,
            String cultivationUrl,
            OffsetDateTime occurredAt
    ) {}
}
