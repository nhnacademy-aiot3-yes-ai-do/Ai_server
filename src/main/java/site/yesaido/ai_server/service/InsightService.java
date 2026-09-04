package site.yesaido.ai_server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.yesaido.ai_server.client.CultivationClient;
import site.yesaido.ai_server.config.PromptProperties;
import site.yesaido.ai_server.dto.ai.insight.*;
import site.yesaido.ai_server.dto.ai.mush_summary.MushroomCsvDto;
import site.yesaido.ai_server.dto.client.cultivation.*;
import site.yesaido.ai_server.dto.client.sensor.EnvironmentComplianceResponse;
import site.yesaido.ai_server.dto.client.sensor.SensorTypeAverageListResponse;
import site.yesaido.ai_server.dto.client.sensor.SensorTypeAverageResponse;
import site.yesaido.ai_server.dto.cultivation.ProductScoreUpdateRequest;
import site.yesaido.ai_server.entity.Insight;
import site.yesaido.ai_server.reader.MushCsvReader;
import site.yesaido.ai_server.repository.DailyFeedbackRepository;
import site.yesaido.ai_server.repository.InsightRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Stream;
import com.fasterxml.jackson.databind.JsonNode;
import site.yesaido.ai_server.dto.client.sensor.CultivationSensorListResponse;
import site.yesaido.ai_server.entity.DailyFeedback;

import java.util.stream.IntStream;

@Service
@Slf4j
@RequiredArgsConstructor
public class InsightService {
    // Vision 병충해 판단
    private static final String STATUS_UNCERTAIN = "UNCERTAIN"; // 판정보류/애매 (-5점 페널티)
    private static final String STATUS_DISEASE_SUSPECTED = "DISEASE_SUSPECTED"; // 병해 감지 (즉시 폐기: 최대 30점 제한)
    private static final String VISION_ANALYSIS = "visionAnalysis";
    private static final String STATUS = "status";
    private static final String MODE_GROWTH = "GROWTH"; // 모드
    private static final String MODE_HARVEST = "HARVEST";

    private static final Map<Long, BigDecimal> INITIAL_BASELINE_HARVEST_WEIGHTS = Map.of(
            1L, BigDecimal.valueOf(300.0), // 느타리
            2L, BigDecimal.valueOf(400.0), // 양송이
            3L, BigDecimal.valueOf(350.0), // 새송이
            4L, BigDecimal.valueOf(300.0), // 팽이
            5L, BigDecimal.valueOf(500.0)  // 표고
    );
    // 수확량 메모리 캐시(동적 기준)
    private final Map<Long, BigDecimal> dynamicBaselineHarvestWeights = new java.util.concurrent.ConcurrentHashMap<>(INITIAL_BASELINE_HARVEST_WEIGHTS);

    private static final String TEMPERATURE = "TEMPERATURE";
    private static final String HUMIDITY = "HUMIDITY";
    private static final String CO2 = "CO2";
    private static final String LIGHT = "LIGHT";

    private final InsightRepository insightRepository;
    private final DailyFeedbackRepository dailyFeedbackRepository;
    private final CultivationClient cultivationClient;
    private final ChatClient chatClient;
    private final MushCsvReader mushCsvReader; // mushroomId로 버섯 이름 가져오기 위해 추가
    private final PromptProperties promptProperties;

