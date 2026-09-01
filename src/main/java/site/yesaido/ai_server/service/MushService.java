package site.yesaido.ai_server.service;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import site.yesaido.ai_server.client.CultivationClient;
import site.yesaido.ai_server.config.PromptProperties;
import site.yesaido.ai_server.dto.ai.mush_summary.EnvironmentConditionInfo;
import site.yesaido.ai_server.dto.ai.mush_summary.MushGuideResponse;
import site.yesaido.ai_server.dto.ai.mush_summary.MushroomCsvDto;
import site.yesaido.ai_server.dto.ai.mush_summary.SensorRange;
import site.yesaido.ai_server.dto.client.mushroom_reference.MushroomReferenceInfoListResponse;
import site.yesaido.ai_server.dto.client.mushroom_reference.MushroomReferenceInfoResponse;
import site.yesaido.ai_server.dto.client.mushroom_reference.MushroomReferenceThresholdInfoResponse;
import site.yesaido.ai_server.exception.MushDataNotFoundException;
import site.yesaido.ai_server.reader.MushCsvReader;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
@NoArgsConstructor(access = AccessLevel.PROTECTED, force = true) // CGLIB @Cacheable 프록시 전용 생성자
public class MushService{
    private final ChatClient chatClient;
    private final MushCsvReader mushCsvReader;
    private final CultivationClient cultivationClient;
    private final PromptProperties promptProperties;

    // Spring AI의 PromptTemplate 기능 사용하는 방식으로 변경 <- 프롬프트를 안전하고 동적으로 관리할 수 있음
    @Cacheable(value = "ai:mushroom", key = "#mushroomId + ':guide'")
    public MushGuideResponse generateRealDataGuide(Long mushroomId) {
        if (chatClient == null || mushCsvReader == null) {
            log.warn("chatClient 또는 mushCsvReader가 null입니다");
            throw new IllegalStateException("chatClient 또는 mushCsvReader가 null입니다");
        }
        log.info("캐시가 만료되어 다시 {}번 데이터 요약을 시작합니다.", mushroomId);

        String mushroomName = "";
        StringBuilder combinedData = new StringBuilder();

        List<MushroomCsvDto> csvDtoList = mushCsvReader.readMushroomCsv();
        for (MushroomCsvDto dto : csvDtoList) {
            if(dto.mushroomId().equals(mushroomId)) {
                mushroomName = dto.mushroomName();
                combinedData.append("[").append(dto.title()).append("] ").append(dto.content()).append("\n");
            }
        }
        // 뽑아낸 데이터 없으면 에러 발생
        if(combinedData.isEmpty()){
            log.error("ID {}에 해당하는 버섯 학습 데이터가 없습니다.",mushroomId);
            throw new MushDataNotFoundException(mushroomId);
        }

        final String finalMushroomName = mushroomName;
        final String finalCombinedData = combinedData.toString();


        log.info("'{}' 가이드라인 및 최적 제어 환경 요약 구조화 출력(Gemini Flash) 요청 시작...", finalMushroomName);

        // AI에게 임계값까지 추천 받던것 제거(이제는 요약, 난이도, 팁, 레시피만 받아옴)
        MushGuideResponse aiTextResponse = chatClient.prompt()
                .system(promptProperties.getMushGuideSystemPrompt())
                .user(u -> u.text(promptProperties.getMushGuideUserPrompt())
                        .param("mushroomId", mushroomId)
                        .param("mushroomName", finalMushroomName)
                        .param("combinedData", finalCombinedData))
                .call()
                .entity(MushGuideResponse.class);

        // Cultivation DB에서 재배기(GROWTH) 및 수확기(HARVEST) 센서(온/습/CO2/조도) 기준값을 꺼내옴
        EnvironmentConditionInfo cultivationCondition = getDbEnvironmentCondition(mushroomId, "GROWTH");
        EnvironmentConditionInfo harvestCondition = getDbEnvironmentCondition(mushroomId, "HARVEST");

        // AI 요약 + cultivation DB의 기준값 조립하여 리턴
        return new MushGuideResponse(
                mushroomId,
                finalMushroomName,
                aiTextResponse.evaluation(),
                aiTextResponse.summary(),
                aiTextResponse.caution(),
                aiTextResponse.tip(),
                cultivationCondition,
                harvestCondition,
                aiTextResponse.recipes()
        );
    }

    // Cultivation DB에서 해당 버섯 및 생육 단계(GROWTH/HARVEST)의 필수 4종 센서 기준값 파싱
    private EnvironmentConditionInfo getDbEnvironmentCondition(Long mushroomId, String phase) {
        List<MushroomReferenceThresholdInfoResponse> thresholds = fetchThresholds(mushroomId);

        SensorRange temperature = findRange(thresholds, phase, "TEMPERATURE");
        SensorRange humidity = findRange(thresholds, phase, "HUMIDITY");
        SensorRange co2 = findRange(thresholds, phase, "CO2");
        SensorRange light = findRange(thresholds, phase, "LIGHT");

        if (temperature == null || humidity == null || co2 == null || light == null) {
            log.error("ID {} 버섯의 [{}] 단계 필수 4종 센서 기준값이 Cultivation DB에 온전히 등록되어 있지 않습니다.", mushroomId, phase);
            throw new MushDataNotFoundException(mushroomId);
        }
        return new EnvironmentConditionInfo(temperature, humidity, co2, light);
    }

    private List<MushroomReferenceThresholdInfoResponse> fetchThresholds(Long mushroomId) { // Cultivation DB에서 버섯 기준 임계값 리스트 조회
        MushroomReferenceInfoListResponse refList = cultivationClient.getMushroomReference();
        if (refList == null || refList.mushroomReferenceInfoResponses() == null) {
            throw new MushDataNotFoundException(mushroomId);
        }

        return refList.mushroomReferenceInfoResponses().stream()
                .filter(m -> m.id() == mushroomId)
                .findFirst()
                .map(MushroomReferenceInfoResponse::thresholdInfoResponses)
                .orElseThrow(() -> new MushDataNotFoundException(mushroomId));
    }

    // 센서 타입(targetType) 생육 단계(phase) SensorRange 추출
    private SensorRange findRange(List<MushroomReferenceThresholdInfoResponse> thresholds, String phase, String targetType) {
        return thresholds.stream()
                .filter(t -> t.sensorType() != null && t.thresholdMin() != null && t.thresholdMax() != null)
                .filter(t -> t.thresholdType() == null || phase.equalsIgnoreCase(t.thresholdType()))
                .filter(t -> targetType.equalsIgnoreCase(t.sensorType().type()))
                .findFirst()
                .map(t -> {
                    double min = Math.min(t.thresholdMin().doubleValue(), t.thresholdMax().doubleValue());
                    double max = Math.max(t.thresholdMin().doubleValue(), t.thresholdMax().doubleValue());
                    return new SensorRange(min, max);
                })
                .orElse(null);
    }
}
