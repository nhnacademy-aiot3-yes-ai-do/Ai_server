package site.yesaido.ai_server.dto.ai.insight;

import site.yesaido.ai_server.entity.Insight;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record InsightDetailResponse(
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
        LocalDateTime createdAt,
        List<DailyRecordDto> dailyRecords
) {
    public record DailyRecordDto(
            int dayNumber,
            String date,
            BigDecimal avgTemperature,
            BigDecimal avgHumidity,
            BigDecimal avgCo2,
            BigDecimal avgLight,
            String dailyFeedback
    ) {}

    public static InsightDetailResponse of(Insight insight, List<DailyRecordDto> dailyRecords) {
        return new InsightDetailResponse(
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
                insight.getCreatedAt(),
                dailyRecords
        );
    }
}
