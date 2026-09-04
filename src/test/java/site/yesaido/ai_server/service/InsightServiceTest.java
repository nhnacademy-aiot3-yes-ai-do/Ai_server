package site.yesaido.ai_server.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import site.yesaido.ai_server.client.CultivationClient;
import site.yesaido.ai_server.config.PromptProperties;
import site.yesaido.ai_server.dto.ai.mush_summary.MushroomCsvDto;
import site.yesaido.ai_server.dto.client.cultivation.*;
import site.yesaido.ai_server.dto.ai.insight.InsightCandidateResponse;
import site.yesaido.ai_server.dto.ai.insight.InsightSearchCondition;
import site.yesaido.ai_server.dto.client.mushroom_reference.MushroomReferenceInfoListResponse;
import site.yesaido.ai_server.dto.client.mushroom_reference.MushroomReferenceInfoResponse;
import site.yesaido.ai_server.dto.client.mushroom_reference.MushroomReferenceThresholdInfoResponse;
import site.yesaido.ai_server.dto.client.mushroom_reference.SensorTypeInfoResponse;
import site.yesaido.ai_server.dto.client.sensor.*;
import site.yesaido.ai_server.entity.DailyFeedback;
import site.yesaido.ai_server.entity.Insight;
import site.yesaido.ai_server.reader.MushCsvReader;
import site.yesaido.ai_server.repository.DailyFeedbackRepository;
import site.yesaido.ai_server.repository.InsightRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InsightServiceTest {
    @Mock
    private PromptProperties promptProperties;

    @Mock
    private InsightRepository insightRepository;

    @Mock
    private DailyFeedbackRepository dailyFeedbackRepository;

    @Mock
    private CultivationClient cultivationClient;

    @Mock
    private ChatClient chatClient;

    @Mock
    private MushCsvReader mushCsvReader;

    @Mock
    private Resource systemPrompt;

    @Mock
    private Resource userPrompt;

    @InjectMocks
    private InsightService insightService;

    private CultivationDetailResponse mockCultivation;
    private HarvestDetailResponse mockHarvest;

    @BeforeEach
    void setUp() {
        lenient().when(promptProperties.getInsightSummarySystemPrompt()).thenReturn(systemPrompt);
        lenient().when(promptProperties.getInsightSummaryUserPrompt()).thenReturn(userPrompt);

        mockCultivation = new CultivationDetailResponse(
                1L, 2L, "FINISHED", "HARVEST", LocalDateTime.now()
        );
        mockHarvest = new HarvestDetailResponse(
                10L, 1L, new BigDecimal("400.00"), "맛있는 느타리",
                LocalDateTime.now(), new BigDecimal("90.0"), null
        );
        // 테스트할 때 진짜 csv 파일 읽지 말고 가짜 데이터 리스트 반환하게 작성
        lenient().when(mushCsvReader.readMushroomCsv()).thenReturn(List.of(
                new MushroomCsvDto(2L, "느타리버섯", "특징", "내용")
        ));

        // 기본 센서 및 유지율 Mock 설정 (새로운 스킵 및 점수 산출 로직 대응)
        EnvironmentComplianceResponse defaultCompliance = new EnvironmentComplianceResponse(
                new BigDecimal("90.00"), new BigDecimal("80.00"), new BigDecimal("70.00"), new BigDecimal("85.00")
        );
        List<SensorTypeAverageResponse> defaultAverages = List.of(
                new SensorTypeAverageResponse(1L, "TEMPERATURE", "°C", 22.50),
                new SensorTypeAverageResponse(1L, "HUMIDITY", "%", 82.00),
                new SensorTypeAverageResponse(1L, "CO2", "ppm", 650.00),
                new SensorTypeAverageResponse(1L, "LIGHT", "lx", 120.00)
        );
        lenient().when(cultivationClient.getEnvironmentCompliance(anyLong(), anyLong())).thenReturn(defaultCompliance);
        lenient().when(cultivationClient.getSensorValuesAverage(anyLong(), anyLong())).thenReturn(new SensorTypeAverageListResponse(defaultAverages));
    }
    // ChatClient 체이닝 모킹 중복 작성 안하기 위해 뻄
    private void setUpMockChatClient(String returnSummary){
        // 가짜 객체 준비
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        ChatClient.PromptUserSpec userSpec = mock(ChatClient.PromptUserSpec.class);
        // userSpec 람다 내부 메서드 체이닝 MOCK
        lenient().when(userSpec.text(any(Resource.class))).thenReturn(userSpec);
        lenient().when(userSpec.param(anyString(), any())).thenReturn(userSpec);

        lenient().when(chatClient.prompt()).thenReturn(requestSpec);
        lenient().when(requestSpec.system((org.springframework.core.io.Resource) any())).thenReturn(requestSpec);
        lenient().when(requestSpec.user(ArgumentMatchers.<Consumer<ChatClient.PromptUserSpec>>any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked") // getArgument 버전 이슈를 방지하기 위해 배열 직접 접근으로 바꿈
            Consumer<ChatClient.PromptUserSpec> consumer = (Consumer<ChatClient.PromptUserSpec>) invocation.getArguments()[0];
            consumer.accept(userSpec);
            return requestSpec;
        });
        lenient().when(requestSpec.call()).thenReturn(callSpec);
        lenient().when(callSpec.content()).thenReturn(returnSummary);
    }

    @Test
    @DisplayName("신규 수확 완료 데이터 들어오면 Insight 정상 적재 확인")
    void saveInsight() {
        setUpMockChatClient("느타리버섯을 최적 환경에서 재배하여 400g 수확했습니다.");
        when(insightRepository.findByCultivationId(1L)).thenReturn(Optional.empty());
        when(cultivationClient.getCultivation(100L, 1L)).thenReturn(mockCultivation);
        when(cultivationClient.getHarvest(1L, 100L)).thenReturn(mockHarvest);

        // DB 저장 가짜 객체 설정
        Insight mockSavedInsight = Insight.builder()
                .cultivationId(1L)
                .mushroomId(2L)
                .avgTemperature(new BigDecimal("22.50"))
                .avgHumidity(new BigDecimal("82.00"))
                .avgCo2(new BigDecimal("650.00"))
                .avgLight(new BigDecimal("120.00"))
                .harvestWeightGrams(new BigDecimal("400.00"))
                .growthScore(85)
                .summary("느타리버섯을 최적 환경에서 재배하여 400g 수확했습니다.")
                .build();
        when(insightRepository.save(any(Insight.class))).thenReturn(mockSavedInsight);

        // 실행 단계
        InsightCandidateResponse response = insightService.saveHarvestInsight(1L, 100L);

        // 검증 단계
        assertThat(response).isNotNull();
        assertThat(response.cultivationId()).isEqualTo(1L);
        assertThat(response.summary()).contains("느타리버섯");
        verify(insightRepository, times(1)).save(any(Insight.class));
    }

    @Test
    @DisplayName("이미 적재된 인사이트가 존재할 경우 AI를 재호출하지 않고 기존 DB 데이터 즉시 반환 (중복 적재 방지)")
    void alreadyExistInsightTest() {
        Insight existingInsight = Insight.builder()
                .cultivationId(1L)
                .mushroomId(2L)
                .summary("요약문")
                .build();
        when(insightRepository.findByCultivationId(1L)).thenReturn(Optional.of(existingInsight));

        InsightCandidateResponse response = insightService.saveHarvestInsight(1L, 100L);

        assertThat(response).isNotNull();
        assertThat(response.summary()).isEqualTo("요약문");
        verify(chatClient, never()).prompt(); // 호출 검증 0번 실행 확인
        verify(insightRepository, never()).save(any()); // 호출 검증 0번 실행 확인
    }

    @Test
    @DisplayName("수확량이 이상치(9999.99g 초과)일 때 9999.99g로 안전하게 클램핑되어 저장되는지 검증")
    void harvestWeightGramsTest(){
        setUpMockChatClient("요약문");

        HarvestDetailResponse overWeightHarvest = new HarvestDetailResponse(
                10L, 1L, new BigDecimal("15000.00"), "맛있는 느타리",
                LocalDateTime.now(), new BigDecimal("90.0"), null);

        when(insightRepository.findByCultivationId(1L)).thenReturn(Optional.empty()); // 테스트를 위해 저장된 것 없다 대답
        when(cultivationClient.getCultivation(100L, 1L)).thenReturn(mockCultivation); // Feign 요청 가짜 객체 반환
        when(cultivationClient.getHarvest(1L, 100L)).thenReturn(overWeightHarvest);
        // DB에 저장하려고 전달받은 그 Insight 객체 그대로 반환
        when(insightRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        insightService.saveHarvestInsight(1L, 100L);
        verify(insightRepository).save(argThat(insight ->
                insight.getHarvestWeightGrams().compareTo(new BigDecimal("9999.99")) == 0));

    }

    @Test
    @DisplayName("유사 인사이트 후보 5개 정상 조회 검증 (내 재배 제외)")
    void InsightCandidatesSuccessTest() {
        CultivationSummaryResponse c1 = new CultivationSummaryResponse(10L, "재배1", 2L, "CULTIVATING", "GROWTH", 1, "유저", null);
        CultivationSummaryResponse c2 = new CultivationSummaryResponse(20L, "재배2", 2L, "CULTIVATING", "GROWTH", 1, "유저", null);
        when(cultivationClient.getCultivations(100L)).thenReturn(new CultivationSummaryListResponse(List.of(c1, c2)));
        when(insightRepository.findSimilarCandidates(any(InsightSearchCondition.class), any(Pageable.class))).thenReturn(List.of());

        List<InsightCandidateResponse> result = insightService.getInsightCandidates(
                100L, 2L,
                new BigDecimal("20.00"), new BigDecimal("80.00"),
                new BigDecimal("700.00"), new BigDecimal("100.00")
        );

        assertThat(result).isNotNull();
        verify(insightRepository).findSimilarCandidates(
                argThat(cond -> cond.mushroomId().equals(2L) && cond.myCultivationIds().equals(List.of(10L, 20L))), any(Pageable.class)
        );
    }

    @Test
    @DisplayName("내 재배 목록 조회 실패(예외 발생) 시 방어 ID(-1L)를 사용해 정상 조회 검증")
    void myCultivationCheckTest() {
        when(cultivationClient.getCultivations(100L)).thenThrow(new RuntimeException("통신 실패"));
        when(insightRepository.findSimilarCandidates(any(InsightSearchCondition.class), any(Pageable.class))).thenReturn(List.of());

        List<InsightCandidateResponse> result = insightService.getInsightCandidates(
                100L, 2L,
                new BigDecimal("20.00"), new BigDecimal("80.00"),
                new BigDecimal("700.00"), new BigDecimal("100.00")
        );

        assertThat(result).isNotNull();
        verify(insightRepository).findSimilarCandidates(
                argThat(cond -> cond.myCultivationIds().equals(List.of(-1L))), any(Pageable.class)
        );
    }

    @Test
    @DisplayName("수확일이 재배 시작일보다 빠를 경우 IllegalArgumentException 발생 검증")
    void saveInsight_HarvestDateBeforeStartDate() {
        when(insightRepository.findByCultivationId(1L)).thenReturn(Optional.empty());

        // 시작일을 현재로, 수확일을 '어제'로 설정 (시간의 모순 발생)
        CultivationDetailResponse wrongCultivation = new CultivationDetailResponse(
                1L, 2L, "FINISHED", "HARVEST", LocalDateTime.now()
        );
        HarvestDetailResponse wrongHarvest = new HarvestDetailResponse(
                10L, 1L, new BigDecimal("400.00"), "맛있는 느타리",
                LocalDateTime.now().minusDays(1), new BigDecimal("90.0"), null
        );

        when(cultivationClient.getCultivation(100L, 1L)).thenReturn(wrongCultivation);
        when(cultivationClient.getHarvest(1L, 100L)).thenReturn(wrongHarvest);

        // 예외가 잘 터지는지 콕 찔러서 확인
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> insightService.saveHarvestInsight(1L, 100L));
    }

    @Test
    @DisplayName("수확량이 음수일 경우 IllegalArgumentException 발생 검증")
    void saveInsight_NegativeHarvestWeight() {
        when(insightRepository.findByCultivationId(1L)).thenReturn(Optional.empty());

        // 수확량을 말도 안 되는 -10g로 설정
        HarvestDetailResponse negativeHarvest = new HarvestDetailResponse(
                10L, 1L, new BigDecimal("-10.00"), "맛있는 느타리",
                LocalDateTime.now(), new BigDecimal("90.0"), null
        );

        when(cultivationClient.getCultivation(100L, 1L)).thenReturn(mockCultivation);
        when(cultivationClient.getHarvest(1L, 100L)).thenReturn(negativeHarvest);

        // signum() < 0 분기에 걸려 예외가 터지는지 확인
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> insightService.saveHarvestInsight(1L, 100L));
    }

    @Test
    @DisplayName("AI 요약(Gemini) 통신 실패 시 기본 요약 템플릿(버섯명, 수확량 포함)으로 대체 생성 검증")
    void summaryGemini_ExceptionFallback() {
        // AI 통신 서버가 죽었다고 강제 가정 (에러 뱉음)
        when(chatClient.prompt()).thenThrow(new RuntimeException("Gemini 통신 완전 실패"));
        when(insightRepository.findByCultivationId(1L)).thenReturn(Optional.empty());
        when(cultivationClient.getCultivation(100L, 1L)).thenReturn(mockCultivation);
        when(cultivationClient.getHarvest(1L, 100L)).thenReturn(mockHarvest);

        // 저장될 때 받은 객체 그대로 리턴하도록 모킹
        when(insightRepository.save(any(Insight.class))).thenAnswer(inv -> inv.getArgument(0));

        InsightCandidateResponse response = insightService.saveHarvestInsight(1L, 100L);

        assertThat(response.summary()).contains("느타리버섯").contains("400g");
    }

    @Test
    @DisplayName("내 재배 목록 조회 시 userId가 null일 경우 방어 로직(-1L 반환) 검증")
    void getInsightCandidates_NullUserId() {
        when(insightRepository.findSimilarCandidates(any(InsightSearchCondition.class), any())).thenReturn(List.of());

        // 일부러 userId 자리에 null을 집어넣음
        insightService.getInsightCandidates(
                null, 2L,
                new BigDecimal("20.00"), new BigDecimal("80.00"),
                new BigDecimal("700.00"), new BigDecimal("100.00")
        );

        verify(insightRepository).findSimilarCandidates( // userId == null 방어막이 작동하여 -1L이 담겼는지 검증
                argThat(cond -> cond.myCultivationIds().contains(-1L)), any()
        );
    }

    @Test
    @DisplayName("실제 환경 유지율과 센서 평균 데이터가 존재할 때 Insight에 정상 반영되는지 검증")
    void saveInsight_ComplianceAndSensorAverages() {
        setUpMockChatClient("느타리버섯 요약문");
        when(insightRepository.findByCultivationId(1L)).thenReturn(Optional.empty());
        when(cultivationClient.getCultivation(100L, 1L)).thenReturn(mockCultivation);
        when(cultivationClient.getHarvest(1L, 100L)).thenReturn(mockHarvest);

        // 실제 환경 유지율 (온도 90%, 습도 80% -> 2개 센서 환경 점수 34점 + 400g 수확량 20점 = 54점)
        EnvironmentComplianceResponse mockCompliance = new EnvironmentComplianceResponse(
                new BigDecimal("90.00"), new BigDecimal("80.00"), null, null
        );
        when(cultivationClient.getEnvironmentCompliance(1L, 100L)).thenReturn(mockCompliance);

        // 실제 센서 평균값
        List<SensorTypeAverageResponse> mockAverages = List.of(
                new SensorTypeAverageResponse(1L, "TEMPERATURE", "°C", 23.40),
                new SensorTypeAverageResponse(1L, "HUMIDITY", "%", 78.50)
        );
        when(cultivationClient.getSensorValuesAverage(1L, 100L)).thenReturn(new SensorTypeAverageListResponse(mockAverages));

        when(insightRepository.save(any(Insight.class))).thenAnswer(inv -> inv.getArgument(0));

        InsightCandidateResponse response = insightService.saveHarvestInsight(1L, 100L);

        // 실제 데이터로 계산된 54점 및 센서 평균값이 DB에 전달되었는지 검증
        assertThat(response).isNotNull();
        verify(insightRepository).save(argThat(saved ->
                saved.getGrowthScore().equals(54) &&
                        saved.getAvgTemperature().compareTo(new BigDecimal("23.40")) == 0 &&
                        saved.getAvgHumidity().compareTo(new BigDecimal("78.50")) == 0
        ));
    }

    @Test
    @DisplayName("환경 데이터 및 센서 평균 조회 실패(0건) 시 인사이트 생성을 스킵하고 null 반환 검증")
    void saveInsight_ComplianceExceptionFallback() {
        when(insightRepository.findByCultivationId(1L)).thenReturn(Optional.empty());
        when(cultivationClient.getCultivation(100L, 1L)).thenReturn(mockCultivation);
        when(cultivationClient.getHarvest(1L, 100L)).thenReturn(mockHarvest);

        // 환경 데이터 조회 시 첫 번째 호출에서 에러 발생 가정 (catch로 넘어가 센서 데이터가 null이 됨)
        when(cultivationClient.getEnvironmentCompliance(1L, 100L)).thenThrow(new RuntimeException("Feign 에러"));

        InsightCandidateResponse response = insightService.saveHarvestInsight(1L, 100L);

        // 센서 데이터가 없으므로 저장을 스킵하고 null을 반환해야 함
        assertThat(response).isNull();
        verify(insightRepository, never()).save(any());
    }

    @Test
    @DisplayName("센서 측정 데이터와 환경 유지율이 전혀 없을 때 인사이트 적재를 안전하게 건너뛰는지(Skip) 검증")
    void saveInsight_SkipWhenNoSensorData() {
        when(insightRepository.findByCultivationId(1L)).thenReturn(Optional.empty());
        when(cultivationClient.getCultivation(100L, 1L)).thenReturn(mockCultivation);
        when(cultivationClient.getHarvest(1L, 100L)).thenReturn(mockHarvest);

        // 센서 데이터가 0건(null)인 상태
        when(cultivationClient.getEnvironmentCompliance(1L, 100L)).thenReturn(null);
        when(cultivationClient.getSensorValuesAverage(1L, 100L)).thenReturn(new SensorTypeAverageListResponse(List.of()));

        InsightCandidateResponse response = insightService.saveHarvestInsight(1L, 100L);

        assertThat(response).isNull();
        verify(insightRepository, never()).save(any());
    }

    @Test
    @DisplayName("인사이트 상세 조회 시 일자별 피드백 타임라인 목록이 정상 반환되는지 검증")
    void getInsightDetailTest() {
        Insight mockInsight = Insight.builder()
                .cultivationId(1L)
                .mushroomId(2L)
                .avgTemperature(new BigDecimal("22.50"))
                .avgHumidity(new BigDecimal("82.00"))
                .avgCo2(new BigDecimal("650.00"))
                .avgLight(new BigDecimal("120.00"))
                .harvestWeightGrams(new BigDecimal("400.00"))
                .growthScore(85)
                .summary("느타리버섯 요약문")
                .build();

        DailyFeedback df1 = DailyFeedback.builder()
                .cultivationId(1L)
                .feedbackDate(java.time.LocalDate.of(2026, 8, 1))
                .hasVisionAnalysis(false)
                .content("1일차 피드백 내용")
                .contextSnapshot(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode())
                .build();

        when(insightRepository.findById(10L)).thenReturn(Optional.of(mockInsight));
        when(dailyFeedbackRepository.findAllByCultivationId(1L)).thenReturn(List.of(df1));

        site.yesaido.ai_server.dto.ai.insight.InsightDetailResponse detail = insightService.getInsightDetail(10L);

        assertThat(detail).isNotNull();
        assertThat(detail.cultivationId()).isEqualTo(1L);
        assertThat(detail.dailyRecords()).hasSize(1);
        assertThat(detail.dailyRecords().getFirst().dayNumber()).isEqualTo(1);
        assertThat(detail.dailyRecords().getFirst().dailyFeedback()).isEqualTo("1일차 피드백 내용");
    }

    @Test
    @DisplayName("존재하지 않는 인사이트 ID로 상세 조회 시 IllegalArgumentException 발생 검증")
    void getInsightDetail_NotFound() {
        when(insightRepository.findById(999L)).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> insightService.getInsightDetail(999L)
        );
    }

    @Test
    @DisplayName("추가 센서(pH 등) 및 일일 피드백(병충해 의심, 수확 모드 전환)이 포함된 경우 종합 분석 정상 동작 검증")
    void saveInsight_WithAdditionalSensorsAndFeedbacks() {
        setUpMockChatClient("종합 분석 요약문");
        when(insightRepository.findByCultivationId(1L)).thenReturn(Optional.empty());
        when(cultivationClient.getCultivation(100L, 1L)).thenReturn(mockCultivation);
        when(cultivationClient.getHarvest(1L, 100L)).thenReturn(mockHarvest);

        // 💡 헬퍼 메서드로 sensorList 생성 분리
        site.yesaido.ai_server.dto.client.sensor.CultivationSensorListResponse sensorList = createSampleSensorList();
        when(cultivationClient.getAllCultivationSensor(100L, 1L)).thenReturn(sensorList);

        // 일일 피드백 (모드전환, 병충해 의심 포함)
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode snapshot = mapper.createObjectNode();
        snapshot.putObject("cultivationDetail").put("mode", "HARVEST");
        snapshot.putObject("visionAnalysis").put("status", "DISEASE_SUSPECTED");
        snapshot.putObject("notificationMetrics")
                .put("totalEvents", 5)
                .put("ruleEngineCooldownThresholdEvents", 1)
                .put("actuatorControlSuccessEvents", 4);

        DailyFeedback df = DailyFeedback.builder()
                .cultivationId(1L)
                .feedbackDate(java.time.LocalDate.of(2026, 8, 1))
                .hasVisionAnalysis(true)
                .content("피드백 상세 내용 1일차")
                .contextSnapshot(snapshot)
                .build();

        when(dailyFeedbackRepository.findAllByCultivationId(1L)).thenReturn(List.of(df));
        when(insightRepository.save(any(Insight.class))).thenAnswer(inv -> inv.getArgument(0));

        InsightCandidateResponse response = insightService.saveHarvestInsight(1L, 100L);

        assertThat(response).isNotNull();
        verify(insightRepository).save(any(Insight.class));
    }

    private site.yesaido.ai_server.dto.client.sensor.CultivationSensorListResponse createSampleSensorList() {
        site.yesaido.ai_server.dto.client.sensor.CultivationSensorTypeResponse phSensor =
                new site.yesaido.ai_server.dto.client.sensor.CultivationSensorTypeResponse(10L, "PH");
        site.yesaido.ai_server.dto.client.sensor.CultivationSensorTypeResponse tempSensor =
                new site.yesaido.ai_server.dto.client.sensor.CultivationSensorTypeResponse(11L, "TEMPERATURE");

        site.yesaido.ai_server.dto.client.sensor.CultivationSensorResponse sensor1 =
                new site.yesaido.ai_server.dto.client.sensor.CultivationSensorResponse(1L, "추가센서", List.of(phSensor, tempSensor));

        return new site.yesaido.ai_server.dto.client.sensor.CultivationSensorListResponse(List.of(sensor1));
    }

    @Test
    @DisplayName("saveInsight: Insight 엔티티 직접 저장 및 응답 변환 검증")
    void saveInsight_directEntity() {
        Insight mockInsight = Insight.builder()
                .cultivationId(1L)
                .mushroomId(2L)
                .summary("직접 저장 요약문")
                .build();

        when(insightRepository.save(mockInsight)).thenReturn(mockInsight);

        InsightCandidateResponse response = insightService.saveInsight(mockInsight);

        assertThat(response).isNotNull();
        assertThat(response.summary()).isEqualTo("직접 저장 요약문");
    }

    @Test
    @DisplayName("자가 튜닝: 유효 수확량 표본이 5건 이상일 때 절삭 평균과 EMA 스무딩으로 기준 수확량 갱신 검증")
    void tuneBaselineHarvestWeights_success() {
        List<BigDecimal> samples = List.of(
                new BigDecimal("100.00"),
                new BigDecimal("400.00"),
                new BigDecimal("500.00"),
                new BigDecimal("600.00"),
                new BigDecimal("1000.00")
        );
        when(insightRepository.findValidHarvestWeightsByMushroomId(2L)).thenReturn(samples);
        when(insightRepository.findValidHarvestWeightsByMushroomId(argThat(id -> id != null && !id.equals(2L))))
                .thenReturn(List.of());

        insightService.tuneBaselineHarvestWeights();

        verify(insightRepository, atLeastOnce()).findValidHarvestWeightsByMushroomId(anyLong());
    }

    @Test
    @DisplayName("자가 튜닝: 수확량 표본이 5건 미만일 때 자가 튜닝을 건너뛰는지 검증")
    void tuneBaselineHarvestWeights_lessThanFiveSamples_skip() {
        when(insightRepository.findValidHarvestWeightsByMushroomId(anyLong()))
                .thenReturn(List.of(new BigDecimal("300.00"), new BigDecimal("400.00")));

        insightService.tuneBaselineHarvestWeights();

        verify(insightRepository, atLeastOnce()).findValidHarvestWeightsByMushroomId(anyLong());
    }

    @Test
    @DisplayName("경작지 기반 인사이트 조회: 4대 센서값이 명시적으로 전달된 경우 해당 센서값으로 즉시 검색 검증")
    void getInsightCandidatesByCultivation_explicitSensors() {
        CultivationDetailResponse cultivationDetail = new CultivationDetailResponse(
                10L, 2L, "CULTIVATING", "GROWTH", LocalDateTime.now()
        );
        when(cultivationClient.getCultivation(100L, 10L)).thenReturn(cultivationDetail);
        CultivationSummaryResponse c1 = new CultivationSummaryResponse(10L, "재배1", 2L, "CULTIVATING", "GROWTH", 1, "유저", null);
        when(cultivationClient.getCultivations(100L)).thenReturn(new CultivationSummaryListResponse(List.of(c1)));
        when(insightRepository.findSimilarCandidates(any(InsightSearchCondition.class), any(Pageable.class)))
                .thenReturn(List.of(Insight.builder().cultivationId(20L).mushroomId(2L).build()));

        List<InsightCandidateResponse> result = insightService.getInsightCandidatesByCultivation(
                100L, 10L, 2L,
                new BigDecimal("21.00"), new BigDecimal("85.00"),
                new BigDecimal("750.00"), new BigDecimal("120.00")
        );

        assertThat(result).hasSize(1);
        verify(insightRepository).findSimilarCandidates(
                argThat(cond -> cond.mushroomId().equals(2L)
                        && cond.minTemp().compareTo(new BigDecimal("19.00")) == 0
                        && cond.maxTemp().compareTo(new BigDecimal("23.00")) == 0),
                any(Pageable.class)
        );
    }

    @Test
    @DisplayName("경작지 기반 인사이트 조회: 센서값이 null일 때 경작지 모드와 버섯 참조 임계값 중간값으로 검색 검증")
    void getInsightCandidatesByCultivation_nullSensors_resolvesModeThresholds() {
        CultivationDetailResponse cultivationDetail = new CultivationDetailResponse(
                10L, 2L, "CULTIVATING", "GROWTH", LocalDateTime.now()
        );
        when(cultivationClient.getCultivation(100L, 10L)).thenReturn(cultivationDetail);

        List<MushroomReferenceThresholdInfoResponse> thresholds = List.of(
                new MushroomReferenceThresholdInfoResponse(1L, new SensorTypeInfoResponse(1L, "TEMPERATURE", "°C"), "GROWTH", new BigDecimal("18.00"), new BigDecimal("22.00")),
                new MushroomReferenceThresholdInfoResponse(2L, new SensorTypeInfoResponse(2L, "HUMIDITY", "%"), "GROWTH", new BigDecimal("80.00"), new BigDecimal("90.00")),
                new MushroomReferenceThresholdInfoResponse(3L, new SensorTypeInfoResponse(3L, "CO2", "ppm"), "GROWTH", new BigDecimal("600.00"), new BigDecimal("800.00")),
                new MushroomReferenceThresholdInfoResponse(4L, new SensorTypeInfoResponse(4L, "LIGHT", "lx"), "GROWTH", new BigDecimal("50.00"), new BigDecimal("150.00"))
        );
        MushroomReferenceInfoResponse mushroomRef = new MushroomReferenceInfoResponse(
                2L, "느타리버섯", "Oyster Mushroom", "Pleurotus ostreatus", thresholds
        );
        when(cultivationClient.getMushroomReference()).thenReturn(new MushroomReferenceInfoListResponse(List.of(mushroomRef)));

        when(cultivationClient.getCultivations(100L)).thenReturn(new CultivationSummaryListResponse(List.of()));
        when(insightRepository.findSimilarCandidates(any(InsightSearchCondition.class), any(Pageable.class)))
                .thenReturn(List.of(Insight.builder().cultivationId(20L).mushroomId(2L).build()));

        List<InsightCandidateResponse> result = insightService.getInsightCandidatesByCultivation(
                100L, 10L, null, null, null, null, null
        );

        assertThat(result).hasSize(1);
        verify(insightRepository).findSimilarCandidates(
                argThat(cond -> cond.mushroomId().equals(2L)
                        && cond.minTemp().compareTo(new BigDecimal("18.00")) == 0
                        && cond.maxTemp().compareTo(new BigDecimal("22.00")) == 0
                        && cond.minHum().compareTo(new BigDecimal("80.00")) == 0
                        && cond.maxHum().compareTo(new BigDecimal("90.00")) == 0
                        && cond.minCo2().compareTo(new BigDecimal("600.00")) == 0
                        && cond.maxCo2().compareTo(new BigDecimal("800.00")) == 0
                        && cond.minLight().compareTo(new BigDecimal("50.00")) == 0
                        && cond.maxLight().compareTo(new BigDecimal("150.00")) == 0),
                any(Pageable.class)
        );
    }

    @Test
    @DisplayName("경작지 기반 인사이트 조회: 유사 검색 결과가 0건일 때 최고 수확량 TOP 5 Fallback 동작 검증")
    void getInsightCandidatesByCultivation_emptyCandidates_fallbackToTopHarvests() {
        CultivationDetailResponse cultivationDetail = new CultivationDetailResponse(
                10L, 2L, "CULTIVATING", "GROWTH", LocalDateTime.now()
        );
        when(cultivationClient.getCultivation(100L, 10L)).thenReturn(cultivationDetail);
        when(cultivationClient.getMushroomReference()).thenReturn(null);
        when(cultivationClient.getCultivations(100L)).thenReturn(new CultivationSummaryListResponse(List.of(
                new CultivationSummaryResponse(10L, "재배1", 2L, "CULTIVATING", "GROWTH", 1, "유저", null)
        )));

        Insight topInsight1 = Insight.builder().cultivationId(10L).mushroomId(2L).harvestWeightGrams(new BigDecimal("1000.00")).build();
        Insight topInsight2 = Insight.builder().cultivationId(20L).mushroomId(2L).harvestWeightGrams(new BigDecimal("900.00")).build();
        when(insightRepository.findTopHarvests(eq(2L), any(Pageable.class))).thenReturn(List.of(topInsight1, topInsight2));

        List<InsightCandidateResponse> result = insightService.getInsightCandidatesByCultivation(
                100L, 10L, 2L, null, null, null, null
        );

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().cultivationId()).isEqualTo(20L);
    }

    @Test
    @DisplayName("경작지 기반 인사이트 조회: cultivationId가 null일 때 fallbackMushroomId 및 기본 모드(GROWTH)로 정상 처리 검증")
    void getInsightCandidatesByCultivation_nullCultivationId() {
        when(cultivationClient.getMushroomReference()).thenReturn(null);
        when(cultivationClient.getCultivations(100L)).thenReturn(new CultivationSummaryListResponse(List.of()));
        when(insightRepository.findTopHarvests(eq(2L), any(Pageable.class))).thenReturn(List.of());

        List<InsightCandidateResponse> result = insightService.getInsightCandidatesByCultivation(
                100L, null, 2L, null, null, null, null
        );

        assertThat(result).isEmpty();
        verify(cultivationClient, never()).getCultivation(anyLong(), anyLong());
    }

    @Test
    @DisplayName("수확 상품 점수 갱신: Cultivation_server 통신이 연속 3회 실패해도 예외 전파 없이 정상 종료 검증")
    void updateProductScoreWithRetry_failureLogged() {
        setUpMockChatClient("느타리버섯 요약문");
        when(insightRepository.findByCultivationId(1L)).thenReturn(Optional.empty());
        when(cultivationClient.getCultivation(100L, 1L)).thenReturn(mockCultivation);
        when(cultivationClient.getHarvest(1L, 100L)).thenReturn(mockHarvest);
        doThrow(new RuntimeException("점수 갱신 서버 장애"))
                .when(cultivationClient).updateProductScore(anyLong(), any());
        when(insightRepository.save(any(Insight.class))).thenAnswer(inv -> inv.getArgument(0));

        InsightCandidateResponse response = insightService.saveHarvestInsight(1L, 100L);

        assertThat(response).isNotNull();
        verify(cultivationClient, times(3)).updateProductScore(eq(1L), any());
    }

    @Test
    @DisplayName("비전 AI 판정: UNCERTAIN 상태가 포함된 경우 총점에서 5점 감점 페널티 적용 검증")
    void saveInsight_withUncertainVisionStatus_appliesPenalty() {
        setUpMockChatClient("요약문");
        when(insightRepository.findByCultivationId(1L)).thenReturn(Optional.empty());
        when(cultivationClient.getCultivation(100L, 1L)).thenReturn(mockCultivation);
        when(cultivationClient.getHarvest(1L, 100L)).thenReturn(mockHarvest);

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode snapshot = mapper.createObjectNode();
        snapshot.putObject("visionAnalysis").put("status", "UNCERTAIN");

        DailyFeedback df = DailyFeedback.builder()
                .cultivationId(1L)
                .feedbackDate(java.time.LocalDate.of(2026, 8, 1))
                .hasVisionAnalysis(true)
                .content("상태 불확실 피드백")
                .contextSnapshot(snapshot)
                .build();
        when(dailyFeedbackRepository.findAllByCultivationId(1L)).thenReturn(List.of(df));
        when(insightRepository.save(any(Insight.class))).thenAnswer(inv -> inv.getArgument(0));

        insightService.saveHarvestInsight(1L, 100L);

        ArgumentCaptor<Insight> captor = ArgumentCaptor.forClass(Insight.class);
        verify(insightRepository).save(captor.capture());

        // 페널티 전 69점(환경 48.75점 + 양송이 400g 기준 수확량 20점 = 68.75점)에서 UNCERTAIN 5점 감점 적용(63.75점 -> 반올림 64점)
        assertThat(captor.getValue().getGrowthScore()).isEqualTo(64);
    }

    @Test
    @DisplayName("유지율 DTO 객체는 존재하지만 4대 유지율 필드가 전부 null이고 센서 평균 데이터도 없으면 인사이트 생성을 스킵하고 null 반환 검증")
    void saveInsight_SkipWhenComplianceFieldsAllNullAndNoSensorAverages() {
        when(insightRepository.findByCultivationId(1L)).thenReturn(Optional.empty());
        when(cultivationClient.getCultivation(100L, 1L)).thenReturn(mockCultivation);
        when(cultivationClient.getHarvest(1L, 100L)).thenReturn(mockHarvest);

        // compliance 객체는 있으나 4대 필드가 전부 null, 센서 평균은 빈 리스트
        EnvironmentComplianceResponse allNullCompliance = new EnvironmentComplianceResponse(null, null, null, null);
        when(cultivationClient.getEnvironmentCompliance(1L, 100L)).thenReturn(allNullCompliance);
        when(cultivationClient.getSensorValuesAverage(1L, 100L)).thenReturn(new SensorTypeAverageListResponse(List.of()));

        InsightCandidateResponse response = insightService.saveHarvestInsight(1L, 100L);

        assertThat(response).isNull();
        verify(insightRepository, never()).save(any());
    }
}
