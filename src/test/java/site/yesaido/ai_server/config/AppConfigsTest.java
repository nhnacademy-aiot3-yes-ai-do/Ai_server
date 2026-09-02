package site.yesaido.ai_server.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class AppConfigsTest {

    @Test
    @DisplayName("ChatClientConfig 빈 생성 검증")
    void geminiChatClientBean() {
        ChatClientConfig config = new ChatClientConfig();
        PromptProperties props = new PromptProperties();
        ReflectionTestUtils.setField(props, "geminiApiKeys", "test-key-1, test-key-2");

        ChatClient client = config.geminiChatClient(props);
        assertThat(client).isNotNull();
    }

    @Test
    @DisplayName("ImageDownloadClientConfig RestClient 빈 생성 검증")
    void imageDownloadRestClientBean() {
        ImageDownloadClientConfig config = new ImageDownloadClientConfig();
        RestClient restClient = config.imageDownloadRestClient();

        assertThat(restClient).isNotNull();
    }
}
