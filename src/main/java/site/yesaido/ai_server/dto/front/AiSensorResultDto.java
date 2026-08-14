package site.yesaido.ai_server.dto.front;

import java.util.List;

public record AiSensorResultDto(
        List<SensorRangeDto> vegetativePhase,// 재배기 추천값
        List<SensorRangeDto> harvestPhase,// 수확기 추천값 (나중에 알림 발송용)
        List<SensorRangeDto> absoluteLimits   // 절대 한계 (프론트 차단용)
) {
}
