package site.yesaido.ai_server.dto;

public record AiEvaluationDto(
        int difficultyLevel,       // 초보자 난이도 (1: 매우 쉬움 ~ 5: 매우 어려움)
        int growthSpeed,           // 성장 속도 (1: 매우 느림 ~ 5: 수확이 아주 빠름)
        String sensitivity,        // 가장 주의해야 할 환경 요인 (예: "건조함에 매우 취약")
        String aiStrategy          // AI의 1:1 맞춤형 재배 컨설팅 (3문장)
) {
}
