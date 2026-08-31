package site.yesaido.ai_server.service;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.yesaido.ai_server.client.CultivationClient;
import site.yesaido.ai_server.dto.ai.mush_summary.MushroomCsvDto;
import site.yesaido.ai_server.dto.client.cultivation.*;
import site.yesaido.ai_server.dto.ai.insight.InsightCandidateResponse;
import site.yesaido.ai_server.dto.ai.insight.InsightSearchCondition;
import site.yesaido.ai_server.dto.client.sensor.EnvironmentComplianceResponse;
import site.yesaido.ai_server.dto.client.sensor.SensorTypeAverageListResponse;
import site.yesaido.ai_server.dto.client.sensor.SensorTypeAverageResponse;
import site.yesaido.ai_server.entity.Insight;
import site.yesaido.ai_server.reader.MushCsvReader;
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

@Service
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Autowired}) // 스프링에게 이 생성자로 의존성 주입하라고 명시
@NoArgsConstructor(access = AccessLevel.PROTECTED, force = true)
public class InsightService {
    private final InsightRepository insightRepository;
    private final CultivationClient cultivationClient;
    private final ChatClient chatClient;
    private final MushCsvReader mushCsvReader; // mushroomId로 버섯 이름 가져오기 위해 추가

    @Value("classpath:prompts/insight_summary_system.st")
    private Resource systemPrompt;

    @Value("classpath:prompts/insight_summary_user.st")
    private Resource userPrompt;

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
        // Cultivation_server에서 수확량(g) 가져오기
        HarvestDetailResponse harvest = client.getHarvest(cultivationId, userId);

        LocalDateTime startedAt =
                cultivation != null ? cultivation.startedAt() : null;

        LocalDateTime harvestedAt =
                harvest != null ? harvest.harvestedAt() : null;

        if (startedAt != null
                && harvestedAt != null
                && harvestedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException(
                    "수확일은 재배 시작일보다 빠를 수 없습니다."
            );
        }

        String startedAtText =
                startedAt != null
                        ? startedAt.toLocalDate().toString()
                        : "정보 없음";

        String harvestedAtText =
                harvestedAt != null
                        ? harvestedAt.toLocalDate().toString()
                        : "정보 없음";

        String cultivationPeriod = calculateCultivationPeriod(startedAt, harvestedAt);
        BigDecimal harvestWeight = getValidHarvestWeight(harvest);

        EnvironmentComplianceResponse compliance = null;
        List<SensorTypeAverageResponse> sensorAverages = null;
        try{
            compliance = client.getEnvironmentCompliance(cultivationId, userId);
            SensorTypeAverageListResponse averageResponse = client.getSensorValuesAverage(cultivationId, userId);
            sensorAverages = (averageResponse != null) ? averageResponse.sensorTypeAverages() : null;
        } catch (Exception e) {
            log.warn("환경 유지율 조회 실패 (cultivationId={}). 기본 점수 및 기본 수치로 대체합니다.", cultivationId, e);
        }

        Integer growthScore = calculateGrowthScore(compliance); // 환경 유지율 기반 성장 점수 계산

        // 실제 센서별 평균값 추출 (조회 실패 시 기본값 사용)
        BigDecimal avgTemp = findSensorAverage(sensorAverages, "TEMPERATURE", new BigDecimal("20.50"));
        BigDecimal avgHum  = findSensorAverage(sensorAverages, "HUMIDITY", new BigDecimal("80.00"));
        BigDecimal avgCo2  = findSensorAverage(sensorAverages, "CO2", new BigDecimal("750.00"));
        BigDecimal avgLight= findSensorAverage(sensorAverages, "LIGHT", new BigDecimal("100.00"));

        String sensorDataText = buildSensorDataText(sensorAverages, compliance);

        Long mushroomId = (cultivation != null && cultivation.mushroomId() != null) ? cultivation.mushroomId() : 1L;
        // MushroomCsvReader 이용해 mushroomId에 해당하는 버섯 이름 가져오기
        MushCsvReader csvReader = Objects.requireNonNull(mushCsvReader);
        String mushroomName = csvReader.readMushroomCsv().stream()
                .filter(dto -> dto.mushroomId().equals(mushroomId))
                .map(MushroomCsvDto::mushroomName)
                .findFirst()
                .orElse("버섯");

