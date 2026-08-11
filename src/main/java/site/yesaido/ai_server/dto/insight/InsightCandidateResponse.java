package site.yesaido.ai_server.dto.insight;

import site.yesaido.ai_server.entity.Insight;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InsightCandidateResponse(
        Long insightId,
        Long cultivationId,
        Long mushroomId,
        BigDecimal avgTemperature,
        BigDecimal avgHumidity,
        BigDecimal avgCo2,
        BigDecimal avgLight,
        BigDecimal harvestWeightGrams,
        Integer growthScore,
        String summary,
        LocalDateTime createdAt
) {
    public static InsightCandidateResponse from(Insight insight) {
        return new InsightCandidateResponse(
                insight.getId(),
                insight.getCultivationId(),
                insight.getMushroomId(),
                insight.getAvgTemperature(),
                insight.getAvgHumidity(),
                insight.getAvgCo2(),
                insight.getAvgLight(),
                insight.getHarvestWeightGrams(),
                insight.getGrowthScore(),
                insight.getSummary(),
                insight.getCreatedAt()
        );
    }
}
