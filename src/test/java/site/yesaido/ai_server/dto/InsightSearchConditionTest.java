package site.yesaido.ai_server.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.yesaido.ai_server.dto.ai.insight.InsightSearchCondition;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InsightSearchConditionTest {

    @Test
    @DisplayName("InsightSearchCondition 생성 및 필드 검증")
    void insightSearchCondition() {
        InsightSearchCondition condition = new InsightSearchCondition(
                1L,
                BigDecimal.valueOf(15), BigDecimal.valueOf(25),
                BigDecimal.valueOf(70), BigDecimal.valueOf(90),
                BigDecimal.valueOf(800), BigDecimal.valueOf(1200),
                BigDecimal.valueOf(100), BigDecimal.valueOf(500),
                List.of(10L, 20L)
        );

        assertThat(condition.mushroomId()).isEqualTo(1L);
        assertThat(condition.minTemp()).isEqualTo(BigDecimal.valueOf(15));
        assertThat(condition.maxTemp()).isEqualTo(BigDecimal.valueOf(25));
        assertThat(condition.minHum()).isEqualTo(BigDecimal.valueOf(70));
        assertThat(condition.maxHum()).isEqualTo(BigDecimal.valueOf(90));
        assertThat(condition.minCo2()).isEqualTo(BigDecimal.valueOf(800));
        assertThat(condition.maxCo2()).isEqualTo(BigDecimal.valueOf(1200));
        assertThat(condition.minLight()).isEqualTo(BigDecimal.valueOf(100));
        assertThat(condition.maxLight()).isEqualTo(BigDecimal.valueOf(500));
        assertThat(condition.myCultivationIds()).containsExactly(10L, 20L);
    }
}
