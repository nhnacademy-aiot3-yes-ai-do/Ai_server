package site.yesaido.ai_server.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.yesaido.ai_server.dto.ai.insight.InsightDetailResponse;
import site.yesaido.ai_server.entity.Insight;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InsightDetailResponseTest {
    @Test
    @DisplayName("Insight 엔티티와 일일 기록 목록으로 InsightDetailResponse를 정상 생성한다")
    void createInsightDetailResponse() {
        Insight insight = Insight.builder()
                .cultivationId(10L)
                .mushroomId(1L)
                .avgTemperature(new BigDecimal("20.5"))
                .avgHumidity(new BigDecimal("80.0"))
                .avgCo2(new BigDecimal("750.0"))
                .avgLight(new BigDecimal("100.0"))
                .harvestWeightGrams(new BigDecimal("350.0"))
                .growthScore(90)
                .summary("좋은 재배 성과")
                .build();

        InsightDetailResponse.DailyRecordDto dailyRecord = new InsightDetailResponse.DailyRecordDto(
                1, "2026-08-27", new BigDecimal("20.0"), new BigDecimal("80.0"),
                new BigDecimal("700.0"), new BigDecimal("100.0"), "생육 양호"
        );

        InsightDetailResponse response = InsightDetailResponse.of(insight, List.of(dailyRecord));

        assertThat(response.cultivationId()).isEqualTo(10L);
        assertThat(response.growthScore()).isEqualTo(90);
        assertThat(response.dailyRecords()).hasSize(1);
    }
}
