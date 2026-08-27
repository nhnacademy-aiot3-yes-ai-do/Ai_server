package site.yesaido.ai_server.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.yesaido.ai_server.dto.ai.insight.InsightCandidateResponse;
import site.yesaido.ai_server.entity.Insight;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
class InsightCandidateResponseTest {
    @Test
    @DisplayName("Insight 엔티티로부터 InsightCandidateResponse를 정상 생성하고 모든 필드를 매핑한다")
    void createInsightCandidateResponseFromEntity() {
        // [Given] Insight 가짜 엔티티 생성
        Insight insight = Insight.builder()
                .cultivationId(10L)
                .mushroomId(1L)
                .avgTemperature(new BigDecimal("20.50"))
                .avgHumidity(new BigDecimal("80.00"))
                .avgCo2(new BigDecimal("750.00"))
                .avgLight(new BigDecimal("100.00"))
                .harvestWeightGrams(new BigDecimal("350.00"))
                .growthScore(90)
                .summary("우수 재배 결과 요약")
                .build();

        // [When] 정적 팩토리 메서드 호출
        InsightCandidateResponse response = InsightCandidateResponse.from(insight);

        // [Then] 모든 필드가 정상적으로 매핑되었는지 검증
        assertThat(response).isNotNull();
        assertThat(response.cultivationId()).isEqualTo(10L);
        assertThat(response.mushroomId()).isEqualTo(1L);
        assertThat(response.avgTemperature()).isEqualTo(new BigDecimal("20.50"));
        assertThat(response.avgHumidity()).isEqualTo(new BigDecimal("80.00"));
        assertThat(response.avgCo2()).isEqualTo(new BigDecimal("750.00"));
        assertThat(response.avgLight()).isEqualTo(new BigDecimal("100.00"));
        assertThat(response.harvestWeightGrams()).isEqualTo(new BigDecimal("350.00"));
        assertThat(response.growthScore()).isEqualTo(90);
        assertThat(response.summary()).isEqualTo("우수 재배 결과 요약");
        assertThat(response.createdAt()).isNotNull();
    }
}
