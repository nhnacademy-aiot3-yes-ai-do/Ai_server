package site.yesaido.ai_server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import site.yesaido.ai_server.dto.daily_feedback.DailyFeedbackContext;
import site.yesaido.ai_server.exception.AiAnalysisFailedException;

import java.math.RoundingMode;
import java.util.Map;
import java.util.Set;

/**
 * 검증된 일일 피드백 Context를 프롬프트로 준비하고
 * LLM을 호출하여 저장 가능한 피드백 문자열을 생성하는 서비스입니다.
 *
 * <p>Context를 Jackson 2 {@link JsonNode}로 변환한 후 보안 정제기를
 * 통과한 JSON만 외부 모델에 전달합니다. 정제기가 만든 독립 복사본의
 * 센서 최솟값·평균값·최댓값만 표시용으로 소수 셋째 자리에서 반올림하여
 * 최대 둘째 자리까지 전달하며,
 * 원본 Context와 DB Context Snapshot의 정밀도는 변경하지 않습니다.
 * 내부 ID와 민감한 연결정보는 프롬프트에 직접 전달하지 않습니다.</p>
 *
 * <p>Gemini를 우선 호출하고, 호출 또는 출력 검증에 실패하면
 * {@code ollamaChatClient} Bean이 존재하는 경우에만 같은 프롬프트로
 * Ollama fallback을 한 번 수행합니다. Ollama 채팅 Bean이 없어도
 * 서버 기동은 정상적으로 허용하며, Gemini 실패 후 fallback을 사용할
 * 수 없으면 안전한 최종 예외를 반환합니다.</p>
 *
 * <p>선택적인 Ollama 채팅 fallback은 팀원이 구성한 기존 Ollama
 * embedding 및 PGVector 구성과 독립적인 기능입니다.</p>
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
    private static final String OLLAMA_PROVIDER_LOOKUP_STAGE = "Ollama fallback Bean 조회";
    private static final String OLLAMA_GENERATION_STAGE = "Ollama 호출 및 출력 검증";

    private static final int SENSOR_DISPLAY_SCALE = 2;

    private static final Set<String> SENSOR_STATISTIC_VALUE_FIELDS = Set.of(
            "minimumValue",
            "averageValue",
            "maximumValue"
    );

    private final ChatClient geminiChatClient;
    private final ObjectProvider<ChatClient> ollamaChatClientProvider;
    private final ObjectMapper objectMapper;
    private final DailyFeedbackPromptContextSanitizer contextSanitizer;
    private final DailyFeedbackOutputValidator outputValidator;
    private final Resource systemPromptResource;
    private final Resource userPromptResource;

    public DailyFeedbackGenerationService(
            @Qualifier("geminiChatClient")
            ChatClient geminiChatClient,
            @Qualifier("ollamaChatClient")
            ObjectProvider<ChatClient> ollamaChatClientProvider,

            ObjectMapper objectMapper,
            DailyFeedbackPromptContextSanitizer contextSanitizer,
            DailyFeedbackOutputValidator outputValidator,

            @Value("classpath:prompts/daily_feedback_system.st")
            Resource systemPromptResource,
            @Value("classpath:prompts/daily_feedback_user.st")
            Resource userPromptResource
    ) {
        this.geminiChatClient = geminiChatClient;
        this.ollamaChatClientProvider = ollamaChatClientProvider;
        this.objectMapper = objectMapper;
        this.contextSanitizer = contextSanitizer;
        this.outputValidator = outputValidator;
        this.systemPromptResource = systemPromptResource;
        this.userPromptResource = userPromptResource;
    }

    /**
     * 일일 피드백 Context를 안전한 프롬프트로 변환하고
     * 검증된 피드백을 생성합니다.
     *
     * <p>프롬프트 준비는 모델 호출 전에 한 번만 수행합니다.
     * Gemini를 우선 호출하며, Gemini 호출 또는 출력 검증에 실패하면
     * {@code ollamaChatClient} Bean이 존재할 때만 동일한 프롬프트로
     * Ollama fallback을 한 번 수행합니다.</p>
     *
     * <p>Ollama 채팅 Bean이 없거나 Provider 조회에 실패하면 모델 입력이나
     * 내부 오류 정보를 노출하지 않는 안전한 최종 생성 실패 예외를
     * 발생시킵니다. 이 선택적 fallback은 Ollama embedding 구성과
     * 독립적으로 동작합니다.</p>
     *
     * @param context 외부 데이터 수집과 계약 검증을 완료한 일일 피드백 Context
     * @return 줄바꿈과 앞뒤 공백이 정규화되고 저장 가능성이 검증된 피드백 문자열
     * @throws IllegalArgumentException context가 null인 경우
     * @throws AiAnalysisFailedException Context 정제 또는 프롬프트 준비에
     *                                   실패하거나, Gemini 실패 후 사용 가능한
     *                                   fallback이 없거나 fallback 처리에도 실패한 경우
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
        roundSensorStatisticsForPrompt(sanitizedContextNode);
        String sanitizedContextJson = serializeSanitizedContext(sanitizedContextNode);

        return renderPrompts(sanitizedContextJson);
    }

    /**
     * LLM에 전달할 독립 Context 복사본의 센서 통계 표시값만
     * 소수 셋째 자리에서 {@link RoundingMode#HALF_UP}으로 반올림하여
     * 최대 둘째 자리까지 전달합니다.
     *
     * <p>{@code sensorStatistics} 배열 요소의 {@code minimumValue},
     * {@code averageValue}, {@code maximumValue}만 대상으로 하며,
     * 집계점 수, 임계값, 환경 유지율과 Vision 확률 등 다른 숫자는
     * 변경하지 않습니다. null과 숫자가 아닌 값도 그대로 유지합니다.</p>
     *
     * @param sanitizedContextNode 보안 정제기가 생성한 프롬프트 전용 복사본
     */
    private void roundSensorStatisticsForPrompt(JsonNode sanitizedContextNode) {
        try {
            JsonNode sensorStatistics =
                    sanitizedContextNode.path("sensorStatistics");

            if (!sensorStatistics.isArray()) {
                return;
            }

            for (JsonNode statistics : sensorStatistics) {
                if (!(statistics instanceof ObjectNode statisticsObject)) {
                    continue;
                }

                for (String fieldName : SENSOR_STATISTIC_VALUE_FIELDS) {
                    JsonNode value = statisticsObject.get(fieldName);

                    if (value == null || !value.isNumber()) {
                        continue;
                    }

                    statisticsObject.put(
                            fieldName,
                            value.decimalValue().setScale(
                                    SENSOR_DISPLAY_SCALE,
                                    RoundingMode.HALF_UP
                            )
                    );
                }
            }
        } catch (RuntimeException exception) {
            throw createPreparationFailure(
                    "센서 통계 표시 정밀도 정규화",
                    exception
            );
        }
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

        ChatClient ollamaChatClient = getOllamaChatClientIfAvailable();

        if (ollamaChatClient == null) {
            log.warn("Ollama fallback Bean을 사용할 수 없음");
            throw new AiAnalysisFailedException(GENERATION_FAILURE_MESSAGE);
        }

        try {
            return callAndValidate(ollamaChatClient, preparedPrompt);
        } catch (RuntimeException exception) {
            logModelFailure(OLLAMA_GENERATION_STAGE, exception);

            throw new AiAnalysisFailedException(GENERATION_FAILURE_MESSAGE);
        }
    }

    private ChatClient getOllamaChatClientIfAvailable() {
        try {
            return ollamaChatClientProvider.getIfAvailable();
        } catch (RuntimeException exception) {
            logModelFailure(OLLAMA_PROVIDER_LOOKUP_STAGE, exception);

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
