package site.yesaido.ai_server.service;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.yesaido.ai_server.client.CultivationClient;
import site.yesaido.ai_server.dto.cultivation.CultivationDetailResponse;
import site.yesaido.ai_server.dto.cultivation.HarvestDetailResponse;
import site.yesaido.ai_server.dto.insight.InsightCandidateResponse;
import site.yesaido.ai_server.entity.Insight;
import site.yesaido.ai_server.repository.InsightRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED, force = true)
public class InsightService {
    private final InsightRepository insightRepository;
    private final CultivationClient cultivationClient;
    private final ChatClient chatClient;

    @Value("classpath:prompts/insight_summary_system.st")
    private Resource systemPrompt;

    @Value("classpath:prompts/insight_summary_user.st")
    private Resource userPrompt;

    // 수확 완료 시 Insight 적재(아직 환경 유지율 점수, 전체 평균 센서 값 못가져와서 임시로 작성)
    @Transactional
    public InsightCandidateResponse saveHarvestInsight(Long cultivationId, Long userId) {
        Optional<Insight> existingInsight = insightRepository.findByCultivationId(cultivationId); // 이미 적재된 cultivationId인지 DB 조회

        if (existingInsight.isPresent()) {
            log.info("이미 적재된 인사이트가 존재하여 기존 데이터를 반환합니다. (cultivationId={})", cultivationId);
            return InsightCandidateResponse.from(existingInsight.get());
        }
        log.info("신규 Insight 사전 적재를 시작합니다. (cultivationId={})", cultivationId);

        // Cultivation_server에서 기본 정보(버섯 ID) 가져오기
        CultivationDetailResponse cultivation = cultivationClient.getCultivation(cultivationId);

        // Cultivation_server에서 수확량(g) 가져오기
        HarvestDetailResponse harvest = cultivationClient.getHarvest(cultivationId, userId);

        // 수확 정보 방어 로직 (Null 방지)
        BigDecimal harvestWeight = (harvest != null && harvest.harvestWeight() != null)
                ? harvest.harvestWeight() : new BigDecimal("350.00");

        // 테스트용 수치 데이터 (Cultivation 센서 API 완성 전까지 사용)
        BigDecimal avgTemp = new BigDecimal("20.50");
        BigDecimal avgHum  = new BigDecimal("80.00");
        BigDecimal avgCo2  = new BigDecimal("750.00");
        BigDecimal avgLight= new BigDecimal("100.00");
        Integer growthScore = 85;

        // Insight 요약문 생성
        String summary = SummaryGemini(
                cultivation != null && cultivation.name() != null ? cultivation.name() : "버섯",
                avgTemp, avgHum, avgCo2, avgLight, harvestWeight, growthScore
        );

        // Insight 엔티티 생성
        Insight insight = Insight.builder()
                .cultivationId(cultivationId)
                .mushroomId(cultivation != null ? cultivation.mushroomId() : 1L)
                .avgTemperature(avgTemp)
                .avgHumidity(avgHum)
                .avgCo2(avgCo2)
                .avgLight(avgLight)
                .harvestWeightGrams(harvestWeight)
                .growthScore(growthScore)
                .summary(summary) // AI 생성 요약문
                .build();

        // AI 서버의 PostgreSQL DB에 저장 후 응답 DTO로 변환하여 리턴
        Insight saved = insightRepository.save(insight);
        log.info("Insight 신규 사전 적재 완료! (insightId={})", saved.getId());

        return InsightCandidateResponse.from(saved);
    }

    // Gemini로 insight 요약
    private String SummaryGemini(
            String mushroomName, BigDecimal temp, BigDecimal hum,
            BigDecimal co2, BigDecimal light, BigDecimal weight, Integer score
    ){
        try{
            return chatClient.prompt()
                    .system(systemPrompt)
                    .user(u -> u.text(userPrompt)
                            .param("mushroomName", mushroomName)
                            .param("avgTemp", temp)
                            .param("avgHum", hum)
                            .param("avgCo2", co2)
                            .param("avgLight", light)
                            .param("harvestWeight", weight)
                            .param("growthScore", score))
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("Gemini 요약 생성 실패, 기본 템플릿으로 대체합니다.", e);
            return String.format("%s 품종을 평균 %.1f℃, %.1f%% 환경에서 재배하여 약 %.0fg 수확했습니다.", mushroomName, temp, hum, weight);
        }
    }

    // 인사이트 후보 5개 조회(나랑 같은 버섯 잘 키운사람 상위 5개 조회 내가 키운 cultivation은 제외)
    @Transactional(readOnly = true)
    public List<InsightCandidateResponse> getInsightCandidates(
            Long userId,
            Long mushroomId,
            BigDecimal targetTemp,
            BigDecimal targetHum,
            BigDecimal targetCo2,
            BigDecimal targetLight
    ) {
        // 오차 범위 설정
        BigDecimal tempOffset = new BigDecimal("2.00");
        BigDecimal humOffset = new BigDecimal("5.00");
        BigDecimal co2Offset = new BigDecimal("100.00");
        BigDecimal lightOffset = new BigDecimal("50.00");
        // 센서 수치의 검색 최소 최댓값 계산
        BigDecimal minTemp = targetTemp.subtract(tempOffset);
        BigDecimal maxTemp = targetTemp.add(tempOffset);
        BigDecimal minHum  = targetHum.subtract(humOffset);
        BigDecimal maxHum  = targetHum.add(humOffset);
        BigDecimal minCo2  = targetCo2.subtract(co2Offset);
        BigDecimal maxCo2  = targetCo2.add(co2Offset);
        BigDecimal minLight= targetLight.subtract(lightOffset);
        BigDecimal maxLight= targetLight.add(lightOffset);
        // Insight 조회에 내 Cultivation 검색 안되게 방지 내 재배 Id 목록 조회
        List<Long> myCultivationIds = getMyCultivation(userId);

        List<Insight> candidates = insightRepository.findSimilarCandidates(
                mushroomId,
                minTemp, maxTemp,
                minHum, maxHum,
                minCo2, maxCo2,
                minLight, maxLight,
                myCultivationIds,
                PageRequest.of(0, 5) // 최신순 상위 5개만 가져오도록 지정
        );

        return candidates.stream()
                .map(InsightCandidateResponse::from)
                .toList();

    }

    // 검색 기록에서 내 Cultivation 조회 안되게 설정
    private List<Long> getMyCultivation(Long userId){
        if (userId == null) return List.of(-1L);
        try {
            // 내 재배 ID 리스트를 가져오기
            List<Long> ids = cultivationClient.getUserCultivationIds(userId);

            // 내 재배가 없거나 비어있으면 SQL NOT IN () 에러 방지를 위해 가짜 ID(-1L) 반환
            return (ids == null || ids.isEmpty()) ? List.of(-1L) : ids;
        } catch (Exception e) {
            log.warn("사용자 재배 목록 조회 실패 (userId={}). 기본 방어 ID(-1L) 사용", userId, e);
            return List.of(-1L);
        }
    }


    // 인사이트 상세 조회(일일 피드백 완성 후 5개 중 하나 눌렀을 때 일일 피드백 기록 보여주는 기능 구현)
}
