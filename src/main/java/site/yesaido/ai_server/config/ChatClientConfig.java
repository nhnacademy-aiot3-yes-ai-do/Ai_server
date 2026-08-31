package site.yesaido.ai_server.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class ChatClientConfig {

    @Bean
    @Primary
    public ChatClient geminiChatClient(@Qualifier("googleGenAiChatModel") ChatModel geminiModel) {
        return ChatClient.builder(geminiModel)
                .defaultOptions(GoogleGenAiChatOptions.builder()
                        .model("gemini-2.5-flash-lite")
                        .temperature(0.0))
                .build();
    }

    @Bean // Gemini 한도 초과 or 장애 발생했을 때 비상용
    public ChatClient ollamaChatClient(@Qualifier("ollamaChatModel") ChatModel ollamaModel) {
        return ChatClient.builder(ollamaModel).build();
    }
}
