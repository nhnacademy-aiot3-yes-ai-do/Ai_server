package site.yesaido.ai_server.dto.ai;

import java.util.List;

public record MushGuideResponse(
        Long mushroomId,                                // 버섯 Id
        String mushroomName,                            // 버섯 이름
        AiEvaluationDto evaluation,                     // 뱃지 및 AI 재배 전략
        String summary,                                 // 기본 정보 요약
        String caution,                                 // 치명적 환경 경고
        String tip,                                     // 수확/보관 꿀팁
        EnvironmentConditionInfo cultivationCondition,  // 재배기 센서 조건
        EnvironmentConditionInfo harvestCondition,      // 수확기 센서 조건
        List<RecipeDto> recipes                         // 요리법
) {}
