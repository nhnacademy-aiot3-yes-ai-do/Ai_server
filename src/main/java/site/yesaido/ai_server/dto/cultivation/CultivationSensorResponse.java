package site.yesaido.ai_server.dto.cultivation;

import java.util.List;

public record CultivationSensorResponse(
        Long sensorId,
        String deviceName,
        List<CultivationSensorTypeResponse> sensorTypes
) {}
