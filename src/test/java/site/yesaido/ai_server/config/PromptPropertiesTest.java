package site.yesaido.ai_server.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class PromptPropertiesTest {

    @Test
    @DisplayName("PromptProperties 필드 주입 및 getter 정상 동작 검증")
    void promptProperties_getterTest() {
        PromptProperties properties = new PromptProperties();
        ByteArrayResource dummyResource = new ByteArrayResource("test prompt".getBytes());

        ReflectionTestUtils.setField(properties, "geminiApiKeys", "key1,key2");
        ReflectionTestUtils.setField(properties, "chatSystemPrompt", dummyResource);
        ReflectionTestUtils.setField(properties, "mushGuideSystemPrompt", dummyResource);
        ReflectionTestUtils.setField(properties, "mushGuideUserPrompt", dummyResource);
        ReflectionTestUtils.setField(properties, "insightSummarySystemPrompt", dummyResource);
        ReflectionTestUtils.setField(properties, "insightSummaryUserPrompt", dummyResource);
        ReflectionTestUtils.setField(properties, "sensorValidationSystemPrompt", dummyResource);
        ReflectionTestUtils.setField(properties, "sensorValidationUserPrompt", dummyResource);

        assertThat(properties.getGeminiApiKeys()).isEqualTo("key1,key2");
        assertThat(properties.getChatSystemPrompt()).isEqualTo(dummyResource);
        assertThat(properties.getMushGuideSystemPrompt()).isEqualTo(dummyResource);
        assertThat(properties.getMushGuideUserPrompt()).isEqualTo(dummyResource);
        assertThat(properties.getInsightSummarySystemPrompt()).isEqualTo(dummyResource);
        assertThat(properties.getInsightSummaryUserPrompt()).isEqualTo(dummyResource);
        assertThat(properties.getSensorValidationSystemPrompt()).isEqualTo(dummyResource);
        assertThat(properties.getSensorValidationUserPrompt()).isEqualTo(dummyResource);
    }
}
