package site.yesaido.ai_server.dto.ai.sensor_validation;

import java.util.List;

public record AiSensorResultDto(
        List<SensorRangeDto> vegetativePhase, // 재배기 추천값
        List<SensorRangeDto> harvestPhase // 수확기 추천값 (나중에 알림 발송용)
) {
}
