package site.yesaido.ai_server.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import site.yesaido.ai_server.client.CultivationClient;
import site.yesaido.ai_server.config.PromptProperties;
import site.yesaido.ai_server.dto.ai.mush_summary.MushroomCsvDto;
import site.yesaido.ai_server.dto.client.cultivation.CultivationDetailResponse;
import static org.mockito.Mockito.lenient;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import site.yesaido.ai_server.dto.ai.sensor_validation.AiSensorResultDto;
import site.yesaido.ai_server.dto.ai.sensor_validation.SensorRangeDto;
import site.yesaido.ai_server.dto.ai.sensor_validation.SensorValidationRequest;
import site.yesaido.ai_server.dto.ai.sensor_validation.SensorValidationResponse;
import site.yesaido.ai_server.exception.AiAnalysisFailedException;
import site.yesaido.ai_server.reader.MushCsvReader;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SensorValidationServiceTest {
    // 메서드 체이닝을 한 줄로 모킹해주는 옵션(객체에 . 찍고 들어가서 반환하는 것들 자동으로 가짜(Mock) 무한 생성)
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient geminiChatClient;

    @Mock
    private PromptProperties promptProperties;

    @Mock private CultivationClient cultivationClient;
    @Mock private ObjectMapper objectMapper;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private HashOperations<String, String, String> hashOps;
    @Mock private MushCsvReader mushCsvReader;

    @InjectMocks
    private SensorValidationService sensorValidationService;
    // 공통 변수
    private static final Long USER_ID = 1L;
    private static final Long CULTIVATION_ID = 100L;
    private static final Long MUSHROOM_ID = 1L;
    private static final String REDIS_KEY = "mushroom:sensor:validation:1";

    @BeforeEach
    void setup() {
        sensorValidationService = new SensorValidationService(
                geminiChatClient, cultivationClient,
                objectMapper, redisTemplate, mushCsvReader, promptProperties);

        // ✅ lenient()를 붙여서 AI를 호출하지 않는 캐시 테스트에서도 에러가 안 나도록 설정!
        lenient().when(promptProperties.getSensorValidationSystemPrompt())
                .thenReturn(new ByteArrayResource("system prompt".getBytes()));
        lenient().when(promptProperties.getSensorValidationUserPrompt())
                .thenReturn(new ByteArrayResource("user prompt".getBytes()));

        given(cultivationClient.getCultivation(USER_ID, CULTIVATION_ID))
                .willReturn(new CultivationDetailResponse(CULTIVATION_ID, MUSHROOM_ID, "ACTIVE", "AUTO", LocalDateTime.now()));

        given(mushCsvReader.readMushroomCsv())
                .willReturn(List.of(new MushroomCsvDto(MUSHROOM_ID, "느타리버섯", "title", "content")));

        given(redisTemplate.<String, String>opsForHash()).willReturn(hashOps);
    }

    @Test
    @DisplayName("캐시 없음 + 정상 범위 입력 -> AI 호출 후 통과(true) 응답")
    void validate_CacheMiss_ValidInput() throws Exception{
        // Given
        // 유저가 16~19도를 입력함
        SensorValidationRequest request = new SensorValidationRequest(
                10L, "TEMPERATURE", "°C", BigDecimal.valueOf(16), BigDecimal.valueOf(19)
        );

        given(hashOps.get(anyString(), anyString())).willReturn(null); // 캐시 비어있음 설정

        // AI가 15~20도를 추천하도록 설정
        AiSensorResultDto mockAiResponse = new AiSensorResultDto(
                List.of(new SensorRangeDto(10L, BigDecimal.valueOf(15), BigDecimal.valueOf(20))),
                List.of());
        given(geminiChatClient.prompt().system(any(Consumer.class)).user(anyString()).call().entity(AiSensorResultDto.class))
                .willReturn(mockAiResponse);

        given(objectMapper.writeValueAsString(any())).willReturn("{\"mocked\":\"json\"}");

        // When
        SensorValidationResponse response = sensorValidationService.validateSensorThreshold(USER_ID, CULTIVATION_ID, request);

        // Then
        assertThat(response.isValid()).isTrue();
        assertThat(response.message()).contains("적절한 임계값입니다");

        // AI가 정상 호출되고, 캐시에 10번 센서 결과가 1번 저장되었는지 검증
        verify(hashOps, times(1)).put(eq(REDIS_KEY), eq("10"), anyString());
    }

    @Test
    @DisplayName("캐시 있음 + 비정상 범위 입력 -> AI 호출 없이 즉시 거절(false) 피드백 반환")
    void validate_CacheHit_InvalidInput() throws Exception{
        // Given
        // 유저가 온도를 10~30도로 너무 넓게 설정함 (비정상 입력)
        SensorValidationRequest request = new SensorValidationRequest(
                10L, "TEMPERATURE", "°C", BigDecimal.valueOf(10), BigDecimal.valueOf(30)
        );

        // 캐시에 이미 15~20도 정답이 있다고 설정
        given(hashOps.get(REDIS_KEY, "10")).willReturn("{\"mocked\": \"json\"}");

        AiSensorResultDto cachedDto = new AiSensorResultDto(
                List.of(new SensorRangeDto(10L, BigDecimal.valueOf(15), BigDecimal.valueOf(20))),
                List.of());
        given(objectMapper.readValue(anyString(), eq(AiSensorResultDto.class))).willReturn(cachedDto);

        // When
        SensorValidationResponse response = sensorValidationService.validateSensorThreshold(USER_ID, CULTIVATION_ID, request);

        // Then
        assertThat(response.isValid()).isFalse(); // 범위 초과로 거절(false) 반환
        assertThat(response.message()).contains("권장 범위는 15"); // 추천 범위를 포함한 피드백인지 확인

        // AI 호출 없었음 확인
        verify(geminiChatClient, times(0)).prompt();
    }

    @Test
    @DisplayName("캐시 파싱 에러(Exception) 발생 시 catch 블록 통과 후 AI 재호출 검증")
    void validate_CacheParsingError() throws Exception {
        // 캐시에 깨진 JSON 문자열이 들어있다고 가정
        given(hashOps.get(anyString(), anyString())).willReturn("invalid_json_string");
        // objectMapper가 파싱하다가 에러를 던지도록 설정
        given(objectMapper.readValue(anyString(), eq(AiSensorResultDto.class))).willThrow(new RuntimeException("파싱 에러"));

        // 에러를 무시하고 AI 호출 준비를 하는지 검증하기 위한 가짜 AI 응답
        AiSensorResultDto mockAiResponse = new AiSensorResultDto(
                List.of(new SensorRangeDto(10L, BigDecimal.valueOf(15), BigDecimal.valueOf(20))),
                List.of(new SensorRangeDto(10L, BigDecimal.valueOf(10), BigDecimal.valueOf(18))));
        given(geminiChatClient.prompt().system(any(Consumer.class)).user(anyString()).call().entity(AiSensorResultDto.class))
                .willReturn(mockAiResponse);
        given(objectMapper.writeValueAsString(any())).willReturn("{}");

        SensorValidationRequest request = new SensorValidationRequest(
                10L, "TEMPERATURE", "°C", BigDecimal.valueOf(16), BigDecimal.valueOf(19)
        );

        SensorValidationResponse response = sensorValidationService.validateSensorThreshold(USER_ID, CULTIVATION_ID, request);

        // 캐시 에러를 삼키고 무사히 AI를 호출해서 통과(true)를 반환하는지 검증
        assertThat(response.isValid()).isTrue();
    }

    @Test
    @DisplayName("AI 응답에 해당 센서 타입이 없을 때 RuntimeException 발생 검증")
    void validate_AiAnalysisFailed() throws RuntimeException {
        given(hashOps.get(anyString(), anyString())).willReturn(null);

        // AI가 빈 배열을 반환하거나, 요청한 10번 센서가 아닌 다른 센서 결과만 줬다고 가정
        AiSensorResultDto emptyAiResponse = new AiSensorResultDto(List.of(), List.of());
        given(geminiChatClient.prompt().system(any(Consumer.class)).user(anyString()).call().entity(AiSensorResultDto.class))
                .willReturn(emptyAiResponse);

        SensorValidationRequest request = new SensorValidationRequest(
                10L, "TEMPERATURE", "°C", BigDecimal.valueOf(16), BigDecimal.valueOf(19)
        );

        // findFirst().orElseThrow() 에 걸려 AiAnalysisFailedException이 터지는지 검증
        org.junit.jupiter.api.Assertions.assertThrows(AiAnalysisFailedException.class, () -> {
            sensorValidationService.validateSensorThreshold(USER_ID, CULTIVATION_ID, request);
        });
    }

    @Test
    @DisplayName("수확기(HARVEST) 모드일 때 -> harvestPhase 권장 범위 기준으로 검증 통과(true) 확인")
    void validate_HarvestMode_ValidInput() throws Exception {
        // Given
        // 1. 재배지 상태가 "HARVEST" 모드로 반환되도록 모킹
        given(cultivationClient.getCultivation(USER_ID, CULTIVATION_ID))
                .willReturn(new CultivationDetailResponse(CULTIVATION_ID, MUSHROOM_ID, "ACTIVE", "HARVEST", LocalDateTime.now()));

        // 2. 캐시에 재배기(20~24도)와 수확기(15~18도) 데이터가 모두 들어있다고 가정
        given(hashOps.get(REDIS_KEY, "10")).willReturn("{\"mocked\": \"json\"}");

        AiSensorResultDto cachedDto = new AiSensorResultDto(
                List.of(new SensorRangeDto(10L, BigDecimal.valueOf(20), BigDecimal.valueOf(24))), // 재배기
                List.of(new SensorRangeDto(10L, BigDecimal.valueOf(15), BigDecimal.valueOf(18)))  // 수확기 (타겟)
        );
        given(objectMapper.readValue(anyString(), eq(AiSensorResultDto.class))).willReturn(cachedDto);

        // 3. 유저가 16~17도 입력 (재배기 20~24도 기준이면 실패하지만, 수확기 15~18도 기준이므로 성공해야 함)
        SensorValidationRequest request = new SensorValidationRequest(
                10L, "TEMPERATURE", "°C", BigDecimal.valueOf(16), BigDecimal.valueOf(17)
        );

        // When
        SensorValidationResponse response = sensorValidationService.validateSensorThreshold(USER_ID, CULTIVATION_ID, request);

        // Then
        assertThat(response.isValid()).isTrue();
        assertThat(response.recommendedMin()).isEqualByComparingTo(BigDecimal.valueOf(15));
        assertThat(response.recommendedMax()).isEqualByComparingTo(BigDecimal.valueOf(18));
        assertThat(response.message()).contains("적절한 임계값입니다");
    }
}
