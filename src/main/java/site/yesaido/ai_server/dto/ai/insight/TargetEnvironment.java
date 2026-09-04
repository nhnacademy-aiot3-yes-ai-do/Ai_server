package site.yesaido.ai_server.dto.ai.insight;

import java.math.BigDecimal;

public record TargetEnvironment(
        BigDecimal temp,
        BigDecimal hum,
        BigDecimal co2,
        BigDecimal light
) {
}
