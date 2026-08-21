package site.yesaido.ai_server.dto.cultivation;

import java.util.List;

public record CultivationSensorListResponse(
        List<CultivationSensorResponse> sensors
) {}
