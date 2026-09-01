package site.yesaido.ai_server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import site.yesaido.ai_server.client.CultivationClient;
import site.yesaido.ai_server.config.PromptProperties;
import site.yesaido.ai_server.dto.ai.mush_summary.MushroomCsvDto;
import site.yesaido.ai_server.dto.client.cultivation.CultivationDetailResponse;
import site.yesaido.ai_server.dto.ai.sensor_validation.AiSensorResultDto;
import site.yesaido.ai_server.dto.ai.sensor_validation.SensorRangeDto;
import site.yesaido.ai_server.dto.ai.sensor_validation.SensorValidationRequest;
import site.yesaido.ai_server.dto.ai.sensor_validation.SensorValidationResponse;
import site.yesaido.ai_server.dto.client.mushroom_reference.MushroomReferenceInfoListResponse;
import site.yesaido.ai_server.dto.client.mushroom_reference.MushroomReferenceThresholdInfoResponse;
import site.yesaido.ai_server.exception.AiAnalysisFailedException;
import site.yesaido.ai_server.exception.MushDataNotFoundException;
import site.yesaido.ai_server.reader.MushCsvReader;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SensorValidationService {
    private final ChatClient geminiChatClient;
    private final CultivationClient cultivationClient;
    private final ObjectMapper objectMapper; // JAVA <-> JSON 변환기
    /**
     * 센서 임계값 AI 추천 결과를 캐싱하기 위한 RedisTemplate
     * - 이유: 동일한 버섯/센서에 대한 AI API 중복 호출을 막아 비용과 응답 시간을 획기적으로 줄임
     * - 구조: Redis Hash를 활용하여 [버섯 ID(Key) -> 센서 ID(HashKey) -> AI결과(Value)] 구조로 미지의 센서까지 동적 저장
     */
    private final StringRedisTemplate redisTemplate;
    private final MushCsvReader mushCsvReader;
    private final PromptProperties promptProperties;
    private static final String REDIS_KEY =  "mushroom:sensor:validation:";


    public SensorValidationResponse validateSensorThreshold(Long userId, Long cultivationId, SensorValidationRequest request) {
        CultivationDetailResponse cultivation = cultivationClient.getCultivation(userId, cultivationId);

        Long mushroomId = cultivation.mushroomId();
        boolean isHarvestMode = "HARVEST".equalsIgnoreCase(cultivation.mode());

        BigDecimal optimalMin;
        BigDecimal optimalMax;

        String targetMode = isHarvestMode ? "HARVEST" : "GROWTH"; // 재배지 모드 확인
        // targetMode를 함께 넘겨서 DB에서 해당 단계의 임계값 매칭
        Optional<MushroomReferenceThresholdInfoResponse> matchedDbThreshold = findDbThreshold(mushroomId, request.sensorTypeId(), targetMode);

        if (matchedDbThreshold.isPresent()) {
            // cultivation DB에 등록되어 있는 필수 센서(온도, 습도, co2, 조도)는 AI 호출하지 않고 바로 가져와서 사용
            MushroomReferenceThresholdInfoResponse dbStandard = matchedDbThreshold.get();
            optimalMin = dbStandard.thresholdMin();
            optimalMax = dbStandard.thresholdMax();
            log.info("[센서 검증] Cultivation DB 기준값 적용 (sensorTypeId: {}, 범위: {} ~ {})", request.sensorTypeId(), optimalMin, optimalMax);
        }
        else {
            // cultivation DB에 없는 신규 센서는 AI 호출 후 Redis 캐싱
            log.info("[센서 검증] DB에 없는 신규 센서(ID:{}, 이름:{})입니다. AI 분석을 실행합니다.", request.sensorTypeId(), request.sensorTypeName());
            SensorRangeDto aiOptimalRange = getAiRecommendedRange(cultivation, request, isHarvestMode);
            optimalMin = aiOptimalRange.min();
            optimalMax = aiOptimalRange.max();
        }

        if(optimalMin.compareTo(optimalMax) >= 0) {
            optimalMax = optimalMin.add(new BigDecimal("0.5"));
        }

        boolean isValid = true;
        String feedbackMessage = "적절한 임계값입니다. 센서를 등록하셔도 좋습니다!";

        // 유저 입력값이 추천 범위를 벗어났는지 확인 (느슨한 검증)
        if (request.userMin().compareTo(optimalMin) < 0 || request.userMax().compareTo(optimalMax) > 0) {
            String mushroomName = findMushroomName(mushroomId);
            isValid = false;
            feedbackMessage = String.format("입력하신 값이 권장 범위를 벗어납니다. %s의 권장 범위는 %s ~ %s 입니다.", mushroomName, optimalMin, optimalMax);
        }
        return new SensorValidationResponse(isValid, feedbackMessage, optimalMin, optimalMax);
    }

    // Cultivation DB에서 버섯, 센서 타입 일치하는 기준 값 찾기
    private Optional<MushroomReferenceThresholdInfoResponse> findDbThreshold(Long mushroomId, Long sensorTypeId, String targetMode) {
        try {
            MushroomReferenceInfoListResponse response = cultivationClient.getMushroomReference();
            if (response == null || response.mushroomReferenceInfoResponses() == null) {
                return Optional.empty();
            }

            return response.mushroomReferenceInfoResponses().stream()
                    .filter(m -> m.id() == mushroomId)
                    .findFirst()
                    .flatMap(m -> m.thresholdInfoResponses().stream()
                            .filter(t -> t.sensorType() != null
                                    && t.sensorType().id() == sensorTypeId
                                    && targetMode.equalsIgnoreCase(t.thresholdType())) // 재배기,수확기 모드 일치 확인
                            .findFirst());
        } catch (Exception e) {
            log.warn("Cultivation DB 기준 임계값 조회 실패. AI 분석으로 대체합니다. 원인: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // 신규 센서용 AI 추천 및 Redis 캐싱 로직
    private SensorRangeDto getAiRecommendedRange(CultivationDetailResponse cultivation, SensorValidationRequest request, boolean isHarvestMode) {
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
            String combinedData = findMushroomContext(cultivation.mushroomId());
            aiData = callAiForRecommendations(mushroomName, combinedData, List.of(sensorIdStr + " : " + sensorInfo));
            cacheAiResultBySensor(redisKey, hashOps, aiData);
        }

        List<SensorRangeDto> targetPhaseList = isHarvestMode ? aiData.harvestPhase() : aiData.vegetativePhase();

        return targetPhaseList.stream()
                .filter(s -> s.sensorTypeId().equals(request.sensorTypeId()))
                .findFirst()
                .orElseThrow(() -> new AiAnalysisFailedException(request.sensorTypeId()));
    }

    private AiSensorResultDto callAiForRecommendations(String mushroomName, String combinedData, List<String> sensorList) {
        String sensorListString = String.join("\n", sensorList);

        PromptTemplate userPromptTemplate = new PromptTemplate(promptProperties.getSensorValidationUserPrompt());
        String userMessage = userPromptTemplate.render(Map.of(
                "mushroomName", mushroomName,
                "combinedData", combinedData,
                "sensorList", sensorListString
        ));

        try { // gemini로 1차 시도 후 실패하면 올라마 사용해서라도 답변 나오게 수정
            log.info("[AI 임계값 분석] 1차 시도: Gemini API 호출 시작...");
            return geminiChatClient.prompt()
                    .system(sys -> sys.text(promptProperties.getSensorValidationSystemPrompt()))
                    .user(userMessage)
                    .call()
                    .entity(AiSensorResultDto.class);
        } catch (Exception e) {
            log.error("[AI 임계값 분석] Gemini 호출 실패. 원인: {}", e.getMessage(), e);
            throw new AiAnalysisFailedException(0L);
        }
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

    private String findMushroomContext(Long mushroomId) { // CSV 텍스트 추출 메서드
        List<MushroomCsvDto> csvDtoList = mushCsvReader.readMushroomCsv();
        StringBuilder sb = new StringBuilder();
        for (MushroomCsvDto dto : csvDtoList) {
            if (dto.mushroomId().equals(mushroomId)) {
                sb.append("[").append(dto.title()).append("] ").append(dto.content()).append("\n");
            }
        }
        if (sb.isEmpty()) {
            throw new MushDataNotFoundException(mushroomId);
        }
        return sb.toString();
    }
}
