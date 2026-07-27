package site.yesaido.ai_server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MushService {
    private final ChatClient chatClient;

    public record Recipe(String name, String instructions) {}

    public record OptimalConditions(
            String temperature, String humidity, String co2, String illuminance
    ) {}

    public record AiEvaluation(
            int difficultyLevel,       // 초보자 난이도 (1: 매우 쉬움 ~ 5: 매우 어려움)
            int growthSpeed,           // 성장 속도 (1: 매우 느림 ~ 5: 수확이 아주 빠름)
            String sensitivity,        // 가장 주의해야 할 환경 요인 (예: "건조함에 매우 취약")
            String aiStrategy          // AI의 1:1 맞춤형 재배 컨설팅 (3문장)
    ) {}

    public record MushroomGuideResponse(
            AiEvaluation evaluation,        // 뱃지 및 AI 재배 전략
            String summary,                 // 기본 정보 요약
            String caution,                 // 치명적 환경 경고
            String tip,                     // 수확/보관 꿀팁
            OptimalConditions conditions,   // 센서 세팅용 최적 환경
            List<Recipe> recipes            // 요리법
    ) {}

    // 결과 Redis에 저장해 다음엔 AI 거치지 않고 꺼낼 수 있게 해줌
    @Cacheable(value = "ai:mushroom:guide", key = "#mushroomName")
    public MushroomGuideResponse generateRealDataGuide(String mushroomName, String combinedData) {


        String systemPrompt = """
            당신은 스마트팜 버섯 재배 최고 권위자이자 데이터 분석가입니다.
            제공되는 [수집된 버섯 데이터 모음]을 바탕으로, 초보 농부가 대시보드에서 한눈에 볼 수 있는 직관적인 JSON 데이터를 생성하세요.
            
            [핵심 가공 지침 - 컨설팅 수준의 분석 요구]
            - evaluation: 이 버섯의 종합 평가를 내리세요.
              * difficultyLevel: 초보자가 집이나 소규모 시설에서 키우기 얼마나 어려운지 (1=매우쉬움 ~ 5=매우어려움). 정수만 입력.
              * growthSpeed: 균사 배양부터 수확까지의 성장 속도 및 만족도 (1=매우느림 ~ 5=아주빠름). 정수만 입력.
              * sensitivity: 이 버섯이 가장 취약한 환경 요인 (예: "환기 및 습도 저하에 매우 민감"). 15자 이내.
              * aiStrategy: 제공된 데이터를 종합하여, 초보자가 이 버섯을 성공적으로 수확하기 위해 매일 체크해야 할 1순위 행동 지침을 친절하게 3문장 작성하세요.
            
            - summary: 외형과 핵심 효능을 합쳐 2문장으로 알기 쉽게 요약하세요.
            - caution: 환경 제어(온/습도/환기 등) 실패 시 발생하는 가장 끔찍한 결과(기형 등)를 1문장으로 강하게 경고하세요.
            - tip: 수확 후 위생 처리와 집에서 오랫동안 싱싱하게 보관하는 꿀팁을 결합하여 2문장으로 작성하세요.
            - conditions: 데이터에 명시된 온도, 습도, 조도를 파싱하세요. 단, 이산화탄소(CO2) 농도는 원문에 없으므로 당신의 농업 지식을 바탕으로 '이 특정 버섯 품종'의 생육에 가장 알맞은 정확한 CO2 ppm 범위를 반드시 추론하여 기입하세요. (품종별로 CO2 요구량이 다르다는 점을 명심하세요!)
            - recipes: 누구나 집에서 쉽게 따라 할 수 있는 맛있는 한국식 레시피 2개를 제안하세요. 조리법에는 "간장 2큰술", "물 500ml"처럼 정확한 정량 계량 수치를 반드시 포함하여 상세히 서술하세요.
            """;

        String userDataPrompt = String.format("""
            대상 품종: %s
            
            [수집된 버섯 데이터 모음]
            %s
            """, mushroomName, combinedData);

        log.info("'{}' 가이드라인 및 최적 제어 환경 요약 구조화 출력(Gemini Flash) 요청 시작...", mushroomName);

        return chatClient.prompt()
                .system(systemPrompt)
                .user(userDataPrompt)
                .call()
                .entity(MushroomGuideResponse.class);
    }
}
