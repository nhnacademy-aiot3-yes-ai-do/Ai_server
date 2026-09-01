package site.yesaido.ai_server.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.Resource;
import site.yesaido.ai_server.config.PromptProperties;
import site.yesaido.ai_server.dto.ai.mush_summary.*;
import site.yesaido.ai_server.exception.MushDataNotFoundException;
import site.yesaido.ai_server.reader.MushCsvReader;
import static org.assertj.core.api.Assertions.*;
import java.util.List;
import static org.mockito.BDDMockito.given;

import java.util.function.Consumer;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MushServiceTest {
    @Mock
    private ChatClient chatClient;

    @Mock
    private MushCsvReader mushCsvReader;

    @Mock
    private site.yesaido.ai_server.client.CultivationClient cultivationClient;

    @Mock
    private PromptProperties promptProperties;

    @InjectMocks
    private MushService mushService;

    @Mock
    private Resource systemPrompt;

    @Mock
    private Resource userPrompt;

    @BeforeEach
    void setUp() {
        lenient().when(promptProperties.getMushGuideSystemPrompt()).thenReturn(systemPrompt);
        lenient().when(promptProperties.getMushGuideUserPrompt()).thenReturn(userPrompt);
    }

    @Test
    @DisplayName("존재하지 않는 mushroomId로 조회 시 MushDataNotFoundException 발생")
    void generateRealDataGuideNotFoundTest() {
        // given
        Long notExistId = 999L;
        List<MushroomCsvDto> mockCsvData = List.of(
                new MushroomCsvDto(1L, "느타리버섯", "특징", "내용")
        );
        given(mushCsvReader.readMushroomCsv()).willReturn(mockCsvData);

        // when & then
        assertThatThrownBy(() -> mushService.generateRealDataGuide(notExistId))
                .isInstanceOf(MushDataNotFoundException.class)
                .hasMessageContaining("999");
    }

    /**
     * 람다 내부에서 mushroomId, mushroomName, combinedData 파라미터 실제 전달 되는지 테스트 검증 추가
     */
    @Test
    @DisplayName("정상 조회 시 버섯 가이드 정보 반환")
    void generateRealDataGuideSuccessTest(){
        // given
        Long mushroomId = 1L;

        // 1. MushCsvReader 반환 가짜 데이터 준비
        List<MushroomCsvDto> mockCsvData = List.of(
                new MushroomCsvDto(1L, "느타리버섯", "특징", "느타리버섯은 습한 환경에서 잘 자랍니다.")
        );
        given(mushCsvReader.readMushroomCsv()).willReturn(mockCsvData);

        // 2. AI 가짜 응답 DTO 준비
        EnvironmentConditionInfo cultivationCond = new EnvironmentConditionInfo(
                new SensorRange(18.0, 24.0),
                new SensorRange(80.0, 90.0),
                new SensorRange(800.0, 1200.0),
                new SensorRange(100.0, 500.0)
        );
        EnvironmentConditionInfo harvestCond = new EnvironmentConditionInfo(
                new SensorRange(15.0, 18.0),
                new SensorRange(85.0, 95.0),
                new SensorRange(1000.0, 1500.0),
                new SensorRange(100.0, 300.0)
        );

        MushGuideResponse mockGuide = new MushGuideResponse(
                mushroomId,
                "느타리버섯",
                new AiEvaluationDto(1, 5, "민감도 낮음", "매일 습도 확인"),
                "느타리버섯 요약",
                "건조 주의",
                "냉장 보관 팁",
                cultivationCond,
                harvestCond,
                List.of()
        );

        // 3. CultivationClient 가짜 응답 설정 (GROWTH + HARVEST 모두 설정)
        List<site.yesaido.ai_server.dto.client.mushroom_reference.MushroomReferenceThresholdInfoResponse> mockThresholds = List.of(
                // 재배기 (GROWTH)
                new site.yesaido.ai_server.dto.client.mushroom_reference.MushroomReferenceThresholdInfoResponse(
                        101L,
                        new site.yesaido.ai_server.dto.client.mushroom_reference.SensorTypeInfoResponse(1L, "TEMPERATURE", "°C"),
                        "GROWTH",
                        java.math.BigDecimal.valueOf(18.0), java.math.BigDecimal.valueOf(24.0)),
                new site.yesaido.ai_server.dto.client.mushroom_reference.MushroomReferenceThresholdInfoResponse(
                        102L,
                        new site.yesaido.ai_server.dto.client.mushroom_reference.SensorTypeInfoResponse(2L, "HUMIDITY", "%"),
                        "GROWTH",
                        java.math.BigDecimal.valueOf(80.0), java.math.BigDecimal.valueOf(90.0)),
                new site.yesaido.ai_server.dto.client.mushroom_reference.MushroomReferenceThresholdInfoResponse(
                        103L,
                        new site.yesaido.ai_server.dto.client.mushroom_reference.SensorTypeInfoResponse(3L, "CO2", "ppm"),
                        "GROWTH",
                        java.math.BigDecimal.valueOf(800.0), java.math.BigDecimal.valueOf(1200.0)),
                new site.yesaido.ai_server.dto.client.mushroom_reference.MushroomReferenceThresholdInfoResponse(
                        104L,
                        new site.yesaido.ai_server.dto.client.mushroom_reference.SensorTypeInfoResponse(4L, "LIGHT", "lux"),
                        "GROWTH",
                        java.math.BigDecimal.valueOf(100.0), java.math.BigDecimal.valueOf(500.0)),
                // 수확기 (HARVEST)
                new site.yesaido.ai_server.dto.client.mushroom_reference.MushroomReferenceThresholdInfoResponse(
                        105L,
                        new site.yesaido.ai_server.dto.client.mushroom_reference.SensorTypeInfoResponse(1L, "TEMPERATURE", "°C"),
                        "HARVEST",
                        java.math.BigDecimal.valueOf(15.0), java.math.BigDecimal.valueOf(18.0)),
                new site.yesaido.ai_server.dto.client.mushroom_reference.MushroomReferenceThresholdInfoResponse(
                        106L,
                        new site.yesaido.ai_server.dto.client.mushroom_reference.SensorTypeInfoResponse(2L, "HUMIDITY", "%"),
                        "HARVEST",
                        java.math.BigDecimal.valueOf(85.0), java.math.BigDecimal.valueOf(95.0)),
                new site.yesaido.ai_server.dto.client.mushroom_reference.MushroomReferenceThresholdInfoResponse(
                        107L,
                        new site.yesaido.ai_server.dto.client.mushroom_reference.SensorTypeInfoResponse(3L, "CO2", "ppm"),
                        "HARVEST",
                        java.math.BigDecimal.valueOf(1000.0), java.math.BigDecimal.valueOf(1500.0)),
                new site.yesaido.ai_server.dto.client.mushroom_reference.MushroomReferenceThresholdInfoResponse(
                        108L,
                        new site.yesaido.ai_server.dto.client.mushroom_reference.SensorTypeInfoResponse(4L, "LIGHT", "lux"),
                        "HARVEST",
                        java.math.BigDecimal.valueOf(100.0), java.math.BigDecimal.valueOf(300.0))
        );
        site.yesaido.ai_server.dto.client.mushroom_reference.MushroomReferenceInfoResponse mockRef =
                new site.yesaido.ai_server.dto.client.mushroom_reference.MushroomReferenceInfoResponse(
                        1L, "느타리버섯", "Oyster mushroom", "Pleurotus ostreatus", mockThresholds
                );
        given(cultivationClient.getMushroomReference()).willReturn(
                new site.yesaido.ai_server.dto.client.mushroom_reference.MushroomReferenceInfoListResponse(List.of(mockRef))
        );
        // 체이닝 중간 통로 가짜 객체 생성
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        ChatClient.PromptUserSpec userSpec = mock(ChatClient.PromptUserSpec.class);

        // userSpec 람다 내부 메서드 체이닝을 위해 스텁 설정 추가
        given(userSpec.text((Resource) any())).willReturn(userSpec);
        given(userSpec.param(anyString(), any())).willReturn(userSpec);

        // chatClient.prompt() 호출 시 -> 요청 전용 매니저(requestSpec)를 돌려줘라
        given(chatClient.prompt()).willReturn(requestSpec);
        // requestSpec.system 호출 시 -> 계속 체이닝할 수 있게 자기 자신(requestSpec) 돌려줘라
        given(requestSpec.system((Resource) any())).willReturn(requestSpec);
        // requestSpec.user 호출 시 -> 계속 체이닝할 수 있게 자기 자신(requestSpec)을 돌려줘라
        // 타겟 제네릭 타입을 명시하여 모호성 에러 해결
        given(requestSpec.user(Mockito.<Consumer<ChatClient.PromptUserSpec>>any())).willAnswer(invocation -> {
            Consumer<ChatClient.PromptUserSpec> consumer = invocation.getArgument(0);
            consumer.accept(userSpec); // 람다 내부 .param() 코드를 실제로 실행시킴!
            return requestSpec;
        });
        // requestSpec.call() 호출 시 -> 이제 응답 전용 매니저(callSpec)로 넘겨줘라
        given(requestSpec.call()).willReturn(callSpec);
        // callSpec.entity 호출 시 -> 최종 결과물인 가짜 DTO(mockGuide)를 넘겨주고 끝내라
        given(callSpec.entity(MushGuideResponse.class)).willReturn(mockGuide);

        // when
        MushGuideResponse result = mushService.generateRealDataGuide(mushroomId);

        // then
        assertThat(result).isNotNull()
                .usingRecursiveComparison()
                .isEqualTo(mockGuide);

        verify(mushCsvReader).readMushroomCsv();
        // 값 바인딩 잘 됬나 검증 코드 추가
        verify(userSpec).param("mushroomId", mushroomId);
        verify(userSpec).param("mushroomName", "느타리버섯");
        verify(userSpec).param(org.mockito.ArgumentMatchers.eq("combinedData"), anyString());
    }
}
