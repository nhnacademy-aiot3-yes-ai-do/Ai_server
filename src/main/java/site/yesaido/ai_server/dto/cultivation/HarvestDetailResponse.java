package site.yesaido.ai_server.dto.cultivation;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record HarvestDetailResponse(
        Long harvestId,
        Long cultivationId,
        BigDecimal harvestWeight, // 수확량
        String name,
        LocalDateTime harvestedAt,
        BigDecimal productScore,
        String productGrade
) {
}
