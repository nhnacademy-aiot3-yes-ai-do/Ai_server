package site.yesaido.ai_server.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Getter
@Component
public class PromptProperties {
    // Gemini API 키
    @Value("${spring.ai.google.genai.api-key}")
    private String geminiApiKeys;

    // AI 챗봇
    @Value("classpath:prompts/chat_system.st")
    private Resource chatSystemPrompt;

    // 버섯 가이드라인 요약 (시스템 / 유저)
    @Value("classpath:prompts/mush_guide_system.st")
    private Resource mushGuideSystemPrompt;

    @Value("classpath:prompts/mush_guide_user.st")
    private Resource mushGuideUserPrompt;

    // 수확 성과 인사이트 분석 (시스템 / 유저)
    @Value("classpath:prompts/insight_summary_system.st")
    private Resource insightSummarySystemPrompt;

    @Value("classpath:prompts/insight_summary_user.st")
    private Resource insightSummaryUserPrompt;

    // 센서 임계값 검증 (시스템 / 유저)
    @Value("classpath:prompts/sensor_validation_system.st")
    private Resource sensorValidationSystemPrompt;

    @Value("classpath:prompts/sensor_validation_user.st")
    private Resource sensorValidationUserPrompt;
}
