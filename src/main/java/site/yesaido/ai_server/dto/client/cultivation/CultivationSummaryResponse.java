package site.yesaido.ai_server.dto.client.cultivation;

import java.time.LocalDateTime;

public record CultivationSummaryResponse(
        Long cultivationId,
        String name,
        Long mushroomId,
        String status,
        String mode,
        Integer memberCount,
        String ownerNickname,
        LocalDateTime createdAt
) {
}
