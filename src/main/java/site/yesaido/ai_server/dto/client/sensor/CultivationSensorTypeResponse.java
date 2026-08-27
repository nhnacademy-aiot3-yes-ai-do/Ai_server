package site.yesaido.ai_server.dto.client.sensor;

public record CultivationSensorTypeResponse( // 경작지 센서 목록 및 센서 데이터
        Long sensorTypeId,
        String type
) {}
