package site.yesaido.ai_server.dto.cultivation;

import java.math.BigDecimal;

public record ProductScoreUpdateResponse(
        Long harvestId,
        BigDecimal productScore,
        String productGrade
) {
}
