package site.yesaido.ai_server.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import site.yesaido.ai_server.client.CultivationClient;
import site.yesaido.ai_server.dto.ai.MushroomCsvDto;
import site.yesaido.ai_server.dto.cultivation.CultivationDetailResponse;
import site.yesaido.ai_server.dto.cultivation.HarvestDetailResponse;
import site.yesaido.ai_server.dto.insight.InsightCandidateResponse;
import site.yesaido.ai_server.dto.insight.InsightSearchCondition;
import site.yesaido.ai_server.entity.Insight;
import site.yesaido.ai_server.reader.MushCsvReader;
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
    private InsightRepository insightRepository;

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
        ReflectionTestUtils.setField(insightService, "systemPrompt", systemPrompt);
        ReflectionTestUtils.setField(insightService, "userPrompt", userPrompt);

        mockCultivation = new CultivationDetailResponse(
                1L, "맛있는 느타리", 2L, "FINISHED", "HARVEST",
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now()
        );
        mockHarvest = new HarvestDetailResponse(
                10L, 1L, new BigDecimal("400.00"), "맛있는 느타리",
                LocalDateTime.now(), new BigDecimal("90.0"), null
        );
        // 테스트할 때 진짜 csv 파일 읽지 말고 가짜 데이터 리스트 반환하게 작성
        lenient().when(mushCsvReader.readMushroomCsv()).thenReturn(List.of(
                new MushroomCsvDto(2L, "느타리버섯", "특징", "내용")
        ));
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
        lenient().when(requestSpec.user(any(Consumer.class))).thenAnswer(invocation -> {
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
        when(cultivationClient.getUserCultivationIds(100L)).thenReturn(List.of(10L, 20L));
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
        when(cultivationClient.getUserCultivationIds(100L)).thenThrow(new RuntimeException("통신 실패"));
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
}
