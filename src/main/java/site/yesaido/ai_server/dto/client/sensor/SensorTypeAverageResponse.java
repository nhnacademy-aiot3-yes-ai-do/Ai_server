package site.yesaido.ai_server.dto.client.sensor;

public record SensorTypeAverageResponse( // 경작지 센서 목록 및 센서 데이터
        Long cultivationId,
        String sensorType, // TEMPERATURE, HUMIDITY, CO2, LIGHT...
        String unit,
        Double averageValue // 평균 량
) {
}
