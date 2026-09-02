package site.yesaido.ai_server.dto.cultivation;

import java.math.BigDecimal;

public record ProductScoreUpdateRequest(
        BigDecimal productScore
) {
}
