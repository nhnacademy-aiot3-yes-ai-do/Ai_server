package site.yesaido.ai_server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import site.yesaido.ai_server.client.CultivationClient;
import site.yesaido.ai_server.dto.ai.mush_summary.MushroomCsvDto;
import site.yesaido.ai_server.dto.cultivation.CultivationDetailResponse;
import site.yesaido.ai_server.dto.front.AiSensorResultDto;
import site.yesaido.ai_server.dto.front.SensorRangeDto;
import site.yesaido.ai_server.dto.front.SensorValidationRequest;
import site.yesaido.ai_server.dto.front.SensorValidationResponse;
import site.yesaido.ai_server.exception.AiAnalysisFailedException;
import site.yesaido.ai_server.exception.MushDataNotFoundException;
import site.yesaido.ai_server.reader.MushCsvReader;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SensorValidationService {
    private final ChatClient chatClient;
    private final CultivationClient cultivationClient;
    private final ObjectMapper objectMapper; // JAVA <-> JSON 변환기
    /**
     * 센서 임계값 AI 추천 결과를 캐싱하기 위한 RedisTemplate
     * - 이유: 동일한 버섯/센서에 대한 AI API 중복 호출을 막아 비용과 응답 시간을 획기적으로 줄임
     * - 구조: Redis Hash를 활용하여 [버섯 ID(Key) -> 센서 ID(HashKey) -> AI결과(Value)] 구조로 미지의 센서까지 동적 저장
     */
    private final StringRedisTemplate redisTemplate;
    private final MushCsvReader mushCsvReader;
    @Value("classpath:prompts/sensor_validation_system.st")
    private Resource systemResource;
    @Value("classpath:prompts/sensor_validation_user.st")
    private Resource userResource;
    private static final String REDIS_KEY =  "mushroom:sensor:validation:";

    public SensorValidationResponse validateSensorThreshold(Long userId, SensorValidationRequest request) {
        CultivationDetailResponse cultivation = cultivationClient.getCultivation(userId, request.cultivationId());
        String mushroomName = findMushroomName(cultivation.mushroomId());

        String redisKey = REDIS_KEY + cultivation.mushroomId();
        HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();

        String sensorIdStr = String.valueOf(request.sensorTypeId());
        String cachedResult = hashOps.get(redisKey, sensorIdStr);
        AiSensorResultDto aiData = null;

        if (cachedResult != null) {
            try {
                aiData = objectMapper.readValue(cachedResult, AiSensorResultDto.class);
            } catch (Exception e) {
                log.warn("Cache parsing error");
            }
        }

        if (aiData == null || aiData.vegetativePhase().isEmpty()) {
            String sensorInfo = request.sensorTypeName() + " (" + request.sensorUnit() + ")";
            aiData = callAiForRecommendations(mushroomName, List.of(sensorIdStr + " : " + sensorInfo));
            cacheAiResultBySensor(redisKey, hashOps, aiData);
        }

        SensorRangeDto optimal = aiData.vegetativePhase().stream()
                .filter(s -> s.sensorTypeId().equals(request.sensorTypeId()))
                .findFirst()
                .orElseThrow(() -> new AiAnalysisFailedException(request.sensorTypeId()));

        boolean isValid = true;
        String feedbackMessage = "적절한 임계값입니다. 센서를 등록하셔도 좋습니다!";

        // 유저 입력값이 추천 범위를 벗어났는지 확인 (느슨한 검증)
        if (request.userMin().compareTo(optimal.min()) < 0 || request.userMax().compareTo(optimal.max()) > 0) {
            isValid = false;
            feedbackMessage = String.format("입력하신 값이 권장 범위를 벗어납니다. %s의 권장 범위는 %s ~ %s 입니다.",
                    mushroomName, optimal.min(), optimal.max());
        }

        return new SensorValidationResponse(isValid, feedbackMessage, optimal.min(), optimal.max());
    }

    private AiSensorResultDto callAiForRecommendations(String mushroomName, List<String> sensorList) {
        String sensorListString = String.join("\n", sensorList);

        PromptTemplate userPromptTemplate = new PromptTemplate(userResource);
        String userMessage = userPromptTemplate.render(Map.of(
                "mushroomName", mushroomName,
                "sensorList", sensorListString
        ));

        return chatClient.prompt()
                .system(sys -> sys.text(systemResource))
                .user(userMessage)
                .call()
                .entity(AiSensorResultDto.class);
    }

    private void cacheAiResultBySensor(String redisKey, HashOperations<String, String, String> hashOps, AiSensorResultDto aiResponse)
    {
        List<Long> sensorIds = aiResponse.vegetativePhase().stream()
                .map(SensorRangeDto::sensorTypeId)
                .distinct()
                .toList();

        for (Long sensorId : sensorIds) {
            List<SensorRangeDto> veg = aiResponse.vegetativePhase().stream()
                    .filter(s -> s.sensorTypeId().equals(sensorId))
                    .toList();

            List<SensorRangeDto> harv = aiResponse.harvestPhase().stream()
                    .filter(s -> s.sensorTypeId().equals(sensorId))
                    .toList();

            AiSensorResultDto singleSensorResult = new AiSensorResultDto(veg, harv);

            try {
                String jsonResult = objectMapper.writeValueAsString(singleSensorResult);
                hashOps.put(redisKey, String.valueOf(sensorId), jsonResult);
            } catch (Exception e) {
                log.error("AI 결과 Redis 저장 전 JSON 직렬화 에러", e);
            }
        }
    }

    private String findMushroomName(Long mushroomId){ // 버섯 이름 찾기
        List<MushroomCsvDto> csvDtoList = mushCsvReader.readMushroomCsv();
        for(MushroomCsvDto dto : csvDtoList) {
            if(dto.mushroomId().equals(mushroomId)) {
                return dto.mushroomName();
            }
        }
        throw new MushDataNotFoundException(mushroomId);
    }
}
