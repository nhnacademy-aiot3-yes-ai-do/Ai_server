package site.yesaido.ai_server.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataRetrievalFailureException;
import site.yesaido.ai_server.entity.Insight;
import site.yesaido.ai_server.repository.InsightRepository;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PastHarvestInsightToolTest {
    @Mock
    private InsightRepository insightRepository;

    @InjectMocks
    private PastHarvestInsightTool pastHarvestInsightTool;

    @Test
    @DisplayName("getTopHarvestInsights - 과거 우수 사례가 있을 때 포맷팅 정상 반환")
    void getTopHarvestInsights_success() {
        Insight insight = Insight.builder()
                .cultivationId(7L)
                .mushroomId(1L)
                .harvestWeightGrams(new BigDecimal("500.0"))
                .growthScore(95)
                .avgTemperature(new BigDecimal("16.0"))
                .avgHumidity(new BigDecimal("85.0"))
                .avgCo2(new BigDecimal("1000"))
                .avgLight(new BigDecimal("50"))
                .summary("최적 온습도 유지로 대풍작 달성")
                .build();

        when(insightRepository.findTopHarvests(1L)).thenReturn(List.of(insight));

        String result = pastHarvestInsightTool.getTopHarvestInsights(1L);

        assertThat(result).contains("[수확량: 500.0 g | 환경 점수: 95점]", "AI 성공 분석: 최적 온습도 유지로 대풍작 달성");
    }

    @Test
    @DisplayName("getTopHarvestInsights - 데이터가 없을 때 안내 문구 반환")
    void getTopHarvestInsights_empty() {
        when(insightRepository.findTopHarvests(2L)).thenReturn(Collections.emptyList());

        String result = pastHarvestInsightTool.getTopHarvestInsights(2L);

        assertThat(result).contains("해당 버섯의 과거 수확 완료 데이터가 아직 충분하지 않습니다.");
    }

    @Test
    @DisplayName("getTopHarvestInsights - DataAccessException 발생 시 예외 문구 반환")
    void getTopHarvestInsights_dataAccessException() {
        when(insightRepository.findTopHarvests(1L))
                .thenThrow(new DataRetrievalFailureException("DB connection error"));

        String result = pastHarvestInsightTool.getTopHarvestInsights(1L);

        assertThat(result).contains("과거 수확 데이터베이스를 조회하는 중 오류가 발생했습니다.");
    }
}
