package site.yesaido.ai_server.dto.ai.insight;

import java.math.BigDecimal;
import java.util.List;

public record InsightSearchCondition(
        Long mushroomId,
        BigDecimal minTemp, BigDecimal maxTemp,
        BigDecimal minHum, BigDecimal maxHum,
        BigDecimal minCo2, BigDecimal maxCo2,
        BigDecimal minLight, BigDecimal maxLight,
        List<Long> myCultivationIds
) {
}
