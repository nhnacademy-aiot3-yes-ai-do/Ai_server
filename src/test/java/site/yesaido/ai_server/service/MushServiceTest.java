package site.yesaido.ai_server.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import site.yesaido.ai_server.dto.*;
import site.yesaido.ai_server.exception.MushDataNotFoundException;
import site.yesaido.ai_server.reader.MushCsvReader;
import static org.assertj.core.api.Assertions.*;
import java.util.List;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import java.util.function.Consumer;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MushServiceTest {
    @Mock
    private ChatClient chatClient;

    @Mock
    private MushCsvReader mushCsvReader;

    @InjectMocks
    private MushService mushService;

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
        // 체이닝 중간 통로 가짜 객체 생성
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);

        // chatClient.prompt() 호출 시 -> 요청 전용 매니저(requestSpec)를 돌려줘라
        given(chatClient.prompt()).willReturn(requestSpec);
        // requestSpec.system 호출 시 -> 계속 체이닝할 수 있게 자기 자신(requestSpec) 돌려줘라
        given(requestSpec.system(anyString())).willReturn(requestSpec);
        // requestSpec.user 호출 시 -> 계속 체이닝할 수 있게 자기 자신(requestSpec)을 돌려줘라
        // 타겟 제네릭 타입을 명시하여 모호성 에러 해결
        given(requestSpec.user(Mockito.<Consumer<ChatClient.PromptUserSpec>>any())).willReturn(requestSpec);
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
    }
}
