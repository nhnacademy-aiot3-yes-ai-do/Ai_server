package site.yesaido.ai_server.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import site.yesaido.ai_server.entity.Insight;
import site.yesaido.ai_server.repository.InsightRepository;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PastHarvestInsightTool {
    private final InsightRepository insightRepository;

    @Tool(description = "특정 버섯의 과거 최고 수확량 성공 사례(평균 온습도 환경 데이터, 수확량, 성공 요약 인사이트 등)를 조회합니다.")
    public String getTopHarvestInsights(
            @ToolParam(description = "조회할 버섯의 고유 ID (예: 1=느타리, 2=양송이, 3=새송이, 4=팽이, 5=표고)") Long mushroomId) {

        log.info("버섯 ID {}의 과거 최고 수확량 우수 사례 조회 시작", mushroomId);
        try {
            List<Insight> topInsights = insightRepository.findTopHarvests(mushroomId);
            if (topInsights.isEmpty()) {
                return "해당 버섯의 과거 수확 완료 데이터가 아직 충분하지 않습니다.";
            }

            StringBuilder sb = new StringBuilder(String.format("[과거 우수 수확 성공 사례 TOP %d]%n", topInsights.size()));
            int rank = 1;
            for (Insight insight : topInsights) {
                sb.append(String.format("%d. [수확량: %s g | 환경 점수: %d점]%n", rank++,
                        insight.getHarvestWeightGrams() != null ? insight.getHarvestWeightGrams().toPlainString() : "0.0",
                        insight.getGrowthScore() != null ? insight.getGrowthScore() : 0));

                sb.append(String.format("- 평균 환경: 온도 %s℃ | 습도 %s%% | CO2 %s ppm | 조도 %s lx%n",
                        insight.getAvgTemperature(), insight.getAvgHumidity(), insight.getAvgCo2(), insight.getAvgLight()));

                if (insight.getSummary() != null && !insight.getSummary().isBlank()) {
                    sb.append(String.format("- AI 성공 분석: %s%n", insight.getSummary()));
                }
                sb.append(String.format("%n"));
            }

            return sb.toString();

        } catch (org.springframework.dao.DataAccessException e) {
            log.error("인사이트 데이터베이스 조회 실패: {}", e.getMessage(), e);
            return "과거 수확 데이터베이스를 조회하는 중 오류가 발생했습니다.";
        } catch (Exception e) {
            log.error("과거 수확 인사이트 조회 중 예상치 못한 오류 발생: {}", e.getMessage(), e);
            return "과거 수확 데이터를 조회하는 중 일시적인 오류가 발생했습니다.";
        }
    }
}
