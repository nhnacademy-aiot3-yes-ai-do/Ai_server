package site.yesaido.ai_server.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class ChatClientConfig {
    @Bean
    @Primary
    public ChatClient geminiChatClient(PromptProperties promptProperties) {
        GoogleGenAiChatOptions.Builder optionsBuilder = GoogleGenAiChatOptions.builder()
                .model("gemini-3.5-flash-lite")
                .temperature(0.0);

        RotatingApiKey rotatingModel = new RotatingApiKey(promptProperties.getGeminiApiKeys(), optionsBuilder.build());

        return ChatClient.builder(rotatingModel)
                .defaultOptions(optionsBuilder)
                .build();
    }
}
