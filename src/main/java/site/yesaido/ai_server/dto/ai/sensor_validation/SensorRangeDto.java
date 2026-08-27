package site.yesaido.ai_server.dto.ai.sensor_validation;

import java.math.BigDecimal;
// AI 응답 파싱 및 Redis 저장용
public record SensorRangeDto(
        Long sensorTypeId,
        BigDecimal min,
        BigDecimal max
) {
}
