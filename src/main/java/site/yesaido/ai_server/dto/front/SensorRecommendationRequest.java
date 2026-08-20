package site.yesaido.ai_server.dto.front;

import java.math.BigDecimal;

// 프론트 요청용
public record SensorRecommendationRequest(
        Long cultivationId,
        Long sensorTypeId,
        String sensorTypeName,
        String sensorUnit,
        BigDecimal userMin,
        BigDecimal userMax
) {
}
