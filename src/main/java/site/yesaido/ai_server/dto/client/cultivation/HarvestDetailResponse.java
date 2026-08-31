package site.yesaido.ai_server.dto.client.cultivation;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record HarvestDetailResponse( // 재배 기본 및 수확 정보
        Long harvestId,
        Long cultivationId,
        BigDecimal harvestWeight, // 수확량
        String name,
        LocalDateTime harvestedAt,
        BigDecimal productScore,
        String productGrade
) {
}