    // 수확 완료 시 Insight 적재(아직 환경 유지율 점수, 전체 평균 센서 값 못가져와서 임시로 작성)
    public InsightCandidateResponse saveHarvestInsight(Long cultivationId, Long userId) {
        InsightRepository repository = Objects.requireNonNull(insightRepository);
        Optional<Insight> existingInsight = repository.findByCultivationId(cultivationId); // 이미 적재된 cultivationId인지 DB 조회

        if (existingInsight.isPresent()) {
            log.info("이미 적재된 인사이트가 존재하여 기존 데이터를 반환합니다. (cultivationId={})", cultivationId);
            return InsightCandidateResponse.from(existingInsight.get());
        }
        log.info("신규 Insight 사전 적재를 시작합니다. (cultivationId={})", cultivationId);

        // Cultivation_server에서 기본 정보(버섯 ID) 가져오기
        CultivationClient client = Objects.requireNonNull(cultivationClient);
        CultivationDetailResponse cultivation = client.getCultivation(userId, cultivationId);
        if (cultivation == null) {
            throw new IllegalStateException("재배지 기본 정보를 조회할 수 없습니다. cultivationId=" + cultivationId);
        }
        if (cultivation.mushroomId() == null) {
            throw new IllegalStateException("재배지의 버섯 정보(mushroomId)가 존재하지 않습니다. cultivationId=" + cultivationId);
        }

        // Cultivation_server에서 수확량(g) 가져오기
        HarvestDetailResponse harvest = client.getHarvest(cultivationId, userId);
        if (harvest == null || harvest.harvestWeight() == null) {
            throw new IllegalStateException("수확량 정보가 누락되었거나 조회할 수 없습니다. cultivationId=" + cultivationId);
        }

        LocalDateTime startedAt = cultivation.startedAt();
        LocalDateTime harvestedAt = harvest.harvestedAt();

        if (startedAt != null && harvestedAt != null && harvestedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("수확일은 재배 시작일보다 빠를 수 없습니다.");
        }

        String startedAtText = (startedAt != null) ? startedAt.toLocalDate().toString() : "정보 없음";
        String harvestedAtText = (harvestedAt != null) ? harvestedAt.toLocalDate().toString() : "정보 없음";
        String cultivationPeriod = calculateCultivationPeriod(startedAt, harvestedAt);
        BigDecimal harvestWeight = getValidHarvestWeight(harvest);

        EnvironmentComplianceResponse compliance = null;
        List<SensorTypeAverageResponse> sensorAverages = null;
        try {
            compliance = client.getEnvironmentCompliance(cultivationId, userId);
            SensorTypeAverageListResponse averageResponse = client.getSensorValuesAverage(cultivationId, userId);
            sensorAverages = (averageResponse != null) ? averageResponse.sensorTypeAverages() : null;
        } catch (Exception e) {
            log.warn("환경 유지율 및 센서 평균 조회 실패 (cultivationId={})", cultivationId, e);
        } // 센서 평균 데이터, 유지율 둘 다 없으면 인사이트 생성 스킵하고 종료하게 추가
        // 유효한 센서 데이터가 전혀 없으면 인사이트 생성을 스킵하고 종료
        if (!hasValidSensorData(sensorAverages, compliance)) {
            log.warn("유효한 센서 측정 데이터가 존재하지 않아 인사이트 생성을 건너뜁니다. (cultivationId={})", cultivationId);
            return null;
        }

        // [컴파일 에러 해결 및 더미값 제거] 실제 센서 평균값 추출 (없으면 null)
        BigDecimal avgTemp = findSensorAverage(sensorAverages, TEMPERATURE);
        BigDecimal avgHum  = findSensorAverage(sensorAverages, HUMIDITY);
        BigDecimal avgCo2  = findSensorAverage(sensorAverages, CO2);
        BigDecimal avgLight= findSensorAverage(sensorAverages, LIGHT);

        String sensorDataText = buildSensorDataText(sensorAverages, compliance);

        Long mushroomId = cultivation.mushroomId();
        String mushroomName = resolveMushroomName(mushroomId);

        // 추가 센서 장착 이력 분석
        String additionalSensorsText = buildAdditionalSensorsText(client, userId, cultivationId);

        // 일일 피드백 누적 데이터 분석(모드 전환, 알림/제어 통계, 병충해 유무)
        List<DailyFeedback> dailyFeedbacks = dailyFeedbackRepository.findAllByCultivationId(cultivationId);
        DailyStatsSummary dailyStats = analyzeDailyFeedbacks(dailyFeedbacks);

        // 버섯 ID와 일일 피드백 조회가 끝난 후, 정밀 점수 산출 및 Cultivation_server로 전송
        Integer growthScore = calculateGrowthScore(compliance, harvestWeight, mushroomId, dailyFeedbacks);
        updateProductScoreWithRetry(client, cultivationId, growthScore);

        // Insight 요약문 생성
        CultivationTimeInfo timeInfo = new CultivationTimeInfo(startedAtText, harvestedAtText, cultivationPeriod);
        String summary = summaryGemini(
                mushroomName, timeInfo, harvestWeight,
                growthScore, sensorDataText, additionalSensorsText, dailyStats
        );

        // Insight 엔티티 조립
        Insight insight = Insight.builder()
                .cultivationId(cultivationId)
                .mushroomId(mushroomId)
                .avgTemperature(avgTemp)
                .avgHumidity(avgHum)
                .avgCo2(avgCo2)
                .avgLight(avgLight)
                .harvestWeightGrams(harvestWeight)
                .growthScore(growthScore)
                .summary(summary)
                .build();

        // 👉 마지막 return은 DB 저장 전용 메서드를 호출하여 반환합니다!
        return saveInsight(insight);
    }

    // 트랜잭션 안에서 외부 호출하면 계속 점유하고 있는 문제가 발생하여 AI로 요약 생성 후 @Transactional 저장 전용 메서드 호출하도록 수정
    public InsightCandidateResponse saveInsight(Insight insight) {

        // AI 서버의 PostgreSQL DB에 저장 후 응답 DTO로 변환하여 리턴
        InsightRepository repository = Objects.requireNonNull(insightRepository);
        Insight saved = repository.save(insight); // SimpleJpaRepository 안에 @Transactional 기본으로 적용되어 있음
        log.info("Insight 신규 사전 적재 완료! (insightId={})", saved.getId());
        return InsightCandidateResponse.from(saved);
    }

