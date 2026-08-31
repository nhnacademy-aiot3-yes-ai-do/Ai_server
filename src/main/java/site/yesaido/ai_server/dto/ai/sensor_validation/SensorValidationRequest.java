package site.yesaido.ai_server.dto.ai.sensor_validation;

import java.math.BigDecimal;

// 프론트 요청용
public record SensorValidationRequest(
        Long sensorTypeId,
        String sensorTypeName,
        String sensorUnit,
        BigDecimal userMin,
        BigDecimal userMax
) {
}
