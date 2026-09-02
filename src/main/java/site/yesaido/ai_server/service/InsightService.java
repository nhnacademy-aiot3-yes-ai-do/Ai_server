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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import com.fasterxml.jackson.databind.JsonNode;
import site.yesaido.ai_server.dto.client.sensor.CultivationSensorListResponse;
import site.yesaido.ai_server.entity.DailyFeedback;
import java.util.Set;
import java.util.stream.IntStream;

@Service
@Slf4j
@RequiredArgsConstructor
public class InsightService {
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
        }

        Integer growthScore = calculateGrowthScore(compliance);
        updateProductScoreWithRetry(client, cultivationId, growthScore);

        // [컴파일 에러 해결 및 더미값 제거] 실제 센서 평균값 추출 (없으면 null)
        BigDecimal avgTemp = findSensorAverage(sensorAverages, TEMPERATURE);
        BigDecimal avgHum  = findSensorAverage(sensorAverages, HUMIDITY);
        BigDecimal avgCo2  = findSensorAverage(sensorAverages, CO2);
        BigDecimal avgLight= findSensorAverage(sensorAverages, LIGHT);

        String sensorDataText = buildSensorDataText(sensorAverages, compliance);

        Long mushroomId = cultivation.mushroomId();
        MushCsvReader csvReader = Objects.requireNonNull(mushCsvReader);
        String mushroomName = csvReader.readMushroomCsv().stream()
                .filter(dto -> dto.mushroomId().equals(mushroomId))
                .map(MushroomCsvDto::mushroomName)
                .findFirst()
                .orElse("버섯");

        // 추가 센서 장착 이력 분석
        String additionalSensorsText = buildAdditionalSensorsText(client, userId, cultivationId);
        // 일일 피드백 누적 데이터 분석(모드 전환, 알림/제어 통계, 병충해 유무)
        List<DailyFeedback> dailyFeedbacks = dailyFeedbackRepository.findAllByCultivationId(cultivationId);
        DailyStatsSummary dailyStats = analyzeDailyFeedbacks(dailyFeedbacks);

        // Insight 요약문 생성
        CultivationTimeInfo timeInfo = new CultivationTimeInfo(startedAtText, harvestedAtText, cultivationPeriod);
        String summary = summaryGemini(
                mushroomName, timeInfo, harvestWeight,
                growthScore, sensorDataText, additionalSensorsText, dailyStats
        );

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

        InsightSearchCondition condition = new InsightSearchCondition(
                mushroomId,
                minTemp, maxTemp,
                minHum, maxHum,
                minCo2, maxCo2,
                minLight, maxLight,
                myCultivationIds
        );
        InsightRepository repository = Objects.requireNonNull(insightRepository);
        List<Insight> candidates = repository.findSimilarCandidates(
                condition,
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
    // 생육 점수 로직 변경 필요
    private Integer calculateGrowthScore(EnvironmentComplianceResponse compliance) {
        if (compliance == null) return null;

        List<BigDecimal> score = Stream.of(
                compliance.temperatureCompliance(),
                compliance.humidityCompliance(),
                compliance.co2Compliance(),
                compliance.lightCompliance()
        ).filter(Objects::nonNull).toList();

        if (score.isEmpty()) return null;

        BigDecimal sum = score.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(score.size()), 0, RoundingMode.HALF_UP).intValue();
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

    // 2) 유지율 분리
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
                            extractSensorAvgFromSnapshot(snapshot, TEMPERATURE, null),
                            extractSensorAvgFromSnapshot(snapshot, HUMIDITY, null),
                            extractSensorAvgFromSnapshot(snapshot, CO2, null),
                            extractSensorAvgFromSnapshot(snapshot, LIGHT, null),
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
        if ("HARVEST".equalsIgnoreCase(mode)) {
            return String.format("생육 %d일차(%s)에 수확 모드로 전환", dayNumber, date);
        }

        return currentInfo;
    }

    // 비전 병충해 감지
    private boolean isDiseaseDetected(JsonNode snapshot) {
        if (snapshot != null && snapshot.has("visionAnalysis")) {
            String status = snapshot.path("visionAnalysis").path("status").asText("");
            return "DISEASE_SUSPECTED".equalsIgnoreCase(status);
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
    private BigDecimal extractSensorAvgFromSnapshot(JsonNode snapshot, String sensorType, BigDecimal fallback) {
        if (snapshot == null || !snapshot.has("sensorStatistics")) return fallback;
        JsonNode stats = snapshot.get("sensorStatistics");
        if (stats.has(sensorType) && stats.get(sensorType).has("average")) {
            return BigDecimal.valueOf(stats.get(sensorType).get("average").asDouble()).setScale(2, RoundingMode.HALF_UP);
        }
        return fallback;
    }

    // 알림 및 제어 누적용 클래스
    private static class NotificationAccumulator {
        int totalEvents = 0;
        int thresholdAlerts = 0;
        int actuatorSuccess = 0;

        void accumulate(JsonNode snapshot) {
            if (snapshot != null && snapshot.has("notificationMetrics")) {
                JsonNode nm = snapshot.get("notificationMetrics");
                this.totalEvents += nm.path("totalEvents").asInt(0);
                this.thresholdAlerts += nm.path("ruleEngineCooldownThresholdEvents").asInt(0);
                this.actuatorSuccess += nm.path("actuatorControlSuccessEvents").asInt(0);
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
}
