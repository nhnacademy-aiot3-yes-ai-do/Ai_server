package site.yesaido.ai_server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import site.yesaido.ai_server.dto.client.cultivation.DailyCultivationDetailResponse;
import site.yesaido.ai_server.dto.client.mushroom_reference.MushroomReferenceInfoResponse;
import site.yesaido.ai_server.dto.client.mushroom_reference.MushroomReferenceThresholdInfoResponse;
import site.yesaido.ai_server.dto.client.mushroom_reference.SensorTypeInfoResponse;
import site.yesaido.ai_server.dto.client.sensor.snapshot.DataGeneratorThresholdResponse;
import site.yesaido.ai_server.dto.daily_feedback.DailyEnvironmentCompliance;
import site.yesaido.ai_server.dto.daily_feedback.DailyFeedbackContext;
import site.yesaido.ai_server.dto.daily_feedback.DailyNotificationMetrics;
import site.yesaido.ai_server.dto.daily_feedback.DailyVisionAnalysisSnapshot;
import site.yesaido.ai_server.dto.daily_feedback.SensorChannelKey;
import site.yesaido.ai_server.dto.daily_feedback.SensorChannelStatistics;
import site.yesaido.ai_server.exception.AiAnalysisFailedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("일일 피드백 생성 서비스 테스트")
class DailyFeedbackGenerationServiceTest {

    private static final Long CULTIVATION_ID = 101L;
    private static final Long MUSHROOM_ID = 7L;
    private static final long SENSOR_TYPE_ID = 301L;

    private static final LocalDate FEEDBACK_DATE =
            LocalDate.of(2026, 9, 1);

    private static final String CULTIVATION_NAME =
            "느타리버섯 테스트 재배지";

    private static final String DEVICE_EUI =
            "EUI-TEST-001";

    private static final String SENSOR_TYPE =
            "TEMPERATURE";

    private static final String SENSOR_UNIT =
            "℃";

    private static final String CONTEXT_JSON_START =
            "----- DAILY_FEEDBACK_CONTEXT_JSON_START -----";

    private static final String CONTEXT_JSON_END =
            "----- DAILY_FEEDBACK_CONTEXT_JSON_END -----";

    private static final String FINAL_FAILURE_MESSAGE =
            "일일 피드백을 생성하지 못했습니다.";

    private static final String PREPARATION_FAILURE_MESSAGE =
            "일일 피드백 생성 준비 과정에 실패했습니다.";

    private static final String SANITIZER_FAILURE_MESSAGE =
            "일일 피드백 Context에 외부 전송이 허용되지 않는 연결 정보가 포함되어 있습니다.";

    private static final String GEMINI_EXCEPTION_DETAIL =
            "TEST_GEMINI_PROVIDER_PRIVATE_MESSAGE";

    private static final String OLLAMA_EXCEPTION_DETAIL =
            "TEST_OLLAMA_PROVIDER_PRIVATE_MESSAGE";

    private static final String OLLAMA_PROVIDER_EXCEPTION_DETAIL =
            "TEST_OLLAMA_PROVIDER_LOOKUP_PRIVATE_MESSAGE_"
                    + DEVICE_EUI;

    private static final String PREPARATION_EXCEPTION_DETAIL =
            "TEST_PREPARATION_PRIVATE_MESSAGE_" + DEVICE_EUI;

    private static final String TEST_OUTPUT_URL =
            "https://feedback-output.invalid/private";

    private static final String TEST_CONTEXT_URL =
            "https://context-value.invalid/private";

    private static final String TEST_SIGNATURE_VALUE =
            "TEST_ONLY_FAKE_SIGNATURE";

    @Mock
    private ChatClient geminiChatClient;

    @Mock
    private ChatClient ollamaChatClient;

    @Mock
    private ObjectProvider<ChatClient>
            ollamaChatClientProvider;

    private ObjectMapper objectMapper;
    private DailyFeedbackPromptContextSanitizer contextSanitizer;
    private DailyFeedbackOutputValidator outputValidator;
    private Resource systemPromptResource;
    private Resource userPromptResource;
    private DailyFeedbackGenerationService service;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(
                        SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
                )
                .build();

        contextSanitizer =
                new DailyFeedbackPromptContextSanitizer();

        outputValidator =
                new DailyFeedbackOutputValidator();

