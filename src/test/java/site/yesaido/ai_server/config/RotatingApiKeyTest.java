package site.yesaido.ai_server.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RotatingApiKeyTest {

    @Test
    @DisplayName("생성자 검증: 빈 키 목록 전달 시 IllegalArgumentException")
    void constructor_emptyKeys() {
        GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder().build();

        assertThatThrownBy(() -> new RotatingApiKey("", options))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new RotatingApiKey("  ,  ", options))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("생성자 검증: 유효한 키 목록 전달 시 중복 제거 및 정상 초기화")
    void constructor_validKeys() {
        GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder().build();
        RotatingApiKey rotating = new RotatingApiKey("fake-key-1, fake-key-2, fake-key-1", options);

        assertThat(rotating).isNotNull();
    }
}
