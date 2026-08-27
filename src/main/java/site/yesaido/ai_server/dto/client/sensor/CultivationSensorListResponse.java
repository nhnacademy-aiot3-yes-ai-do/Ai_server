package site.yesaido.ai_server.dto.client.sensor;

import java.util.List;

public record CultivationSensorListResponse( // 경작지 센서 목록 및 센서 데이터
        List<CultivationSensorResponse> sensors
) {}
