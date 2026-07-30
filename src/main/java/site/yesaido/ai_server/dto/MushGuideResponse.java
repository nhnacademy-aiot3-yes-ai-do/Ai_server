package site.yesaido.ai_server.dto;

import java.util.List;

public record MushGuideResponse(
        AiEvaluationDto evaluation,        // 뱃지 및 AI 재배 전략
        String summary,                 // 기본 정보 요약
        String caution,                 // 치명적 환경 경고
        String tip,                     // 수확/보관 꿀팁
        EnvironmentConditionInfo conditions,   // 센서 세팅용 최적 환경
        List<RecipeDto> recipes            // 요리법
) {
}