        // Insight 요약문 생성
        String summary = summaryGemini(
                mushroomName, sensorDataText, harvestWeight, growthScore,
                startedAtText, harvestedAtText, cultivationPeriod
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
            String mushroomName, String sensorDataText,
            BigDecimal weight, Integer score, String startedAtText,
            String harvestedAtText, String cultivationPeriod
    ){
        try{
            ChatClient client = Objects.requireNonNull(chatClient);
            return client.prompt()
                    .system(systemPrompt)
                    .user(u -> u.text(userPrompt)
                            .param("mushroomName", mushroomName)
                            .param("sensorDataText", sensorDataText)
                            .param("harvestWeight", weight)
                            .param("growthScore", score)
                            .param("startedAt", startedAtText)
                            .param("harvestedAt", harvestedAtText)
                            .param("cultivationPeriod", cultivationPeriod)
                    )

                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("Gemini 요약 생성 실패, 기본 템플릿으로 대체합니다.", e);
            return String.format(
                    "%s를 %s부터 %s까지 총 %s 동안 재배해 평균 온도 20.50℃, 습도 80.00%%, CO2 750.00ppm, " +
                            "조도 100.00lx 환경에서 %d점의 환경 유지 점수와 약 %.0fg의 수확량을 기록했습니다.",
                    mushroomName,
                    startedAtText,
                    harvestedAtText,
                    cultivationPeriod,
                    score,
                    weight
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

    private Integer calculateGrowthScore(EnvironmentComplianceResponse compliance){
        if (compliance == null) return 80;

        List<BigDecimal> score = Stream.of(
                compliance.temperatureCompliance(),
                compliance.humidityCompliance(),
                compliance.co2Compliance(),
                compliance.lightCompliance()
        ).filter(Objects::nonNull).toList(); // null 값 걸러내기
        if(score.isEmpty()) return 80;
        // 센서 유지율 평균 계산(반올림)
        BigDecimal sum = score.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(score.size()), 0, RoundingMode.HALF_UP).intValue();
    }

    // 센서 평균 목록에서 특정 센서 타입의 평균값을 찾아 BigDecimal로 반환 (없으면 기본값 사용)
    private BigDecimal findSensorAverage(List<SensorTypeAverageResponse> list, String sensorType, BigDecimal defaultVal){
        if(list == null) return defaultVal;
        return list.stream()
                .filter(s -> sensorType.equalsIgnoreCase(s.sensorType()) && s.averageValue() != null)
                .map(s -> BigDecimal.valueOf(s.averageValue()).setScale(2, RoundingMode.HALF_UP))
                .findFirst().orElse(defaultVal);
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
        BigDecimal harvestWeight = (harvest != null && harvest.harvestWeight() != null)
                ? harvest.harvestWeight() : new BigDecimal("350.00");

        if (harvestWeight.signum() < 0) {
            throw new IllegalArgumentException("수확량이 음수입니다.");
        }
        if (harvestWeight.compareTo(new BigDecimal("9999.99")) > 0) {
            return new BigDecimal("9999.99");
        }
        return harvestWeight;
    }

    // 센서 및 유지율 텍스트 빌더 분리
    private String buildSensorDataText(List<SensorTypeAverageResponse> sensorAverages, EnvironmentComplianceResponse compliance) {
        StringBuilder sb = new StringBuilder();
        appendSensorAverages(sb, sensorAverages);
        appendCompliance(sb, compliance);

        if (sb.isEmpty()) {
            sb.append(" - 평균 온도: 20.50℃\n")
                    .append(" - 평균 습도: 80.00%\n")
                    .append(" - 평균 CO2: 750.00ppm\n")
                    .append(" - 평균 조도: 100.00lx\n");
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



    // 인사이트 상세 조회(일일 피드백 완성 후 5개 중 하나 눌렀을 때 일일 피드백 기록 보여주는 기능 구현)
}
