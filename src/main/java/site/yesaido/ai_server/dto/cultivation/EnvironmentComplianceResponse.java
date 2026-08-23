package site.yesaido.ai_server.dto.cultivation;

import java.math.BigDecimal;

public record EnvironmentComplianceResponse(
        BigDecimal temperatureCompliance, // 온도 적정 범위 유지율
        BigDecimal humidityCompliance, // 습도 적정 범위 유지율
        BigDecimal co2Compliance, // co2 적정 범위 유지율
        BigDecimal lightCompliance // 조도 적정 범위 유지율
) {
}
