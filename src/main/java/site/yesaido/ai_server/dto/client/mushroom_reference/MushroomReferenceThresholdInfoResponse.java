package site.yesaido.ai_server.dto.client.mushroom_reference;

import java.math.BigDecimal;

public record MushroomReferenceThresholdInfoResponse( // 버섯 기준 정보 및 기준 임계값
        Long id,
        SensorTypeInfoResponse sensorType,
        String thresholdType, // GROWTH 또는 HARVEST
        BigDecimal thresholdMin,
        BigDecimal thresholdMax
) {
}
