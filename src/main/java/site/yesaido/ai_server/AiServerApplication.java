package site.yesaido.ai_server;

import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration;
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(exclude = {
        GoogleGenAiChatAutoConfiguration.class, // 스프링 기본 구글 채팅 자동생성 끄기
        OllamaChatAutoConfiguration.class       // Ollama 채팅 자동생성 끄기 (임베딩만 사용)
})
@EnableAsync
@EnableFeignClients
public class AiServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiServerApplication.class, args);
    }

}