        systemPromptResource = new ClassPathResource(
                "prompts/daily_feedback_system.st"
        );

        userPromptResource = new ClassPathResource(
                "prompts/daily_feedback_user.st"
        );

        service = createService(objectMapper);
    }

    @Test
    @DisplayName("Context가 null이면 모델을 호출하기 전에 거부한다")
    void rejectsNullContextBeforeCallingModels() {
        // 준비
        DailyFeedbackContext context = null;

        // 실행
        IllegalArgumentException exception =
                catchThrowableOfType(
                        IllegalArgumentException.class,
                        () -> service.generate(context)
                );

        // 검증
        assertThat(exception)
                .isNotNull()
                .hasMessage("context는 null일 수 없습니다.");

        verifyNoInteractions(
                geminiChatClient,
                ollamaChatClientProvider,
                ollamaChatClient
        );
    }

    @Test
    @DisplayName("Gemini의 유효한 응답을 정규화하고 정제된 Context만 전달한다")
    void returnsNormalizedGeminiResultAndBuildsSanitizedPrompts()
            throws JsonProcessingException {
        // 준비
        DailyFeedbackContext context = validContext();
        String originalContextJson =
                objectMapper.writeValueAsString(context);

        String geminiOutput =
                withCrLfAndTrailingWhitespace(validFeedback());

        ModelCallMock geminiCall = stubModelResponse(
                geminiChatClient,
                geminiOutput
        );

        // 실행
        String result = service.generate(context);

        // 검증
        assertThat(result)
                .isEqualTo(validFeedback())
                .doesNotContain("\r");

        verify(geminiChatClient, times(1)).prompt();
        verify(geminiCall.responseSpec(), times(1)).content();

        verifyNoInteractions(
                ollamaChatClientProvider,
                ollamaChatClient
        );

        CapturedMessages messages =
                captureMessages(geminiCall);

        String sanitizedContextJson =
                extractSanitizedContextJson(
                        messages.userMessage()
                );

        JsonNode sanitizedContext =
                objectMapper.readTree(sanitizedContextJson);

        assertThat(
                sanitizedContext.path("feedbackDate").asText()
        ).isEqualTo(FEEDBACK_DATE.toString());

        assertThat(
                sanitizedContext
                        .path("cultivationDetail")
                        .path("name")
                        .asText()
        ).isEqualTo(CULTIVATION_NAME);

        assertThat(
                sanitizedContext
                        .path("mushroomReference")
                        .path("mushroomNameKo")
                        .asText()
        ).isEqualTo("느타리버섯");

        JsonNode currentThreshold =
                sanitizedContext
                        .path("currentThresholds")
                        .get(0);

        assertThat(
                currentThreshold.path("sensorType").asText()
        ).isEqualTo(SENSOR_TYPE);

        assertThat(
                currentThreshold.path("unit").asText()
        ).isEqualTo(SENSOR_UNIT);

        JsonNode statistics =
                sanitizedContext
                        .path("sensorStatistics")
                        .get(0);

        assertThat(
                statistics
                        .path("channelKey")
                        .path("deviceEui")
                        .asText()
        ).isEqualTo(DEVICE_EUI);

        assertThat(
                statistics
                        .path("channelKey")
                        .path("sensorType")
                        .asText()
        ).isEqualTo(SENSOR_TYPE);

        assertThat(
                statistics
                        .path("channelKey")
                        .path("unit")
                        .asText()
        ).isEqualTo(SENSOR_UNIT);

        BigDecimal promptedMinimumValue =
                statistics.path("minimumValue").decimalValue();
        BigDecimal promptedAverageValue =
                statistics.path("averageValue").decimalValue();
        BigDecimal promptedMaximumValue =
                statistics.path("maximumValue").decimalValue();

        assertThat(promptedMinimumValue)
                .isEqualByComparingTo("18.50");

        assertThat(promptedAverageValue)
                .isEqualByComparingTo("20.26");

        assertThat(promptedMaximumValue)
                .isEqualByComparingTo("22.01");

        assertThat(sanitizedContextJson)
                .doesNotContain(
                        "18.504",
                        "20.255",
                        "22.006"
                );

        assertThat(
                statistics
                        .path("aggregationPointCount")
                        .intValue()
        ).isEqualTo(96);

        JsonNode noDataStatistics =
                sanitizedContext
                        .path("sensorStatistics")
                        .get(1);

        assertThat(noDataStatistics.path("aggregationPointCount").intValue())
                .isZero();
        assertThat(noDataStatistics.path("minimumValue").isNull())
                .isTrue();
        assertThat(noDataStatistics.path("averageValue").isNull())
                .isTrue();
        assertThat(noDataStatistics.path("maximumValue").isNull())
                .isTrue();

        assertThat(
                sanitizedContext
                        .path("environmentCompliance")
                        .path("temperatureCompliance")
                        .decimalValue()
        ).isEqualByComparingTo("92.5001");

        assertThat(
                currentThreshold.path("minValue").decimalValue()
        ).isEqualByComparingTo("19.1234");

        assertThat(
                currentThreshold.path("maxValue").decimalValue()
        ).isEqualByComparingTo("23.5678");

        assertThat(
                sanitizedContext
                        .path("notificationMetrics")
                        .path("thresholdBreachAlertCount")
                        .longValue()
        ).isEqualTo(3L);

        assertThat(
                sanitizedContext
                        .path("visionAnalysis")
                        .path("hasVisionAnalysis")
                        .booleanValue()
        ).isFalse();

        assertThat(sanitizedContext.has("cultivationId"))
                .isFalse();

        assertThat(
                sanitizedContext
                        .path("cultivationDetail")
                        .has("mushroomId")
        ).isFalse();

        assertThat(
                sanitizedContext
                        .path("cultivationDetail")
                        .has("myRole")
        ).isFalse();

        assertThat(
                sanitizedContext
                        .path("mushroomReference")
                        .has("id")
        ).isFalse();

        assertThat(
                sanitizedContext
                        .path("visionAnalysis")
                        .has("growthRecordId")
        ).isFalse();

        assertThat(
                sanitizedContext
                        .path("visionAnalysis")
                        .has("cultivationPhotoId")
        ).isFalse();

        assertThat(sanitizedContextJson)
                .doesNotContain(
                        "\"cultivationId\"",
                        "\"mushroomId\"",
                        "\"sensorTypeId\"",
                        "\"growthRecordId\"",
                        "\"cultivationPhotoId\"",
                        "\"presignedUrl\"",
                        "\"objectKey\"",
                        "\"url\"",
                        "http://",
                        "https://",
                        "s3://"
                );

        assertThat(messages.systemMessage())
                .contains("## 오늘의 환경 요약")
                .doesNotContain(
                        DEVICE_EUI,
                        CULTIVATION_NAME,
                        FEEDBACK_DATE.toString(),
                        CONTEXT_JSON_START,
                        sanitizedContextJson
                );

        assertThat(objectMapper.writeValueAsString(context))
                .isEqualTo(originalContextJson);
        assertThat(context.sensorStatistics().getFirst().minimumValue())
                .isEqualTo(new BigDecimal("18.504"));
        assertThat(context.sensorStatistics().getFirst().averageValue())
                .isEqualTo(new BigDecimal("20.255"));
        assertThat(context.sensorStatistics().getFirst().maximumValue())
                .isEqualTo(new BigDecimal("22.006"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("geminiFallbackCases")
    @DisplayName("Gemini 호출 또는 출력 검증 실패 시 동일한 프롬프트로 Ollama를 호출한다")
    void fallsBackToOllamaForGeminiFailures(
            ModelOutcome geminiFailure
    ) {
        // 준비
        DailyFeedbackContext context = validContext();

        stubAvailableOllamaChatClient();

        ModelCallMock geminiCall = stubModelOutcome(
                geminiChatClient,
                geminiFailure
        );

        String ollamaOutput =
                withCrLfAndTrailingWhitespace(
                        validFallbackFeedback()
                );

        ModelCallMock ollamaCall = stubModelResponse(
                ollamaChatClient,
                ollamaOutput
        );

        // 실행
        String result = service.generate(context);

        // 검증
        assertThat(result)
                .isEqualTo(validFallbackFeedback())
                .doesNotContain("\r");

        if (geminiFailure.content() != null
                && !geminiFailure.content().isBlank()) {
            assertThat(result)
                    .isNotEqualTo(geminiFailure.content());
        }

        verify(geminiChatClient, times(1)).prompt();

        verify(
                ollamaChatClientProvider,
                times(1)
        ).getIfAvailable();

        verify(ollamaChatClient, times(1)).prompt();
        verify(geminiCall.responseSpec(), times(1)).content();
        verify(ollamaCall.responseSpec(), times(1)).content();

        CapturedMessages geminiMessages =
                captureMessages(geminiCall);

        CapturedMessages ollamaMessages =
                captureMessages(ollamaCall);

        assertThat(ollamaMessages.systemMessage())
                .isEqualTo(geminiMessages.systemMessage());

        assertThat(ollamaMessages.userMessage())
                .isEqualTo(geminiMessages.userMessage())
                .contains(DEVICE_EUI)
                .doesNotContain(
                        "\"cultivationId\"",
                        "\"mushroomId\"",
                        "\"growthRecordId\"",
                        "\"cultivationPhotoId\""
                );
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("ollamaFailureCases")
    @DisplayName("Ollama 호출 또는 출력 검증도 실패하면 안전한 최종 예외를 반환한다")
    void throwsSafeFinalExceptionWhenOllamaAlsoFails(
            ModelOutcome ollamaFailure
    ) throws JsonProcessingException {
        // 준비
        DailyFeedbackContext context = validContext();

        String rawContextJson =
                objectMapper.writeValueAsString(context);

        stubAvailableOllamaChatClient();

        stubModelFailure(
                geminiChatClient,
                new IllegalStateException(
                        GEMINI_EXCEPTION_DETAIL
                )
        );

        stubModelOutcome(
                ollamaChatClient,
                ollamaFailure
        );

        // 실행
        AiAnalysisFailedException exception =
                catchThrowableOfType(
                        AiAnalysisFailedException.class,
                        () -> service.generate(context)
                );

        // 검증
        assertThat(exception)
                .isNotNull()
                .isInstanceOf(AiAnalysisFailedException.class);

        String message = exception.getMessage();

        assertThat(message)
                .isEqualTo(FINAL_FAILURE_MESSAGE)
                .doesNotContain(
                        GEMINI_EXCEPTION_DETAIL,
                        OLLAMA_EXCEPTION_DETAIL,
                        rawContextJson,
                        DEVICE_EUI,
                        TEST_OUTPUT_URL,
                        TEST_SIGNATURE_VALUE
                );

        if (ollamaFailure.failure() != null) {
            assertThat(message)
                    .doesNotContain(
                            ollamaFailure.failure().getMessage()
                    );
        }

        if (ollamaFailure.content() != null
                && !ollamaFailure.content().isBlank()) {
            assertThat(message)
                    .doesNotContain(ollamaFailure.content());
        }

        verify(geminiChatClient, times(1)).prompt();

        verify(
                ollamaChatClientProvider,
                times(1)
        ).getIfAvailable();

        verify(ollamaChatClient, times(1)).prompt();
    }

    @Test
    @DisplayName("Gemini가 실패하고 Ollama Provider가 null이면 안전한 최종 예외를 반환한다")
    void throwsSafeFinalExceptionWhenOllamaProviderReturnsNull()
            throws JsonProcessingException {
        // 준비
        DailyFeedbackContext context = validContext();

        String rawContextJson =
                objectMapper.writeValueAsString(context);

        ModelCallMock geminiCall = stubModelFailure(
                geminiChatClient,
                new IllegalStateException(
                        GEMINI_EXCEPTION_DETAIL
                )
        );

        when(ollamaChatClientProvider.getIfAvailable())
                .thenReturn(null);

        // 실행
        AiAnalysisFailedException exception =
                catchThrowableOfType(
                        AiAnalysisFailedException.class,
                        () -> service.generate(context)
                );

        // 검증
        assertThat(exception)
                .isNotNull()
                .isInstanceOf(AiAnalysisFailedException.class);

        assertThat(exception.getMessage())
                .isEqualTo(FINAL_FAILURE_MESSAGE)
                .doesNotContain(
                        GEMINI_EXCEPTION_DETAIL,
                        rawContextJson,
                        DEVICE_EUI,
                        CULTIVATION_NAME
                );

        verify(geminiChatClient, times(1)).prompt();
        verify(geminiCall.responseSpec(), times(1)).content();

        verify(
                ollamaChatClientProvider,
                times(1)
        ).getIfAvailable();

        verifyNoInteractions(ollamaChatClient);
    }

    @Test
    @DisplayName("Gemini가 실패하고 Ollama Provider 조회도 실패하면 안전한 최종 예외를 반환한다")
    void throwsSafeFinalExceptionWhenOllamaProviderLookupFails()
            throws JsonProcessingException {
        // 준비
        DailyFeedbackContext context = validContext();

        String rawContextJson =
                objectMapper.writeValueAsString(context);

        ModelCallMock geminiCall = stubModelFailure(
                geminiChatClient,
                new IllegalStateException(
                        GEMINI_EXCEPTION_DETAIL
                )
        );

        when(ollamaChatClientProvider.getIfAvailable())
                .thenThrow(
                        new IllegalStateException(
                                OLLAMA_PROVIDER_EXCEPTION_DETAIL
                        )
                );

        // 실행
        AiAnalysisFailedException exception =
                catchThrowableOfType(
                        AiAnalysisFailedException.class,
                        () -> service.generate(context)
                );

        // 검증
        assertThat(exception)
                .isNotNull()
                .isInstanceOf(AiAnalysisFailedException.class);

        assertThat(exception.getMessage())
                .isEqualTo(FINAL_FAILURE_MESSAGE)
                .doesNotContain(
                        OLLAMA_PROVIDER_EXCEPTION_DETAIL,
                        GEMINI_EXCEPTION_DETAIL,
                        rawContextJson,
                        DEVICE_EUI,
                        CULTIVATION_NAME
                );

        verify(geminiChatClient, times(1)).prompt();
        verify(geminiCall.responseSpec(), times(1)).content();

        verify(
                ollamaChatClientProvider,
                times(1)
        ).getIfAvailable();

        verifyNoInteractions(ollamaChatClient);
    }

    @Test
    @DisplayName("Context 문자열에 외부 연결정보가 있으면 모델 호출 전에 실패한다")
    void propagatesSanitizerFailureBeforeCallingModels() {
        // 준비
        String unsafeCultivationName =
                TEST_CONTEXT_URL
                        + "?signature="
                        + TEST_SIGNATURE_VALUE;

        DailyFeedbackContext context =
                validContext(unsafeCultivationName);

        // 실행
        AiAnalysisFailedException exception =
                catchThrowableOfType(
                        AiAnalysisFailedException.class,
                        () -> service.generate(context)
                );

        // 검증
        assertThat(exception)
                .isNotNull()
                .isInstanceOf(AiAnalysisFailedException.class);

        String message = exception.getMessage();

        assertThat(message)
                .isEqualTo(SANITIZER_FAILURE_MESSAGE)
                .doesNotContain(
                        TEST_CONTEXT_URL,
                        TEST_SIGNATURE_VALUE,
                        unsafeCultivationName
                );

        verifyNoInteractions(
                geminiChatClient,
                ollamaChatClientProvider,
                ollamaChatClient
        );
    }

    @Test
    @DisplayName("Context JsonNode 변환 실패는 fallback 없이 안전한 준비 예외로 변환한다")
    void wrapsContextConversionFailureWithoutModelFallback()
            throws JsonProcessingException {
        // 준비
        DailyFeedbackContext context = validContext();

        String rawContextJson =
                objectMapper.writeValueAsString(context);

        ObjectMapper failingObjectMapper =
                mock(ObjectMapper.class);

        when(
                failingObjectMapper
                        .<JsonNode>valueToTree(context)
        ).thenThrow(
                new IllegalArgumentException(
                        PREPARATION_EXCEPTION_DETAIL
                )
        );

        DailyFeedbackGenerationService failingService =
                createService(failingObjectMapper);

        // 실행
        AiAnalysisFailedException exception =
                catchThrowableOfType(
                        AiAnalysisFailedException.class,
                        () -> failingService.generate(context)
                );

        // 검증
        assertThat(exception)
                .isNotNull()
                .isInstanceOf(AiAnalysisFailedException.class);

        String message = exception.getMessage();

        assertThat(message)
                .isEqualTo(PREPARATION_FAILURE_MESSAGE)
                .doesNotContain(
                        PREPARATION_EXCEPTION_DETAIL,
                        rawContextJson,
                        DEVICE_EUI,
                        CULTIVATION_NAME
                );

        verifyNoInteractions(
                geminiChatClient,
                ollamaChatClientProvider,
                ollamaChatClient
        );
    }

    private DailyFeedbackGenerationService createService(
            ObjectMapper mapper
    ) {
        return new DailyFeedbackGenerationService(
                geminiChatClient,
                ollamaChatClientProvider,
                mapper,
                contextSanitizer,
                outputValidator,
                systemPromptResource,
                userPromptResource
        );
    }

    private void stubAvailableOllamaChatClient() {
        when(ollamaChatClientProvider.getIfAvailable())
                .thenReturn(ollamaChatClient);
    }

    private ModelCallMock stubModelOutcome(
            ChatClient client,
            ModelOutcome outcome
    ) {
        if (outcome.failure() != null) {
            return stubModelFailure(
                    client,
                    outcome.failure()
            );
        }

        return stubModelResponse(
                client,
                outcome.content()
        );
    }

    private ModelCallMock stubModelResponse(
            ChatClient client,
            String response
    ) {
        ModelCallMock modelCall =
                stubModelChain(client);

        when(modelCall.responseSpec().content())
                .thenReturn(response);

        return modelCall;
    }

    private ModelCallMock stubModelFailure(
            ChatClient client,
            RuntimeException failure
    ) {
        ModelCallMock modelCall =
                stubModelChain(client);

        when(modelCall.responseSpec().content())
                .thenThrow(failure);

        return modelCall;
    }

    private ModelCallMock stubModelChain(
            ChatClient client
    ) {
        ChatClient.ChatClientRequestSpec requestSpec =
                mock(
                        ChatClient.ChatClientRequestSpec.class
                );

        ChatClient.CallResponseSpec responseSpec =
                mock(
                        ChatClient.CallResponseSpec.class
                );

        when(client.prompt())
                .thenReturn(requestSpec);

        when(requestSpec.system(anyString()))
                .thenReturn(requestSpec);

        when(requestSpec.user(anyString()))
                .thenReturn(requestSpec);

        when(requestSpec.call())
                .thenReturn(responseSpec);

        return new ModelCallMock(
                requestSpec,
                responseSpec
        );
    }

    private CapturedMessages captureMessages(
            ModelCallMock modelCall
    ) {
        ArgumentCaptor<String> systemCaptor =
                ArgumentCaptor.forClass(String.class);

        ArgumentCaptor<String> userCaptor =
                ArgumentCaptor.forClass(String.class);

        verify(modelCall.requestSpec())
                .system(systemCaptor.capture());

        verify(modelCall.requestSpec())
                .user(userCaptor.capture());

        return new CapturedMessages(
                systemCaptor.getValue(),
                userCaptor.getValue()
        );
    }

    private String extractSanitizedContextJson(
            String userMessage
    ) {
        int startMarkerIndex =
                userMessage.indexOf(CONTEXT_JSON_START);

        int endMarkerIndex =
                userMessage.indexOf(CONTEXT_JSON_END);

        assertThat(startMarkerIndex)
                .isGreaterThanOrEqualTo(0);

        assertThat(endMarkerIndex)
                .isGreaterThan(startMarkerIndex);

        int jsonStartIndex =
                startMarkerIndex + CONTEXT_JSON_START.length();

        return userMessage
                .substring(
                        jsonStartIndex,
                        endMarkerIndex
                )
                .strip();
    }

    private static DailyFeedbackContext validContext() {
        return validContext(CULTIVATION_NAME);
    }

    private static DailyFeedbackContext validContext(
            String cultivationName
    ) {
        DailyCultivationDetailResponse cultivationDetail =
                new DailyCultivationDetailResponse(
                        CULTIVATION_ID,
                        cultivationName,
                        MUSHROOM_ID,
                        "RUNNING",
                        "GROWTH",
                        "OWNER",
                        LocalDateTime.of(
                                2026,
                                8,
                                20,
                                9,
                                0
                        ),
                        null,
                        LocalDateTime.of(
                                2026,
                                8,
                                20,
                                8,
                                30
                        ),
                        LocalDateTime.of(
                                2026,
                                9,
                                1,
                                23,
                                50
                        )
                );

        SensorTypeInfoResponse sensorTypeInfo =
                new SensorTypeInfoResponse(
                        SENSOR_TYPE_ID,
                        SENSOR_TYPE,
                        SENSOR_UNIT
                );

        MushroomReferenceThresholdInfoResponse referenceThreshold =
                new MushroomReferenceThresholdInfoResponse(
                        701L,
                        sensorTypeInfo,
                        "GROWTH",
                        new BigDecimal("18.00"),
                        new BigDecimal("24.00")
                );

        MushroomReferenceInfoResponse mushroomReference =
                new MushroomReferenceInfoResponse(
                        MUSHROOM_ID,
                        "느타리버섯",
                        "Oyster mushroom",
                        "Pleurotus ostreatus",
                        List.of(referenceThreshold)
                );

        DataGeneratorThresholdResponse currentThreshold =
                new DataGeneratorThresholdResponse(
                        CULTIVATION_ID,
                        SENSOR_TYPE,
                        SENSOR_UNIT,
                        new BigDecimal("19.1234"),
                        new BigDecimal("23.5678")
                );

        SensorChannelStatistics sensorStatistics =
                new SensorChannelStatistics(
                        new SensorChannelKey(
                                CULTIVATION_ID,
                                DEVICE_EUI,
                                SENSOR_TYPE,
                                SENSOR_UNIT
                        ),
                        new BigDecimal("18.504"),
                        new BigDecimal("20.255"),
                        new BigDecimal("22.006"),
                        96
                );

        SensorChannelStatistics noDataSensorStatistics =
                new SensorChannelStatistics(
                        new SensorChannelKey(
                                CULTIVATION_ID,
                                "ZZZ-NO-DATA-EUI",
                                "HUMIDITY",
                                "%"
                        ),
                        null,
                        null,
                        null,
                        0
                );

        DailyEnvironmentCompliance environmentCompliance =
                new DailyEnvironmentCompliance(
                        CULTIVATION_ID,
                        FEEDBACK_DATE,
                        new BigDecimal("92.5001"),
                        new BigDecimal("88.00"),
                        new BigDecimal("95.00"),
                        new BigDecimal("90.00")
                );

        DailyNotificationMetrics notificationMetrics =
                new DailyNotificationMetrics(
                        CULTIVATION_ID,
                        FEEDBACK_DATE,
                        7L,
                        3L,
                        2L,
                        1L
                );

        return new DailyFeedbackContext(
                CULTIVATION_ID,
                FEEDBACK_DATE,
                OffsetDateTime.of(
                        2026,
                        9,
                        2,
                        0,
                        5,
                        0,
                        0,
                        ZoneOffset.ofHours(9)
                ),
                cultivationDetail,
                mushroomReference,
                List.of(currentThreshold),
                List.of(
                        sensorStatistics,
                        noDataSensorStatistics
                ),
                environmentCompliance,
                notificationMetrics,
                DailyVisionAnalysisSnapshot.withoutPhoto(
                        CULTIVATION_ID
                )
        );
    }

    private static String validFeedback() {
        return """
                    ## 오늘의 환경 요약
                    테스트 재배지는 느타리버섯을 재배 중이며 입력된 환경 지표를 확인했습니다.

                    ## 센서별 통계
                    - EUI-TEST-001 TEMPERATURE: 15분 평균 집계점 최솟값 18.50℃, 평균값 20.26℃, 최댓값 22.01℃, 집계점 96개입니다.

                    ## 이탈 및 제어
                    임계값 이탈 알림 3건, 제어 성공 2건, 제어 실패 1건입니다.

                    ## Vision 분석
                    사진이 등록되지 않아 Vision 분석이 없습니다.

                    ## 내일의 관리 포인트
                    - 센서와 제어 상태를 계속 확인해 주세요.
                    """.strip();
    }

    private static String validFallbackFeedback() {
        return """
                    ## 오늘의 환경 요약
                    Ollama fallback 결과로 재배지의 환경 지표를 확인했습니다.

                    ## 센서별 통계
                    - EUI-TEST-001 TEMPERATURE: 15분 평균 집계점 최솟값 18.50℃, 평균값 20.26℃, 최댓값 22.01℃, 집계점 96개입니다.

                    ## 이탈 및 제어
                    임계값 이탈 알림 3건, 제어 성공 2건, 제어 실패 1건입니다.

                    ## Vision 분석
                    사진이 등록되지 않아 Vision 분석이 없습니다.

                    ## 내일의 관리 포인트
                    - 센서 채널과 제어 실패 여부를 다시 확인해 주세요.
                    """.strip();
    }

    private static String withCrLfAndTrailingWhitespace(
            String feedback
    ) {
        return feedback.replace("\n", "\r\n")
                + "\r\n\r\n \t";
    }

    private static String feedbackWithoutVisionHeading() {
        return validFeedback().replace(
                "## Vision 분석\n",
                ""
        );
    }

    private static String feedbackWithWrongHeadingOrder() {
        return """
                    ## 오늘의 환경 요약
                    테스트 재배지의 환경 지표를 확인했습니다.

                    ## 이탈 및 제어
                    임계값 이탈 알림과 제어 횟수를 확인했습니다.

                    ## 센서별 통계
                    EUI-TEST-001 채널 통계를 확인했습니다.

                    ## Vision 분석
                    사진이 등록되지 않아 Vision 분석이 없습니다.

                    ## 내일의 관리 포인트
                    - 센서 상태를 확인해 주세요.
                    """.strip();
    }

    private static String feedbackContainingLine(
            String additionalLine
    ) {
        String originalSummary =
                "테스트 재배지는 느타리버섯을 재배 중이며 입력된 환경 지표를 확인했습니다.";

        return validFeedback().replace(
                originalSummary,
                originalSummary + "\n" + additionalLine
        );
    }

    private static Stream<ModelOutcome> geminiFallbackCases() {
        return Stream.of(
                ModelOutcome.failure(
                        "Gemini 호출 예외",
                        new IllegalStateException(
                                GEMINI_EXCEPTION_DETAIL
                        )
                ),
                ModelOutcome.response(
                        "Gemini null 응답",
                        null
                ),
                ModelOutcome.response(
                        "Gemini blank 응답",
                        "   \n\t"
                ),
                ModelOutcome.response(
                        "Gemini 필수 제목 누락",
                        feedbackWithoutVisionHeading()
                ),
                ModelOutcome.response(
                        "Gemini 제목 순서 오류",
                        feedbackWithWrongHeadingOrder()
                ),
                ModelOutcome.response(
                        "Gemini URL 포함",
                        feedbackContainingLine(
                                "- 참고 주소: " + TEST_OUTPUT_URL
                        )
                ),
                ModelOutcome.response(
                        "Gemini 내부 필드명 포함",
                        feedbackContainingLine(
                                "- 내부 진단값: growthRecordId=999"
                        )
                )
        );
    }

    private static Stream<ModelOutcome> ollamaFailureCases() {
        return Stream.of(
                ModelOutcome.failure(
                        "Ollama 호출 예외",
                        new IllegalStateException(
                                OLLAMA_EXCEPTION_DETAIL
                        )
                ),
                ModelOutcome.response(
                        "Ollama null 응답",
                        null
                ),
                ModelOutcome.response(
                        "Ollama blank 응답",
                        "   \n\t"
                ),
                ModelOutcome.response(
                        "Ollama 형식 위반 응답",
                        "필수 Markdown 제목이 없는 응답입니다."
                ),
                ModelOutcome.response(
                        "Ollama 민감정보 포함 응답",
                        feedbackContainingLine(
                                "- 외부 연결: "
                                        + TEST_OUTPUT_URL
                                        + "?signature="
                                        + TEST_SIGNATURE_VALUE
                        )
                )
        );
    }

    private record ModelCallMock(
            ChatClient.ChatClientRequestSpec requestSpec,
            ChatClient.CallResponseSpec responseSpec
    ) {
    }

    private record CapturedMessages(
            String systemMessage,
            String userMessage
    ) {
    }

    private record ModelOutcome(
            String description,
            String content,
            RuntimeException failure
    ) {

        private static ModelOutcome response(
                String description,
                String content
        ) {
            return new ModelOutcome(
                    description,
                    content,
                    null
            );
        }

        private static ModelOutcome failure(
                String description,
                RuntimeException failure
        ) {
            return new ModelOutcome(
                    description,
                    null,
                    failure
            );
        }

        @Override
        public String toString() {
            return description;
        }
    }
}
