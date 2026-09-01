package site.yesaido.ai_server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import site.yesaido.ai_server.dto.daily_feedback.DailyFeedbackContext;
import site.yesaido.ai_server.exception.AiAnalysisFailedException;

import java.util.Map;

/**
 * 검증된 일일 피드백 Context를 프롬프트로 준비하고
 * LLM을 호출하여 저장 가능한 피드백 문자열을 생성하는 서비스입니다.
 *
 * <p>Context를 Jackson 2 {@link JsonNode}로 변환한 후 보안 정제기를
 * 통과한 JSON만 외부 모델에 전달합니다. 원본 Context와 내부 ID,
 * 민감한 연결정보는 프롬프트에 직접 전달하지 않습니다.</p>
 *
 * <p>Gemini를 먼저 호출하고 호출 또는 출력 검증에 실패하면 동일한
 * 프롬프트로 Ollama를 한 번 호출합니다. 두 모델의 결과 모두
 * {@link DailyFeedbackOutputValidator}를 통과한 경우에만 반환합니다.</p>
 *
 * <p>이 서비스는 DailyFeedback DB 저장, RabbitMQ 이벤트 발행,
 * 외부 데이터 수집, 배치 반복과 스케줄링을 담당하지 않습니다.</p>
 */
@Slf4j
@Service
public class DailyFeedbackGenerationService {

    private static final String PREPARATION_FAILURE_MESSAGE = "일일 피드백 생성 준비 과정에 실패했습니다.";
    private static final String GENERATION_FAILURE_MESSAGE = "일일 피드백을 생성하지 못했습니다.";
    private static final String GEMINI_GENERATION_STAGE = "Gemini 호출 및 출력 검증";
    private static final String OLLAMA_GENERATION_STAGE = "Ollama 호출 및 출력 검증";

    private final ChatClient geminiChatClient;
    private final ChatClient ollamaChatClient;
    private final ObjectMapper objectMapper;
    private final DailyFeedbackPromptContextSanitizer contextSanitizer;
    private final DailyFeedbackOutputValidator outputValidator;
    private final Resource systemPromptResource;
    private final Resource userPromptResource;

    public DailyFeedbackGenerationService(
            @Qualifier("geminiChatClient")
            ChatClient geminiChatClient,
            @Qualifier("ollamaChatClient")
            ChatClient ollamaChatClient,

            ObjectMapper objectMapper,
            DailyFeedbackPromptContextSanitizer contextSanitizer,
            DailyFeedbackOutputValidator outputValidator,

            @Value("classpath:prompts/daily_feedback_system.st")
            Resource systemPromptResource,
            @Value("classpath:prompts/daily_feedback_user.st")
            Resource userPromptResource
    ) {
        this.geminiChatClient = geminiChatClient;
        this.ollamaChatClient = ollamaChatClient;
        this.objectMapper = objectMapper;
        this.contextSanitizer = contextSanitizer;
        this.outputValidator = outputValidator;
        this.systemPromptResource = systemPromptResource;
        this.userPromptResource = userPromptResource;
    }

    /**
     * 일일 피드백 Context를 안전한 프롬프트로 변환하고 검증된 피드백을 생성합니다.
     *
     * <p>프롬프트 준비는 모델 호출 전에 한 번만 수행합니다. Gemini 호출이나
     * 출력 검증에 실패하면 같은 프롬프트로 Ollama를 한 번 호출합니다.</p>
     *
     * @param context 외부 데이터 수집과 계약 검증을 완료한 일일 피드백 Context
     * @return 줄바꿈과 앞뒤 공백이 정규화되고 저장 가능성이 검증된 피드백 문자열
     * @throws IllegalArgumentException context가 null인 경우
     * @throws AiAnalysisFailedException Context 정제 또는 프롬프트 준비에 실패하거나
     *         두 모델 모두 호출 또는 출력 검증에 실패한 경우
     */
    public String generate(DailyFeedbackContext context) {
        if (context == null) {
            throw new IllegalArgumentException("context는 null일 수 없습니다.");
        }

        PreparedPrompt preparedPrompt = preparePrompt(context);

        return generateWithFallback(preparedPrompt);
    }

    private PreparedPrompt preparePrompt(DailyFeedbackContext context) {
        JsonNode rawContextNode = convertContextToJsonNode(context);
        JsonNode sanitizedContextNode = sanitizeContext(rawContextNode);
        String sanitizedContextJson = serializeSanitizedContext(sanitizedContextNode);

        return renderPrompts(sanitizedContextJson);
    }

    private JsonNode convertContextToJsonNode(DailyFeedbackContext context) {
        try {
            return objectMapper.valueToTree(context);
        } catch (RuntimeException exception) {
            throw createPreparationFailure("Context JsonNode 변환", exception);
        }
    }

    private JsonNode sanitizeContext(JsonNode rawContextNode) {
        try {
            return contextSanitizer.sanitize(rawContextNode);
        } catch (AiAnalysisFailedException exception) {
            logPreparationFailure("Context 정제", exception);
            throw exception;
        } catch (RuntimeException exception) {
            throw createPreparationFailure(
                    "Context 정제",
                    exception
            );
        }
    }

    private String serializeSanitizedContext(JsonNode sanitizedContextNode) {
        try {
            return objectMapper.writeValueAsString(sanitizedContextNode);
        } catch (JsonProcessingException | RuntimeException exception) {
            throw createPreparationFailure("정제된 Context JSON 직렬화", exception);
        }
    }

    private PreparedPrompt renderPrompts(String sanitizedContextJson) {
        try {
            PromptTemplate systemPromptTemplate = new PromptTemplate(systemPromptResource);
            PromptTemplate userPromptTemplate = new PromptTemplate(userPromptResource);

            String systemMessage = systemPromptTemplate.render();
            String userMessage = userPromptTemplate.render(Map.of("contextJson", sanitizedContextJson));

            return new PreparedPrompt(systemMessage, userMessage);
        } catch (RuntimeException exception) {
            throw createPreparationFailure("프롬프트 렌더링", exception);
        }
    }

    private String generateWithFallback(PreparedPrompt preparedPrompt) {
        try {
            return callAndValidate(geminiChatClient, preparedPrompt);
        } catch (RuntimeException exception) {
            logModelFailure(GEMINI_GENERATION_STAGE, exception);
        }

        try {
            return callAndValidate(ollamaChatClient, preparedPrompt);
        } catch (RuntimeException exception) {
            logModelFailure(OLLAMA_GENERATION_STAGE, exception);

            throw new AiAnalysisFailedException(GENERATION_FAILURE_MESSAGE);
        }
    }

    private String callAndValidate(ChatClient client, PreparedPrompt preparedPrompt) {
        String content = client.prompt()
                .system(preparedPrompt.systemMessage())
                .user(preparedPrompt.userMessage())
                .call()
                .content();

        return outputValidator.validateAndNormalize(content);
    }

    private AiAnalysisFailedException createPreparationFailure(String stage, Exception exception) {
        logPreparationFailure(stage, exception);

        return new AiAnalysisFailedException(PREPARATION_FAILURE_MESSAGE);
    }

    private void logPreparationFailure(String stage, Exception exception) {
        log.error("일일 피드백 준비 실패: stage={}, exceptionType={}", stage, exception.getClass().getSimpleName());
    }

    private void logModelFailure(String stage, RuntimeException exception) {
        log.warn("일일 피드백 모델 처리 실패: stage={}, exceptionType={}", stage, exception.getClass().getSimpleName());
    }

    private record PreparedPrompt(
            String systemMessage,
            String userMessage
    ) {
    }
}
