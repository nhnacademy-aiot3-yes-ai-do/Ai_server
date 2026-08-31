package site.yesaido.ai_server.dto.client.sensor;

import java.math.BigDecimal;

public record EnvironmentComplianceResponse( // 경작지 센서 목록 및 센서 데이터
        BigDecimal temperatureCompliance, // 온도 적정 범위 유지율
        BigDecimal humidityCompliance, // 습도 적정 범위 유지율
        BigDecimal co2Compliance, // co2 적정 범위 유지율
        BigDecimal lightCompliance // 조도 적정 범위 유지율
) {
}
