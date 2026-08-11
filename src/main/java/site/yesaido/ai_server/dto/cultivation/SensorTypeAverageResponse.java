package site.yesaido.ai_server.dto.cultivation;

public record SensorTypeAverageResponse(
        Long cultivationId,
        String sensorType, // TEMPERATURE, HUMIDITY, CO2, LIGHT...
        Double averageValue // 평균 량
) {
}
