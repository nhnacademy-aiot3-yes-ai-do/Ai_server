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
import org.springframework.test.util.ReflectionTestUtils;
import site.yesaido.ai_server.client.CultivationClient;
import site.yesaido.ai_server.dto.ai.mush_summary.MushroomCsvDto;
import site.yesaido.ai_server.dto.cultivation.CultivationDetailResponse;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import site.yesaido.ai_server.dto.front.AiSensorResultDto;
import site.yesaido.ai_server.dto.front.SensorRangeDto;
import site.yesaido.ai_server.dto.front.SensorRecommendationRequest;
import site.yesaido.ai_server.dto.front.SensorValidationResponse;
import site.yesaido.ai_server.reader.MushCsvReader;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SensorRecommendationServiceTest {
    // 메서드 체이닝을 한 줄로 모킹해주는 옵션(객체에 . 찍고 들어가서 반환하는 것들 자동으로 가짜(Mock) 무한 생성)
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;
    @Mock private CultivationClient cultivationClient;
    @Mock private ObjectMapper objectMapper;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private HashOperations<String, String, String> hashOps;
    @Mock private MushCsvReader mushCsvReader;

    @InjectMocks
    private SensorRecommendationService sensorRecommendationService;
    // 공통 변수
    private static final Long USER_ID = 1L;
    private static final Long CULTIVATION_ID = 100L;
    private static final Long MUSHROOM_ID = 1L;
    private static final String REDIS_KEY = "mushroom:sensor:recommendation:1";


    @BeforeEach
    void setup(){ // @Value에 값 테스트에서 안 넣어버리는 문제 해결 위해 가짜 파일 채워줌
        ReflectionTestUtils.setField(sensorRecommendationService, "systemResource", new ByteArrayResource("system prompt".getBytes()));
        ReflectionTestUtils.setField(sensorRecommendationService, "userResource", new ByteArrayResource("user prompt".getBytes()));

        given(cultivationClient.getCultivation(USER_ID, CULTIVATION_ID))
                .willReturn(new CultivationDetailResponse(CULTIVATION_ID, MUSHROOM_ID, "ACTIVE", "AUTO", LocalDateTime.now()));

        given(mushCsvReader.readMushroomCsv())
                .willReturn(List.of(new MushroomCsvDto(MUSHROOM_ID, "느타리버섯", "title", "content")));

        // 공통 모킹 2: Redis 해시 오퍼레이션 강제 타입 주입 (컴파일 에러 방지)
        given(redisTemplate.<String, String>opsForHash()).willReturn(hashOps);
    }

    @Test
    @DisplayName("캐시 없음 + 정상 범위 입력 -> AI 호출 후 통과(true) 응답")
    void validate_CacheMiss_ValidInput() throws Exception{
        // Given
        // 유저가 16~19도를 입력함
        SensorRecommendationRequest request = new SensorRecommendationRequest(
                CULTIVATION_ID, 10L, "TEMPERATURE", "°C", BigDecimal.valueOf(16), BigDecimal.valueOf(19)
        );

        given(hashOps.get(anyString(), anyString())).willReturn(null); // 캐시 비어있음 설정

        // AI가 15~20도를 추천하도록 설정
        AiSensorResultDto mockAiResponse = new AiSensorResultDto(
                List.of(new SensorRangeDto(10L, BigDecimal.valueOf(15), BigDecimal.valueOf(20))),
                List.of(), List.of()
        );
        given(chatClient.prompt().system(any(Consumer.class)).user(anyString()).call().entity(AiSensorResultDto.class))
                .willReturn(mockAiResponse);

        given(objectMapper.writeValueAsString(any())).willReturn("{\"mocked\":\"json\"}");

        // When
        SensorValidationResponse response = sensorRecommendationService.validateSensorThreshold(USER_ID, request);

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
        SensorRecommendationRequest request = new SensorRecommendationRequest(
                CULTIVATION_ID, 10L, "TEMPERATURE", "°C", BigDecimal.valueOf(10), BigDecimal.valueOf(30)
        );

        // 캐시에 이미 15~20도 정답이 있다고 설정
        given(hashOps.get(REDIS_KEY, "10")).willReturn("{\"mocked\": \"json\"}");

        AiSensorResultDto cachedDto = new AiSensorResultDto(
                List.of(new SensorRangeDto(10L, BigDecimal.valueOf(15), BigDecimal.valueOf(20))),
                List.of(), List.of()
        );
        given(objectMapper.readValue(anyString(), eq(AiSensorResultDto.class))).willReturn(cachedDto);

        // When
        SensorValidationResponse response = sensorRecommendationService.validateSensorThreshold(USER_ID, request);

        // Then
        assertThat(response.isValid()).isFalse(); // 범위 초과로 거절(false) 반환
        assertThat(response.message()).contains("권장 범위는 15"); // 추천 범위를 포함한 피드백인지 확인

        // AI 호출 없었음 확인
        verify(chatClient, times(0)).prompt();
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
                List.of(new SensorRangeDto(10L, BigDecimal.valueOf(15), BigDecimal.valueOf(20))), List.of(), List.of()
        );
        given(chatClient.prompt().system(any(java.util.function.Consumer.class)).user(anyString()).call().entity(AiSensorResultDto.class))
                .willReturn(mockAiResponse);
        given(objectMapper.writeValueAsString(any())).willReturn("{}");

        SensorRecommendationRequest request = new SensorRecommendationRequest(
                CULTIVATION_ID, 10L, "TEMPERATURE", "°C", BigDecimal.valueOf(16), BigDecimal.valueOf(19)
        );

        SensorValidationResponse response = sensorRecommendationService.validateSensorThreshold(USER_ID, request);

        // 캐시 에러를 삼키고 무사히 AI를 호출해서 통과(true)를 반환하는지 검증
        assertThat(response.isValid()).isTrue();
    }

    @Test
    @DisplayName("AI 응답에 해당 센서 타입이 없을 때 RuntimeException 발생 검증")
    void validate_AiAnalysisFailed() throws RuntimeException {
        given(hashOps.get(anyString(), anyString())).willReturn(null);

        // AI가 빈 배열을 반환하거나, 요청한 10번 센서가 아닌 다른 센서 결과만 줬다고 가정
        AiSensorResultDto emptyAiResponse = new AiSensorResultDto(List.of(), List.of(), List.of());
        given(chatClient.prompt().system(any(java.util.function.Consumer.class)).user(anyString()).call().entity(AiSensorResultDto.class))
                .willReturn(emptyAiResponse);

        SensorRecommendationRequest request = new SensorRecommendationRequest(
                CULTIVATION_ID, 10L, "TEMPERATURE", "°C", BigDecimal.valueOf(16), BigDecimal.valueOf(19)
        );

        // findFirst().orElseThrow() 에 걸려 강제로 RuntimeException이 터지는지 검증
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> {
            sensorRecommendationService.validateSensorThreshold(USER_ID, request);
        });
    }
}
