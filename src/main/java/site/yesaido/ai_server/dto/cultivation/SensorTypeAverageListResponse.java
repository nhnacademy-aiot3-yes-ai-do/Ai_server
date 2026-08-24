package site.yesaido.ai_server.dto.cultivation;

import java.util.List;

public record SensorTypeAverageListResponse(
        List<SensorTypeAverageResponse> sensorTypeAverages
) {
}
