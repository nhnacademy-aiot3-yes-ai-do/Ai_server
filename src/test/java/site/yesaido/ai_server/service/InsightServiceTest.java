package site.yesaido.ai_server.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.Resource;
import site.yesaido.ai_server.client.CultivationClient;
import site.yesaido.ai_server.dto.cultivation.CultivationDetailResponse;
import site.yesaido.ai_server.dto.cultivation.HarvestDetailResponse;
import site.yesaido.ai_server.dto.insight.InsightCandidateResponse;
import site.yesaido.ai_server.entity.Insight;
import site.yesaido.ai_server.repository.InsightRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    private Resource systemPrompt;

    @Mock
    private Resource userPrompt;

    @InjectMocks
    private InsightService insightService;

    @Test
    @DisplayName("신규 수확 완료 데이터 들어오면 Insight 정상 적재 확인")
    void saveInsight() {
        Long cultivationId = 1L;
        Long userId = 100L;

        when(insightRepository.findById(cultivationId)).thenReturn(Optional.empty());

        CultivationDetailResponse mockCultivation = new CultivationDetailResponse(
                cultivationId, "맛있는 느타리", 2L, "FINISHED", "HARVEST",
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now()
        );
        HarvestDetailResponse mockHarvest = new HarvestDetailResponse(
                10L, cultivationId, new BigDecimal("400.00"), "맛있는 느타리",
                LocalDateTime.now(), new BigDecimal("90.0"), null
        );
        when(cultivationClient.getCultivation(cultivationId)).thenReturn(mockCultivation);
        when(cultivationClient.getHarvest(cultivationId,userId)).thenReturn(mockHarvest);
        // 가짜 객체 준비
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        ChatClient.PromptUserSpec userSpec = mock(ChatClient.PromptUserSpec.class);
        // userSpec 람다 내부 메서드 체이닝 MOCK
        when(userSpec.text(any(Resource.class))).thenReturn(userSpec);
        when(userSpec.param(anyString(), any())).thenReturn(userSpec);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system((org.springframework.core.io.Resource) any())).thenReturn(requestSpec);
        when(requestSpec.user(any(Consumer.class))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked") // getArgument 버전 이슈를 방지하기 위해 배열 직접 접근으로 바꿈
            Consumer<ChatClient.PromptUserSpec> consumer = (Consumer<ChatClient.PromptUserSpec>) invocation.getArguments()[0];
            consumer.accept(userSpec);
            return requestSpec;
        });
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("느타리버섯을 최적 환경에서 재배하여 400g 수확했습니다.");

        // DB 저장 가짜 객체 설정
        Insight mockSavedInsight = Insight.builder()
                .cultivationId(cultivationId)
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
        InsightCandidateResponse response = insightService.saveHarvestInsight(cultivationId, userId);

        // 검증 단계
        assertThat(response).isNotNull();
        assertThat(response.cultivationId()).isEqualTo(cultivationId);
        assertThat(response.summary()).contains("느타리버섯");
        verify(insightRepository, times(1)).save(any(Insight.class));
        verify(insightRepository).findByCultivationId(cultivationId); // DB 중복 확인 호출되었는지 검증
        verify(insightRepository).save(any(Insight.class)); // DB 저장 호출되었는지 검증
    }


}