    // Gemini로 insight 요약
    private String summaryGemini(
            String mushroomName, CultivationTimeInfo timeInfo,
            BigDecimal weight, Integer score,
            String sensorDataText, String additionalSensorsText,
            DailyStatsSummary dailyStats
    ){
        try{
            ChatClient client = Objects.requireNonNull(chatClient);
            return client.prompt()
                    .system(promptProperties.getInsightSummarySystemPrompt())
                    .user(u -> u.text(promptProperties.getInsightSummaryUserPrompt())
                            .param("mushroomName", mushroomName)
                            .param("startedAt", timeInfo.startedAt())
                            .param("harvestedAt", timeInfo.harvestedAt())
                            .param("cultivationPeriod", timeInfo.period())
                            .param("modeSwitchInfo", dailyStats.modeSwitchInfo())
                            .param("harvestWeight", weight)
                            .param("growthScore", score)
                            .param("sensorDataText", sensorDataText)
                            .param("additionalSensorsText", additionalSensorsText)
                            .param("totalEvents", dailyStats.totalEvents())
                            .param("thresholdAlerts", dailyStats.thresholdAlerts())
                            .param("actuatorSuccessCount", dailyStats.actuatorSuccessCount())
                            .param("actuatorSuccessRate", dailyStats.actuatorSuccessRate())
                            .param("stableDaysRate", dailyStats.stableDaysRate())
                            .param("diseaseStatusText", dailyStats.diseaseStatusText())
                            .param("stableDaysText", dailyStats.stableDaysText())
                            .param("dailyFeedbackSummary", dailyStats.dailySummaryExcerpt())
                    )
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("Gemini 요약 생성 실패, 기본 템플릿으로 대체합니다.", e);
            return String.format(
                    "%s를 %s부터 %s까지 총 %s 동안 재배해 %d점의 환경 유지 점수와 약 %.0fg의 수확량을 기록했습니다.",
                    mushroomName, timeInfo.startedAt(), timeInfo.harvestedAt(), timeInfo.period(), score, weight
            );
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
        return searchSimilarCandidates(userId, mushroomId, targetTemp, targetHum, targetCo2, targetLight);
    }

    // 경작지 ID 기반 우수 인사이트 후보 5개 조회 (BFF 및 프론트엔드 연동용)
    @Transactional(readOnly = true)
    public List<InsightCandidateResponse> getInsightCandidatesByCultivation(
            Long userId, Long cultivationId, Long mushroomId,
            BigDecimal temp, BigDecimal hum, BigDecimal co2, BigDecimal light
    ) {
        CultivationInfo info = resolveCultivationInfo(userId, cultivationId, mushroomId);
        if (info.mushroomId() == null) {
            return List.of();
        }

        // 1. 온·습도 파라미터가 직접 전달된 경우
        if (hasExplicitEnvironment(temp, hum, co2, light)) {
            return searchSimilarCandidates(userId, info.mushroomId(), temp, hum, co2, light);
        }

        // 2. 현재 모드(생육기 vs 수확기) 기준 임계값 매칭
        List<InsightCandidateResponse> matched = searchByModeTargetEnvironment(userId, info.mushroomId(), info.mode());
        if (!matched.isEmpty()) {
            return matched;
        }

        // 3. Fallback (동일 버섯 최고 수확량 TOP 5)
        return findTopHarvestFallback(userId, info.mushroomId());
    }

    // 공통 검색 로직 (private 메서드로 분리하여 Spring Self-Invocation 경고 완벽 해결)
    private List<InsightCandidateResponse> searchSimilarCandidates(
            Long userId,
            Long mushroomId,
            BigDecimal targetTemp,
            BigDecimal targetHum,
            BigDecimal targetCo2,
            BigDecimal targetLight
    ) {
        BigDecimal tempOffset = new BigDecimal("2.00");
        BigDecimal humOffset = new BigDecimal("5.00");
        BigDecimal co2Offset = new BigDecimal("100.00");
        BigDecimal lightOffset = new BigDecimal("50.00");

        BigDecimal minTemp = targetTemp.subtract(tempOffset);
        BigDecimal maxTemp = targetTemp.add(tempOffset);
        BigDecimal minHum  = targetHum.subtract(humOffset);
        BigDecimal maxHum  = targetHum.add(humOffset);
        BigDecimal minCo2  = targetCo2.subtract(co2Offset);
        BigDecimal maxCo2  = targetCo2.add(co2Offset);
        BigDecimal minLight= targetLight.subtract(lightOffset);
        BigDecimal maxLight= targetLight.add(lightOffset);

        List<Long> myCultivationIds = getMyCultivation(userId);

        InsightSearchCondition condition = new InsightSearchCondition(
                mushroomId,
                minTemp, maxTemp,
                minHum, maxHum,
                minCo2, maxCo2,
                minLight, maxLight,
                myCultivationIds
        );
        List<Insight> candidates = insightRepository.findSimilarCandidates(
                condition,
                PageRequest.of(0, 5)
        );

        return candidates.stream()
                .map(InsightCandidateResponse::from)
                .toList();
    }

    // 검색 기록에서 내 Cultivation 조회 안되게 설정
    private List<Long> getMyCultivation(Long userId){
        if (userId == null) return List.of(-1L);
        try {
            CultivationSummaryListResponse res = cultivationClient.getCultivations(userId);
            if (res == null || res.cultivationSummaryResponses() == null) {
                return List.of(-1L);
            }
            List<Long> validIds = res.cultivationSummaryResponses().stream()
                    .map(site.yesaido.ai_server.dto.client.cultivation.CultivationSummaryResponse::cultivationId)
                    .filter(Objects::nonNull)
                    .toList();
            

            return validIds.isEmpty() ? List.of(-1L) : validIds;
        } catch (Exception e) {
            log.warn("사용자 재배 목록 조회 실패 (userId={}). 기본 방어 ID(-1L) 사용", userId, e);
            return List.of(-1L);
        }
    }
    // 생육 점수 로직
    private Integer calculateGrowthScore(
            EnvironmentComplianceResponse compliance,
            BigDecimal actualHarvestWeight,
            Long mushroomId,
            List<DailyFeedback> dailyFeedbacks
    ) {
        double envScore = calculateEnvironmentScore(compliance);
        double yieldScore = calculateYieldScore(actualHarvestWeight, mushroomId);
        double totalScore = applyDiseasePenalty(envScore + yieldScore, dailyFeedbacks);

        return (int) Math.round(Math.clamp(totalScore, 0.0, 100.0));
    }

    // ① 환경 유지 점수 계산 (최대 60점)
    private double calculateEnvironmentScore(EnvironmentComplianceResponse compliance) {
        if (compliance == null) return 0.0;

        List<BigDecimal> compliances = Stream.of(
                compliance.temperatureCompliance(),
                compliance.humidityCompliance(),
                compliance.co2Compliance(),
                compliance.lightCompliance()
        ).filter(Objects::nonNull).toList();

        int sensorCount = compliances.size();
        if (sensorCount == 0) return 0.0;

        double avgCompliance = compliances.stream()
                .mapToDouble(c -> Math.clamp(c.doubleValue(), 0.0, 100.0))
                .average()
                .orElse(0.0);

        double maxEnvScore = switch (sensorCount) {
            case 4 -> 60.0;
            case 3 -> 50.0;
            case 2 -> 40.0;
            case 1 -> 25.0;
            default -> 0.0;
        };
        return (avgCompliance / 100.0) * maxEnvScore;
    }

    // ② 수확량 달성 점수 계산 (최대 40점)
    private double calculateYieldScore(BigDecimal actualHarvestWeight, Long mushroomId) {
        if (actualHarvestWeight == null || actualHarvestWeight.compareTo(BigDecimal.ZERO) <= 0) {
            return 0.0;
        }

        BigDecimal baselineWeight = dynamicBaselineHarvestWeights.getOrDefault(
                mushroomId,
                INITIAL_BASELINE_HARVEST_WEIGHTS.getOrDefault(mushroomId, BigDecimal.valueOf(300.0))
        );
        double ratio = actualHarvestWeight.divide(baselineWeight, 4, RoundingMode.HALF_UP).doubleValue();

        if (ratio >= 1.0) {
            return Math.min(40.0, ratio * 20.0);
        } else if (ratio >= 0.3) {
            return ((ratio - 0.3) / 0.7) * 20.0;
        }
        return 0.0;
    }

    // ③ Vision AI 병해 상태 페널티 보정
    private double applyDiseasePenalty(double totalScore, List<DailyFeedback> dailyFeedbacks) {
        if (dailyFeedbacks == null || dailyFeedbacks.isEmpty()) {
            return totalScore;
        }

        boolean hasDisease = false;
        boolean hasUncertain = false;

        for (DailyFeedback df : dailyFeedbacks) {
            JsonNode snapshot = df.getContextSnapshot();
            if (snapshot != null && snapshot.has(VISION_ANALYSIS)) {
                String status = snapshot.path(VISION_ANALYSIS).path(STATUS).asText("");
                if (STATUS_DISEASE_SUSPECTED.equalsIgnoreCase(status)) {
                    hasDisease = true;
                } else if (STATUS_UNCERTAIN.equalsIgnoreCase(status)) {
                    hasUncertain = true;
                }
            }
        }

        if (hasDisease) {
            return Math.min(totalScore, 30.0);
        } else if (hasUncertain) {
            return Math.max(0.0, totalScore - 5.0);
        }
        return totalScore;
    }
    // 유효한 센서 데이터 있는지 검사(평균값 목록 최소 1개 이상)
    private boolean hasValidSensorData(List<SensorTypeAverageResponse> sensorAverages, EnvironmentComplianceResponse compliance) {
        boolean hasAverages = sensorAverages != null && !sensorAverages.isEmpty();
        boolean hasCompliance = compliance != null && (
                compliance.temperatureCompliance() != null ||
                        compliance.humidityCompliance() != null ||
                        compliance.co2Compliance() != null ||
                        compliance.lightCompliance() != null
        );
        return hasAverages || hasCompliance;
    }

    // 센서 평균 목록에서 특정 센서 타입의 평균값을 찾아 BigDecimal로 반환 (없으면 null)
    private BigDecimal findSensorAverage(List<SensorTypeAverageResponse> list, String sensorType) {
        if (list == null) return null;
        return list.stream()
                .filter(s -> sensorType.equalsIgnoreCase(s.sensorType()) && s.averageValue() != null)
                .map(s -> BigDecimal.valueOf(s.averageValue()).setScale(2, RoundingMode.HALF_UP))
                .findFirst()
                .orElse(null);
    }

    // 재배 기간 계산 분리
    private String calculateCultivationPeriod(LocalDateTime startedAt, LocalDateTime harvestedAt) {
        if (startedAt == null || harvestedAt == null) {
            return "재배 기간 정보 없음";
        }
        Duration duration = Duration.between(
                startedAt.atZone(ZoneId.of("Asia/Seoul")),
                harvestedAt.atZone(ZoneId.of("Asia/Seoul")));

        return String.format("%d일 %d시간 %d분", duration.toDays(), duration.toHoursPart(), duration.toMinutesPart());
    }

    // 수확량 검증 분리
    private BigDecimal getValidHarvestWeight(HarvestDetailResponse harvest) {
        if (harvest == null || harvest.harvestWeight() == null) {
            throw new IllegalArgumentException("수확량 정보가 존재하지 않습니다.");
        }
        BigDecimal weight = harvest.harvestWeight();
        if (weight.signum() < 0) {
            throw new IllegalArgumentException("수확량이 0보다 작을 수 없습니다: " + weight);
        }
        return weight.compareTo(new BigDecimal("9999.99")) > 0 ? new BigDecimal("9999.99") : weight;
    }

    // 센서 및 유지율 텍스트 빌더 분리
    private String buildSensorDataText(List<SensorTypeAverageResponse> sensorAverages, EnvironmentComplianceResponse compliance) {
        StringBuilder sb = new StringBuilder();
        appendSensorAverages(sb, sensorAverages);
        appendCompliance(sb, compliance);

        // 데이터 없음 명시
        if (sb.isEmpty()) {
            sb.append(" - 수집된 센서 평균 및 유지율 데이터가 없습니다.\n");
        }
        return sb.toString();
    }

    // 센서 평균값 분리
    private void appendSensorAverages(StringBuilder sb, List<SensorTypeAverageResponse> sensorAverages) {
        if (sensorAverages == null || sensorAverages.isEmpty()) {
            return;
        }
        for (SensorTypeAverageResponse s : sensorAverages) {
            if (s.averageValue() != null) {
                sb.append(String.format(" - 평균 %s: %.2f%s%n", s.sensorType(), s.averageValue(), s.unit()));
            }
        }
    }

    // 유지율 분리
    private void appendCompliance(StringBuilder sb, EnvironmentComplianceResponse compliance) {
        if (compliance == null) {
            return;
        }
        if (compliance.temperatureCompliance() != null) {
            sb.append(String.format(" - 온도 적정 유지율: %.2f%%%n", compliance.temperatureCompliance()));
        }
        if (compliance.humidityCompliance() != null) {
            sb.append(String.format(" - 습도 적정 유지율: %.2f%%%n", compliance.humidityCompliance()));
        }
        if (compliance.co2Compliance() != null) {
            sb.append(String.format(" - CO2 적정 유지율: %.2f%%%n", compliance.co2Compliance()));
        }
        if (compliance.lightCompliance() != null) {
            sb.append(String.format(" - 조도 적정 유지율: %.2f%%%n", compliance.lightCompliance()));
        }
    }
    /**
     * 특정 수확 인사이트의 상세 정보 및 일자별(1일차, 2일차...) 피드백 타임라인을 조회합니다.
     *
     * <p><b>[보안 및 비즈니스 목적]</b><br>
     * 타인의 경작지 관리 페이지(/cultivations/{id})는 보안상 멤버(OWNER, MANAGER)만 접근할 수 있습니다.<br>
     * 따라서 다른 사용자가 우수 재배 사례(TOP 등급 등)를 벤치마킹할 수 있도록,<br>
     * 기기 시리얼 등 민감정보는 제외하고 순수 농업 데이터(일별 센서 수치 및 일일 AI 피드백)만<br>
     * 안전하게 추출하여 반환하는 공유 창구 역할을 합니다.</p>
     *
     * <p><b>[프론트엔드 활용]</b><br>
     * 프론트엔드의 우수 재배 사례 상세 모달/패널에서 전달받은 {@code dailyRecords}를 바탕으로<br>
     * 1일차부터 수확일까지의 일자별 센서 환경과 AI 리포트를 아코디언/타임라인 형태로 렌더링합니다.</p>
     *
     * @param insightId 조회할 인사이트의 PK ID
     * @return 인사이트 요약 정보 및 일자별 일일 피드백 목록이 포함된 DTO
     * @throws IllegalArgumentException 해당 ID의 인사이트가 존재하지 않는 경우
     */
    @Transactional(readOnly = true)
    public InsightDetailResponse getInsightDetail(Long insightId) {
        Insight insight = insightRepository.findById(insightId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 인사이트입니다. ID: " + insightId));

        List<DailyFeedback> dailyFeedbacks = dailyFeedbackRepository
                .findAllByCultivationId(insight.getCultivationId());

        List<InsightDetailResponse.DailyRecordDto> dailyRecords = IntStream.range(0, dailyFeedbacks.size())
                .mapToObj(i -> {
                    DailyFeedback df = dailyFeedbacks.get(i);
                    JsonNode snapshot = df.getContextSnapshot();
                    return new InsightDetailResponse.DailyRecordDto(
                            i + 1, // dayNumber (1일차, 2일차...)
                            df.getFeedbackDate().toString(),
                            extractSensorAvgFromSnapshot(snapshot, TEMPERATURE),
                            extractSensorAvgFromSnapshot(snapshot, HUMIDITY),
                            extractSensorAvgFromSnapshot(snapshot, CO2),
                            extractSensorAvgFromSnapshot(snapshot, LIGHT),
                            df.getContent()
                    );
                })
                .toList();

        return InsightDetailResponse.of(insight, dailyRecords);
    }

    // 사용자가 온도, 습도, co2, 조도 외에 다른 센서 추가해서 키웠는지 감지해서 요약에 반영하기 위해 추가
    private String buildAdditionalSensorsText(CultivationClient client, Long userId, Long cultivationId) {
        try {
            CultivationSensorListResponse sensorList = client.getAllCultivationSensor(userId, cultivationId);
            if (sensorList == null || sensorList.sensors() == null) {
                return "- 센서 장착 구성: 필수 4대 기본 환경 센서(온도, 습도, CO2, 조도) 중심으로 운영";
            }

            Set<String> standardTypes = Set.of(TEMPERATURE, HUMIDITY, CO2, LIGHT);
            List<String> additionals = sensorList.sensors().stream()
                    .filter(s -> s.sensorTypes() != null)
                    .flatMap(s -> s.sensorTypes().stream())
                    .map(st -> st.type().toUpperCase())
                    .filter(type -> !standardTypes.contains(type))
                    .distinct()
                    .toList();

            // 기본 센서만으로 운영한 경우
            if (additionals.isEmpty()) {
                return "- 센서 장착 구성: 필수 4대 기본 환경 센서(온도, 습도, CO2, 조도) 기반의 표준 환경 제어로 안정적 운영";
            }

            // 추가 센서를 도입한 경우
            String additionalListText = String.join(", ", additionals);
            return String.format("- 센서 장착 구성: 필수 4대 기본 센서 외에 [%s] 센서를 추가 장착하여 한층 더 정밀하고 다각적인 환경 관리 수행", additionalListText);
        } catch (Exception e) {
            log.warn("센서 목록 조회 실패 (cultivationId={}), 기본 센서 텍스트 사용", cultivationId, e);
            return "- 센서 장착 구성: 필수 4대 기본 환경 센서(온도, 습도, CO2, 조도) 중심으로 운영";
        }
    }

    // 일일 피드백 종합 분석
    private DailyStatsSummary analyzeDailyFeedbacks(List<DailyFeedback> dailyFeedbacks) {
        if (dailyFeedbacks == null || dailyFeedbacks.isEmpty()) {
            return new DailyStatsSummary("생육 모드로 안정 관리", 0, 0, 0, 100, 100, "병충해 없음(정상)", "전 기간 안정 유지", "일일 피드백 이력 없음");
        }

        NotificationAccumulator accumulator = new NotificationAccumulator();
        String modeSwitchInfo = "생육기 모드 유지";
        boolean diseaseDetected = false;
        StringBuilder excerpts = new StringBuilder();

        for (int i = 0; i < dailyFeedbacks.size(); i++) {
            DailyFeedback df = dailyFeedbacks.get(i);
            JsonNode snapshot = df.getContextSnapshot();

            modeSwitchInfo = detectModeSwitch(snapshot, i + 1, df.getFeedbackDate().toString(), modeSwitchInfo);
            accumulator.accumulate(snapshot);
            diseaseDetected = diseaseDetected || isDiseaseDetected(snapshot);
            appendSampleSummary(excerpts, df, i, dailyFeedbacks.size());
        }

        String diseaseStatus = diseaseDetected ? "재배 중 병충해 의심 징후 감지됨" : "재배 전 기간 병충해 0건(건강 상태 유지)";
        String stableDays = String.format("총 %d일 중 %d일간 안정 유지",
                dailyFeedbacks.size(), Math.max(1, dailyFeedbacks.size() - (accumulator.thresholdAlerts > 0 ? 1 : 0)));

        int totalActuatorAttempts = accumulator.thresholdAlerts + accumulator.actuatorSuccess;
        int actuatorSuccessRate = totalActuatorAttempts > 0 ? (accumulator.actuatorSuccess * 100) / totalActuatorAttempts : 100;

        int stableDaysCount = Math.max(0, dailyFeedbacks.size() - (accumulator.thresholdAlerts > 0 ? 1 : 0));
        int stableDaysRate = !dailyFeedbacks.isEmpty() ? (stableDaysCount * 100) / dailyFeedbacks.size() : 100;

        return new DailyStatsSummary(
                modeSwitchInfo, accumulator.totalEvents, accumulator.thresholdAlerts,
                accumulator.actuatorSuccess, actuatorSuccessRate, stableDaysRate,
                diseaseStatus, stableDays, excerpts.toString()
        );
    }

    // 모드 전환 감지
    private String detectModeSwitch(JsonNode snapshot, int dayNumber, String date, String currentInfo) {
        if (snapshot == null || !currentInfo.startsWith("생육기")) {
            return currentInfo;
        }

        String mode = snapshot.path("cultivationDetail").path("mode").asText(snapshot.path("mode").asText(""));
        if (MODE_HARVEST.equalsIgnoreCase(mode)) {
            return String.format("생육 %d일차(%s)에 수확 모드로 전환", dayNumber, date);
        }

        return currentInfo;
    }

    // 비전 병충해 감지
    private boolean isDiseaseDetected(JsonNode snapshot) {
        if (snapshot != null && snapshot.has(VISION_ANALYSIS)) {
            String status = snapshot.path(VISION_ANALYSIS).path(STATUS).asText("");
            return STATUS_DISEASE_SUSPECTED.equalsIgnoreCase(status);
        }
        return false;
    }

    // 샘플 요약 텍스트 추가 헬퍼
    private void appendSampleSummary(StringBuilder sb, DailyFeedback df, int index, int totalSize) {
        if (index == 0 || index == totalSize / 2 || index == totalSize - 1) {
            String content = df.getContent();
            String brief = (content != null && content.length() > 80) ? content.substring(0, 80) + "..." : content;
            sb.append(String.format(" - %d일차(%s): %s%n", index + 1, df.getFeedbackDate(), brief));
        }
    }

    // 스냅샷에서 일별 센서 평균값 추출
    private BigDecimal extractSensorAvgFromSnapshot(JsonNode snapshot, String sensorType) {
        if (snapshot == null || !snapshot.has("sensorStatistics")) return null;
        JsonNode stats = snapshot.get("sensorStatistics");
        if (stats.has(sensorType) && stats.get(sensorType).has("average")) {
            return BigDecimal.valueOf(stats.get(sensorType).get("average").asDouble()).setScale(2, RoundingMode.HALF_UP);
        }
        return null;
    }

    // 알림 및 제어 누적용 클래스
    private static class NotificationAccumulator {
        int totalEvents = 0;
        int thresholdAlerts = 0;
        int actuatorSuccess = 0;

        void accumulate(JsonNode snapshot) {
            if (snapshot != null && snapshot.has("notificationMetrics")) {
                JsonNode nm = snapshot.get("notificationMetrics");
                // 실제 DTO 규격 키 우선 조회 + 하위 호환 지원
                this.totalEvents += nm.path("totalNotificationCount").asInt(nm.path("totalEvents").asInt(0));
                this.thresholdAlerts += nm.path("thresholdBreachAlertCount").asInt(nm.path("ruleEngineCooldownThresholdEvents").asInt(0));
                this.actuatorSuccess += nm.path("actuatorControlSucceededCount").asInt(nm.path("actuatorControlSuccessEvents").asInt(0));
            }
        }
    }

    // 점수 갱신 일시적 통신 장애 대비 최대 3회 재시도
    private void updateProductScoreWithRetry(CultivationClient client, Long cultivationId, Integer growthScore) {
        if (growthScore == null) return;

        int maxAttempts = 3;
        ProductScoreUpdateRequest request = new ProductScoreUpdateRequest(BigDecimal.valueOf(growthScore));

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                client.updateProductScore(cultivationId, request);
                log.info("Cultivation_server에 수확 상품 점수 갱신 성공: cultivationId={}, score={}", cultivationId, growthScore);
                return;
            } catch (Exception e) {
                if (attempt == maxAttempts) {
                    log.error("Cultivation_server 상품 점수 갱신 최종 실패 (최대 {}회 재시도 초과, cultivationId={}): {}",
                            maxAttempts, cultivationId, e.getMessage());
                } else {
                    log.warn("Cultivation_server 상품 점수 갱신 재시도 (시도 {}/{}, cultivationId={}): {}",
                            attempt, maxAttempts, cultivationId, e.getMessage());
                    try {
                        Thread.sleep(100L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    private TargetEnvironment resolveTargetEnvironmentByMode(Long mushroomId, String mode) {
        try {
            var referenceList = cultivationClient.getMushroomReference();
            if (referenceList == null || referenceList.mushroomReferenceInfoResponses() == null) {
                return null;
            }

            var mushroomRefOpt = referenceList.mushroomReferenceInfoResponses().stream()
                    .filter(ref -> ref.id() == mushroomId)
                    .findFirst();

            if (mushroomRefOpt.isEmpty() || mushroomRefOpt.get().thresholdInfoResponses() == null) {
                return null;
            }

            // 현재 모드(GROWTH 또는 HARVEST)에 해당하는 임계값 목록 필터링
            var thresholds = mushroomRefOpt.get().thresholdInfoResponses().stream()
                    .filter(t -> t.thresholdType() != null && t.thresholdType().equalsIgnoreCase(mode))
                    .toList();

            BigDecimal temp = calculateMidpoint(thresholds, TEMPERATURE);
            BigDecimal hum = calculateMidpoint(thresholds, HUMIDITY);
            BigDecimal co2 = calculateMidpoint(thresholds, CO2);
            BigDecimal light = calculateMidpoint(thresholds, LIGHT);

            if (temp != null && hum != null && co2 != null && light != null) {
                return new TargetEnvironment(temp, hum, co2, light);
            }
        } catch (Exception e) {
            log.warn("버섯 기준 임계값 조회 실패 (mushroomId={}, mode={})", mushroomId, mode, e);
        }
        return null;
    }

    // 최소값(min)과 최대값(max)의 중간값 계산
    private BigDecimal calculateMidpoint(List<site.yesaido.ai_server.dto.client.mushroom_reference.
            MushroomReferenceThresholdInfoResponse> list, String sensorType) {
        return list.stream()
                .filter(t -> t.sensorType() != null && sensorType.equalsIgnoreCase(t.sensorType().type()))
                .filter(t -> t.thresholdMin() != null && t.thresholdMax() != null)
                .findFirst()
                .map(t -> t.thresholdMin().add(t.thresholdMax()).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP))
                .orElse(null);
    }
    // 인사이트에 사용하는 평균 수확량 기준이 명확하지 않으니 누적된 인사이트의 수확량을 기준으로 계속 업데이트하는거 추가
    // 기준 수확량 자가 튜닝 스케줄러 (서버 시작 시 1회 실행 + 매일 새벽 3시 자동 자가 튜닝 스케줄링)
    @jakarta.annotation.PostConstruct
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 3 * * *")
    public void tuneBaselineHarvestWeights() {
        for (Map.Entry<Long, BigDecimal> entry : INITIAL_BASELINE_HARVEST_WEIGHTS.entrySet()) {
            Long mushroomId = entry.getKey();
            BigDecimal defaultBaseline = entry.getValue();

            List<BigDecimal> weights = insightRepository.findValidHarvestWeightsByMushroomId(mushroomId);
            if (weights.size() >= 5) { // 표본이 5건 이상일 때 자가 튜닝 실행
                BigDecimal trimmedMean = calculateTrimmedMean(weights);
                BigDecimal oldBaseline = dynamicBaselineHarvestWeights.getOrDefault(mushroomId, defaultBaseline);

                // EMA 스무딩 (기존 70% + 신규 절삭평균 30%)
                BigDecimal updatedBaseline = oldBaseline.multiply(BigDecimal.valueOf(0.7))
                        .add(trimmedMean.multiply(BigDecimal.valueOf(0.3)))
                        .setScale(2, RoundingMode.HALF_UP);

                dynamicBaselineHarvestWeights.put(mushroomId, updatedBaseline);
                log.info("[Self-Tuning] 버섯(ID:{}) 기준 수확량 자가 튜닝 완료: {}g -> {}g (표본 수: {}건)",
                        mushroomId, oldBaseline, updatedBaseline, weights.size());
            }
        }
    }

    // 상/하위 10% 이상치 제거 절삭 평균 계산
    private BigDecimal calculateTrimmedMean(List<BigDecimal> sortedWeights) {
        int size = sortedWeights.size();
        int trimCount = Math.max(1, (int) (size * 0.1)); // 상하위 10% (최소 1개)

        List<BigDecimal> trimmedList = sortedWeights.subList(trimCount, size - trimCount);
        if (trimmedList.isEmpty()) {
            trimmedList = sortedWeights;
        }

        BigDecimal sum = trimmedList.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(trimmedList.size()), 2, RoundingMode.HALF_UP);
    }

    // 버섯 이름 조회 헬퍼
    private String resolveMushroomName(Long mushroomId) {
        MushCsvReader csvReader = Objects.requireNonNull(mushCsvReader);
        return csvReader.readMushroomCsv().stream()
                .filter(dto -> dto.mushroomId().equals(mushroomId))
                .map(MushroomCsvDto::mushroomName)
                .findFirst()
                .orElse("버섯");
    }

    private record CultivationInfo(Long mushroomId, String mode) {}

    // 재배지 정보(버섯 ID 및 현재 모드) 추출
    private CultivationInfo resolveCultivationInfo(Long userId, Long cultivationId, Long fallbackMushroomId) {
        if (cultivationId == null) {
            return new CultivationInfo(fallbackMushroomId, MODE_GROWTH);
        }
        try {
            CultivationDetailResponse cultivation = cultivationClient.getCultivation(userId, cultivationId);
            if (cultivation != null) {
                Long targetMushroomId = (fallbackMushroomId != null) ? fallbackMushroomId : cultivation.mushroomId();
                String mode = (cultivation.mode() != null) ? cultivation.mode().toUpperCase() : MODE_GROWTH;
                return new CultivationInfo(targetMushroomId, mode);
            }
        } catch (Exception e) {
            log.warn("재배지 정보 조회 실패 (cultivationId={})", cultivationId, e);
        }
        return new CultivationInfo(fallbackMushroomId, MODE_GROWTH);
    }

    // 4대 필수 센서 파라미터 지정 여부 확인
    private boolean hasExplicitEnvironment(BigDecimal temp, BigDecimal hum, BigDecimal co2, BigDecimal light) {
        return temp != null && hum != null && co2 != null && light != null;
    }

    // 모드별 기준 임계값 기반 유사 검색
    private List<InsightCandidateResponse> searchByModeTargetEnvironment(Long userId, Long mushroomId, String mode) {
        TargetEnvironment targetEnv = resolveTargetEnvironmentByMode(mushroomId, mode);
        if (targetEnv == null) {
            return List.of();
        }
        return searchSimilarCandidates(userId, mushroomId, targetEnv.temp(), targetEnv.hum(), targetEnv.co2(), targetEnv.light());
    }

    // 최고 수확량 Fallback
    private List<InsightCandidateResponse> findTopHarvestFallback(Long userId, Long mushroomId) {
        List<Long> myCultivationIds = getMyCultivation(userId);
        // 내 재배지 제외 고려하여 여유있게 상위 10건 조회 후 5건 추출
        return insightRepository.findTopHarvests(mushroomId, PageRequest.of(0, 10)).stream()
                .filter(i -> !myCultivationIds.contains(i.getCultivationId()))
                .limit(5)
                .map(InsightCandidateResponse::from)
                .toList();
    }
}
