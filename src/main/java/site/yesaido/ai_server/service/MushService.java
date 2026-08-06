package site.yesaido.ai_server.service;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import site.yesaido.ai_server.dto.*;
import site.yesaido.ai_server.exception.MushDataNotFoundException;
import site.yesaido.ai_server.reader.MushCsvReader;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = {@Autowired}) // 스프링에게 이 생성자로 의존성 주입하라고 명시
@NoArgsConstructor(access = AccessLevel.PROTECTED, force = true)
public class MushService{
    private final ChatClient chatClient;
    private final MushCsvReader mushCsvReader;
    private static final String SYSTEM_PROMPT = """
            당신은 스마트팜 버섯 재배 최고 권위자이자 데이터 분석가입니다.
            제공되는 [수집된 버섯 데이터 모음]을 바탕으로, 초보 농부가 대시보드에서 한눈에 볼 수 있는 직관적인 JSON 데이터를 생성하세요.
            [핵심 가공 지침 - 컨설팅 수준의 분석 요구]
            - mushroomId 및 mushroomName: 입력된 대상 버섯의 ID와 한글 이름을 정확히 기입하세요
            
            - evaluation: 이 버섯의 종합 평가를 내리세요.
                * difficultyLevel: 초보자가 집이나 소규모 시설에서 키우기 얼마나 어려운지 (1=매우쉬움 ~ 5=매우어려움). 정수만 입력.
                * growthSpeed: 균사 배양부터 수확까지의 성장 속도 및 만족도 (1=매우느림 ~ 5=아주빠름). 정수만 입력.
                * sensitivity: 이 버섯이 가장 취약한 환경 요인 (예: "환기 및 습도 저하에 매우 민감"). 15자 이내.
                * aiStrategy: 제공된 데이터를 종합하여, 초보자가 이 버섯을 성공적으로 수확하기 위해 매일 체크해야 할 1순위 행동 지침을 친절하게 정확히 3문장으로 작성하세요.
            
            - summary: 외형과 핵심 효능을 합쳐 정확히 2문장으로 알기 쉽게 요약하세요.
            - caution: 환경 제어(온/습도/환기 등) 실패 시 발생하는 가장 끔찍한 결과(기형 등)를 정확히 1문장으로 강하게 경고하세요.
            - tip: 수확 후 위생 처리와 집에서 오랫동안 싱싱하게 보관하는 꿀팁을 결합하여 정확히 2문장으로 작성하세요.

            - cultivationCondition (재배기 / 균사배양기 환경 센서 조건):
                * 재배기(배양 및 초기 생육 단계)의 최적 환경 조건(temperature, humidity, co2, light)의 min(최소) 및 max(최대) 수치(Double)를 기입하세요.
                * 온도(℃), 습도(%), CO2(ppm), 조도(lux) 단위의 "순수 숫자(Double)"만 min, max에 각각 부여하세요. (예: "temperature": { "min": 18.0, "max": 24.0 })
                * 원본 데이터에 수치가 누락되어 있거나 불분명하더라도, 당신의 버섯 농업 지식을 바탕으로 해당 버섯 [재배기]의 정확한 수치 범위를 추론하여 빈값(null) 없이 100% 완성하세요.

            - harvestCondition (수확기 / 자실체 결실기 환경 센서 조건):
                * 수확기(자실체 성장 및 수확 단계)의 최적 환경 조건(temperature, humidity, co2, light)의 min(최소) 및 max(최대) 수치(Double)를 기입하세요.
                * 온도(℃), 습도(%), CO2(ppm), 조도(lux) 단위의 "순수 숫자(Double)"만 min, max에 각각 부여하세요. (예: "temperature": { "min": 15.0, "max": 18.0 })
                * 원본 데이터에 수치가 누락되어 있거나 불분명하더라도, 당신의 버섯 농업 지식을 바탕으로 해당 버섯 [수확기]의 정확한 수치 범위를 추론하여 빈값(null) 없이 100% 완성하세요.
            
            - recipes: 누구나 집에서 쉽게 따라 할 수 있는 맛있는 한국식 레시피 2개를 제안하세요. 조리법에는 "간장 2큰술", "물 500ml"처럼 정확한 정량 계량 수치를 반드시 포함하여 상세히 서술하세요.
            """;
    private static final String USER_PROMPT_TEMPLATE = """
                        대상 버섯 ID: {mushroomId}
                        대상 품종: {mushroomName}
            
                        [수집된 버섯 데이터 모음]
                        {combinedData}
                        """;

    // 결과 Redis에 저장해 다음엔 AI 거치지 않고 꺼낼 수 있게 해줌
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

        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(u -> u.text(USER_PROMPT_TEMPLATE)
                        .param("mushroomId", mushroomId)
                        .param("mushroomName", finalMushroomName)
                        .param("combinedData", finalCombinedData))
                .call()
                .entity(MushGuideResponse.class);
    }
}
