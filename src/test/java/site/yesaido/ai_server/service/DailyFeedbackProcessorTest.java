package site.yesaido.ai_server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.yesaido.ai_server.dto.client.mushroom_reference.MushroomReferenceInfoResponse;
import site.yesaido.ai_server.dto.client.sensor.snapshot.DataGeneratorSnapshotResponse;
import site.yesaido.ai_server.dto.cultivation.DailyCultivationPhotoResponse;
import site.yesaido.ai_server.dto.daily_feedback.DailyFeedbackContext;
import site.yesaido.ai_server.dto.daily_feedback.DailyNotificationMetrics;
import site.yesaido.ai_server.dto.daily_feedback.DailyVisionAnalysisSnapshot;
import site.yesaido.ai_server.entity.DailyFeedback;
import site.yesaido.ai_server.exception.DailyFeedbackProcessingException;
import site.yesaido.ai_server.service.DailyFeedbackPersistenceService.PersistenceResult;
import site.yesaido.ai_server.service.DailyFeedbackPersistenceService.PersistenceStatus;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyFeedbackProcessorTest {

    private static final Long CULTIVATION_ID = 10L;
    private static final Long OWNER_USER_ID = 20L;
    private static final LocalDate FEEDBACK_DATE =
            LocalDate.of(2026, 9, 1);

    private static final String GENERATED_CONTENT =
            "검증을 통과한 일일 피드백입니다.";

    private static final String USER_MESSAGE =
            "일일 피드백을 처리하지 못했습니다.";

    private static final String SENSITIVE_FAILURE_DETAIL =
            "https://processor-test.invalid/image"
                    + "?X-Amz-Signature=fake-sensitive-signature";

    private static final String RAW_PRESIGNED_URL =
            "https://snapshot-test.invalid/image"
                    + "?X-Amz-Signature=fake-snapshot-signature";

    private static final DataGeneratorSnapshotResponse SNAPSHOT =
            new DataGeneratorSnapshotResponse(
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
                    List.of(),
                    List.of()
            );

    private static final Map<Long, MushroomReferenceInfoResponse>
            REFERENCES_BY_ID = Map.of();

    private static final Map<Long, DailyNotificationMetrics>
            NOTIFICATION_METRICS_BY_CULTIVATION_ID = Map.of();

    private static final Map<Long, DailyCultivationPhotoResponse>
            PHOTOS_BY_CULTIVATION_ID = Map.of();

    @Mock
    private DailyFeedbackContextCollector contextCollector;

    @Mock
    private DailyFeedbackGenerationService generationService;

    @Mock
    private DailyFeedbackPersistenceService persistenceService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private DailyFeedbackContext context;

    private DailyFeedbackProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new DailyFeedbackProcessor(
                contextCollector,
                generationService,
                persistenceService,
                objectMapper
        );
    }

    @Test
    @DisplayName("기존 피드백이 있으면 가장 먼저 조회하고 나머지 처리를 생략한다")
    void returnsExistingFeedbackWithoutRunningNewProcessing() {
        // 준비
        DailyFeedback existingFeedback =
                feedback("기존에 저장된 일일 피드백입니다.");

        when(
                persistenceService.findExisting(
                        CULTIVATION_ID,
                        FEEDBACK_DATE
                )
        ).thenReturn(Optional.of(existingFeedback));

        // 실행
        PersistenceResult result = processValidInputs();

        // 검증
        assertThat(result.feedback())
                .isSameAs(existingFeedback);

        assertThat(result.status())
                .isEqualTo(PersistenceStatus.EXISTING);

        InOrder inOrder = inOrder(
                persistenceService,
                contextCollector,
                objectMapper,
                generationService
        );

        inOrder.verify(persistenceService)
                .findExisting(
                        CULTIVATION_ID,
                        FEEDBACK_DATE
                );

        inOrder.verifyNoMoreInteractions();

        verifyNoInteractions(
                contextCollector,
                objectMapper,
                generationService
        );

        verify(
                persistenceService,
                never()
        ).saveOrGet(any(DailyFeedback.class));
    }

    @Test
    @DisplayName("신규 피드백은 Context 수집부터 멱등 저장까지 정해진 순서로 처리한다")
    void createsAndPersistsNewFeedbackInOrder() {
        // 준비
        ObjectNode rawContextSnapshot = rawContextSnapshot();
        JsonNode expectedContextSnapshot =
                rawContextSnapshot.deepCopy();

        DailyVisionAnalysisSnapshot visionAnalysis =
                analyzedVisionSnapshot();

        DailyFeedback savedFeedback =
                feedback("DB에서 확정된 신규 피드백입니다.");

        PersistenceResult persistenceResult =
                new PersistenceResult(
                        savedFeedback,
                        PersistenceStatus.CREATED
                );

        stubSuccessfulProcessing(
                rawContextSnapshot,
                GENERATED_CONTENT,
                visionAnalysis,
                persistenceResult
        );

        ArgumentCaptor<DailyFeedback> feedbackCaptor =
                ArgumentCaptor.forClass(DailyFeedback.class);

        // 실행
        PersistenceResult result = processValidInputs();

        // 검증
        InOrder inOrder = inOrder(
                persistenceService,
                contextCollector,
                objectMapper,
                generationService
        );

        inOrder.verify(persistenceService)
                .findExisting(
                        CULTIVATION_ID,
                        FEEDBACK_DATE
                );

        inOrder.verify(contextCollector)
                .collect(
                        FEEDBACK_DATE,
                        CULTIVATION_ID,
                        OWNER_USER_ID,
                        SNAPSHOT,
                        REFERENCES_BY_ID,
                        NOTIFICATION_METRICS_BY_CULTIVATION_ID,
                        PHOTOS_BY_CULTIVATION_ID
                );

        inOrder.verify(objectMapper)
                .<JsonNode>valueToTree(context);

        inOrder.verify(generationService)
                .generate(context);

        inOrder.verify(persistenceService)
                .saveOrGet(feedbackCaptor.capture());

        inOrder.verifyNoMoreInteractions();

        DailyFeedback candidate = feedbackCaptor.getValue();

        assertThat(candidate.getCultivationId())
                .isEqualTo(CULTIVATION_ID);

        assertThat(candidate.getFeedbackDate())
                .isEqualTo(FEEDBACK_DATE);

        assertThat(candidate.getContent())
                .isEqualTo(GENERATED_CONTENT);

        assertThat(candidate.isHasVisionAnalysis())
                .isEqualTo(
                        visionAnalysis.hasVisionAnalysis()
                );

        assertThat(candidate.getContextSnapshot())
                .isEqualTo(expectedContextSnapshot)
                .isNotSameAs(rawContextSnapshot);

        assertThat(
                candidate.getContextSnapshot()
                        .path("cultivationId")
                        .asLong()
        ).isEqualTo(CULTIVATION_ID);

        assertThat(
                candidate.getContextSnapshot()
                        .path("presignedUrl")
                        .asText()
        ).isEqualTo(RAW_PRESIGNED_URL);

        rawContextSnapshot.remove("presignedUrl");
        rawContextSnapshot.put("changedAfterProcessing", true);

        assertThat(candidate.getContextSnapshot())
                .isEqualTo(expectedContextSnapshot);

        assertThat(result)
                .isSameAs(persistenceResult);
    }

    @Test
    @DisplayName("저장 경쟁으로 기존 행이 반환되면 EXISTING 결과를 그대로 반환한다")
    void preservesExistingResultReturnedAfterSaveRace() {
        // 준비
        ObjectNode rawContextSnapshot = rawContextSnapshot();

        DailyFeedback concurrentlySaved =
                feedback("다른 실행에서 먼저 저장한 피드백입니다.");

        PersistenceResult existingResult =
                new PersistenceResult(
                        concurrentlySaved,
                        PersistenceStatus.EXISTING
                );

        stubSuccessfulProcessing(
                rawContextSnapshot,
                GENERATED_CONTENT,
                DailyVisionAnalysisSnapshot.withoutPhoto(
                        CULTIVATION_ID
                ),
                existingResult
        );

        // 실행
        PersistenceResult result = processValidInputs();

        // 검증
        assertThat(result)
                .isSameAs(existingResult);

        assertThat(result.feedback())
                .isSameAs(concurrentlySaved);

        assertThat(result.status())
                .isEqualTo(PersistenceStatus.EXISTING);

        verify(persistenceService)
                .saveOrGet(any(DailyFeedback.class));
    }

    @Test
    @DisplayName("Context JSON 변환 중 RuntimeException이 발생하면 안전한 처리 예외로 변환한다")
    void wrapsContextSerializationFailureWithoutSensitiveInformation() {
        // 준비
        stubUntilContextSerialization();

        RuntimeException originalException =
                new IllegalStateException(
                        SENSITIVE_FAILURE_DETAIL
                );

        when(
                objectMapper
                        .<JsonNode>valueToTree(context)
        ).thenThrow(originalException);

        // 실행
        DailyFeedbackProcessingException exception =
                catchThrowableOfType(
                        DailyFeedbackProcessingException.class,
                        this::processValidInputs
                );

        // 검증
        assertThat(exception)
                .hasMessage(USER_MESSAGE)
                .hasNoCause();

        String logContent = exception.getLogContent();

        assertThat(logContent)
                .isEqualTo(
                        "일일 피드백 처리 실패: "
                                + "cultivationId=10, "
                                + "feedbackDate=2026-09-01, "
                                + "reason="
                                + "CONTEXT_SNAPSHOT_SERIALIZATION_FAILED, "
                                + "exceptionType=IllegalStateException"
                )
                .doesNotContain(SENSITIVE_FAILURE_DETAIL)
                .doesNotContain("https://")
                .doesNotContain("X-Amz-Signature")
                .doesNotContain("fake-sensitive-signature");

        verifyNoInteractions(generationService);

        verify(
                persistenceService,
                never()
        ).saveOrGet(any(DailyFeedback.class));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidContextSnapshotCases")
    @DisplayName("null 또는 JSON object가 아닌 Context Snapshot을 거부한다")
    void rejectsInvalidContextSnapshot(
            InvalidContextSnapshotCase invalidCase
    ) {
        // 준비
        stubUntilContextSerialization();

        when(
                objectMapper
                        .<JsonNode>valueToTree(context)
        ).thenReturn(invalidCase.snapshot());

        // 실행
        DailyFeedbackProcessingException exception =
                catchThrowableOfType(
                        DailyFeedbackProcessingException.class,
                        this::processValidInputs
                );

        // 검증
        assertThat(exception)
                .hasMessage(USER_MESSAGE)
                .hasNoCause();

        assertThat(exception.getLogContent())
                .isEqualTo(
                        "일일 피드백 처리 실패: "
                                + "cultivationId=10, "
                                + "feedbackDate=2026-09-01, "
                                + "reason=INVALID_CONTEXT_SNAPSHOT, "
                                + "exceptionType=NONE"
                );

        verifyNoInteractions(generationService);

        verify(
                persistenceService,
                never()
        ).saveOrGet(any(DailyFeedback.class));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidInputCases")
    @DisplayName("유효하지 않은 입력은 의존 서비스를 호출하기 전에 거부한다")
    void rejectsInvalidInputsBeforeCallingDependencies(
            InvalidInputCase invalidCase
    ) {
        // 준비
        // 각 케이스가 하나의 입력만 잘못된 값으로 제공합니다.

        // 실행
        IllegalArgumentException exception =
                catchThrowableOfType(
                        IllegalArgumentException.class,
                        () -> processor.process(
                                invalidCase.feedbackDate(),
                                invalidCase.cultivationId(),
                                invalidCase.ownerUserId(),
                                invalidCase.snapshot(),
                                invalidCase.referencesById(),
                                invalidCase.notificationMetricsByCultivationId(),
                                invalidCase.photosByCultivationId()
                        )
                );

        // 검증
        assertThat(exception)
                .hasMessage(invalidCase.expectedMessage());

        verifyNoInteractions(
                contextCollector,
                generationService,
                persistenceService,
                objectMapper
        );
    }

    @Test
    @DisplayName("process 메서드는 기존 트랜잭션을 중단하는 NOT_SUPPORTED 정책을 사용한다")
    void usesNotSupportedTransactionPropagation()
            throws NoSuchMethodException {
        // 준비
        Method processMethod =
                DailyFeedbackProcessor.class.getDeclaredMethod(
                        "process",
                        LocalDate.class,
                        Long.class,
                        Long.class,
                        DataGeneratorSnapshotResponse.class,
                        Map.class,
                        Map.class,
                        Map.class
                );

        // 실행
        Transactional transactional =
                processMethod.getAnnotation(
                        Transactional.class
                );

        // 검증
        assertThat(transactional)
                .isNotNull();

        assertThat(transactional.propagation())
                .isEqualTo(Propagation.NOT_SUPPORTED);
    }

    private void stubUntilContextSerialization() {
        when(
                persistenceService.findExisting(
                        CULTIVATION_ID,
                        FEEDBACK_DATE
                )
        ).thenReturn(Optional.empty());

        when(
                contextCollector.collect(
                        FEEDBACK_DATE,
                        CULTIVATION_ID,
                        OWNER_USER_ID,
                        SNAPSHOT,
                        REFERENCES_BY_ID,
                        NOTIFICATION_METRICS_BY_CULTIVATION_ID,
                        PHOTOS_BY_CULTIVATION_ID
                )
        ).thenReturn(context);

        when(context.cultivationId())
                .thenReturn(CULTIVATION_ID);

        when(context.feedbackDate())
                .thenReturn(FEEDBACK_DATE);
    }

    private void stubSuccessfulProcessing(
            JsonNode rawContextSnapshot,
            String generatedContent,
            DailyVisionAnalysisSnapshot visionAnalysis,
            PersistenceResult persistenceResult
    ) {
        stubUntilContextSerialization();

        when(context.visionAnalysis())
                .thenReturn(visionAnalysis);

        when(
                objectMapper
                        .<JsonNode>valueToTree(context)
        ).thenReturn(rawContextSnapshot);

        when(generationService.generate(context))
                .thenReturn(generatedContent);

        when(
                persistenceService.saveOrGet(
                        any(DailyFeedback.class)
                )
        ).thenReturn(persistenceResult);
    }

    private PersistenceResult processValidInputs() {
        return processor.process(
                FEEDBACK_DATE,
                CULTIVATION_ID,
                OWNER_USER_ID,
                SNAPSHOT,
                REFERENCES_BY_ID,
                NOTIFICATION_METRICS_BY_CULTIVATION_ID,
                PHOTOS_BY_CULTIVATION_ID
        );
    }

    private static ObjectNode rawContextSnapshot() {
        ObjectNode contextSnapshot =
                JsonNodeFactory.instance.objectNode();

        contextSnapshot.put(
                "cultivationId",
                CULTIVATION_ID
        );

        contextSnapshot.put(
                "feedbackDate",
                FEEDBACK_DATE.toString()
        );

        contextSnapshot.put(
                "presignedUrl",
                RAW_PRESIGNED_URL
        );

        contextSnapshot
                .putObject("cultivationDetail")
                .put("name", "테스트 재배지");

        return contextSnapshot;
    }

    private static DailyVisionAnalysisSnapshot
    analyzedVisionSnapshot() {
        ObjectNode analysisData =
                JsonNodeFactory.instance.objectNode();

        analysisData.put(
                "healthStatus",
                "HEALTHY"
        );

        return DailyVisionAnalysisSnapshot.analyzed(
                CULTIVATION_ID,
                501L,
                601L,
                analysisData,
                LocalDateTime.of(
                        2026,
                        9,
                        2,
                        0,
                        6
                )
        );
    }

    private static DailyFeedback feedback(String content) {
        ObjectNode contextSnapshot =
                JsonNodeFactory.instance.objectNode();

        contextSnapshot.put(
                "fixture",
                "daily-feedback-processor-test"
        );

        return DailyFeedback.builder()
                .cultivationId(CULTIVATION_ID)
                .feedbackDate(FEEDBACK_DATE)
                .hasVisionAnalysis(false)
                .content(content)
                .contextSnapshot(contextSnapshot)
                .build();
    }

    private static Stream<InvalidContextSnapshotCase>
    invalidContextSnapshotCases() {
        return Stream.of(
                new InvalidContextSnapshotCase(
                        "Java null 변환 결과",
                        null
                ),
                new InvalidContextSnapshotCase(
                        "JSON null 변환 결과",
                        NullNode.getInstance()
                ),
                new InvalidContextSnapshotCase(
                        "JSON object가 아닌 문자열 변환 결과",
                        TextNode.valueOf("not-an-object")
                )
        );
    }

    private static Stream<InvalidInputCase>
    invalidInputCases() {
        return Stream.of(
                new InvalidInputCase(
                        "feedbackDate가 null인 경우",
                        null,
                        CULTIVATION_ID,
                        OWNER_USER_ID,
                        SNAPSHOT,
                        REFERENCES_BY_ID,
                        NOTIFICATION_METRICS_BY_CULTIVATION_ID,
                        PHOTOS_BY_CULTIVATION_ID,
                        "feedbackDate는 null일 수 없습니다."
                ),
                new InvalidInputCase(
                        "cultivationId가 null인 경우",
                        FEEDBACK_DATE,
                        null,
                        OWNER_USER_ID,
                        SNAPSHOT,
                        REFERENCES_BY_ID,
                        NOTIFICATION_METRICS_BY_CULTIVATION_ID,
                        PHOTOS_BY_CULTIVATION_ID,
                        "cultivationId는 null이 아니며 0보다 커야 합니다."
                ),
                new InvalidInputCase(
                        "cultivationId가 0인 경우",
                        FEEDBACK_DATE,
                        0L,
                        OWNER_USER_ID,
                        SNAPSHOT,
                        REFERENCES_BY_ID,
                        NOTIFICATION_METRICS_BY_CULTIVATION_ID,
                        PHOTOS_BY_CULTIVATION_ID,
                        "cultivationId는 null이 아니며 0보다 커야 합니다."
                ),
                new InvalidInputCase(
                        "cultivationId가 음수인 경우",
                        FEEDBACK_DATE,
                        -1L,
                        OWNER_USER_ID,
                        SNAPSHOT,
                        REFERENCES_BY_ID,
                        NOTIFICATION_METRICS_BY_CULTIVATION_ID,
                        PHOTOS_BY_CULTIVATION_ID,
                        "cultivationId는 null이 아니며 0보다 커야 합니다."
                ),
                new InvalidInputCase(
                        "ownerUserId가 null인 경우",
                        FEEDBACK_DATE,
                        CULTIVATION_ID,
                        null,
                        SNAPSHOT,
                        REFERENCES_BY_ID,
                        NOTIFICATION_METRICS_BY_CULTIVATION_ID,
                        PHOTOS_BY_CULTIVATION_ID,
                        "ownerUserId는 null이 아니며 0보다 커야 합니다."
                ),
                new InvalidInputCase(
                        "ownerUserId가 0인 경우",
                        FEEDBACK_DATE,
                        CULTIVATION_ID,
                        0L,
                        SNAPSHOT,
                        REFERENCES_BY_ID,
                        NOTIFICATION_METRICS_BY_CULTIVATION_ID,
                        PHOTOS_BY_CULTIVATION_ID,
                        "ownerUserId는 null이 아니며 0보다 커야 합니다."
                ),
                new InvalidInputCase(
                        "ownerUserId가 음수인 경우",
                        FEEDBACK_DATE,
                        CULTIVATION_ID,
                        -1L,
                        SNAPSHOT,
                        REFERENCES_BY_ID,
                        NOTIFICATION_METRICS_BY_CULTIVATION_ID,
                        PHOTOS_BY_CULTIVATION_ID,
                        "ownerUserId는 null이 아니며 0보다 커야 합니다."
                ),
                new InvalidInputCase(
                        "snapshot이 null인 경우",
                        FEEDBACK_DATE,
                        CULTIVATION_ID,
                        OWNER_USER_ID,
                        null,
                        REFERENCES_BY_ID,
                        NOTIFICATION_METRICS_BY_CULTIVATION_ID,
                        PHOTOS_BY_CULTIVATION_ID,
                        "snapshot은 null일 수 없습니다."
                ),
                new InvalidInputCase(
                        "referencesById가 null인 경우",
                        FEEDBACK_DATE,
                        CULTIVATION_ID,
                        OWNER_USER_ID,
                        SNAPSHOT,
                        null,
                        NOTIFICATION_METRICS_BY_CULTIVATION_ID,
                        PHOTOS_BY_CULTIVATION_ID,
                        "referencesById는 null일 수 없습니다."
                ),
                new InvalidInputCase(
                        "notificationMetricsByCultivationId가 null인 경우",
                        FEEDBACK_DATE,
                        CULTIVATION_ID,
                        OWNER_USER_ID,
                        SNAPSHOT,
                        REFERENCES_BY_ID,
                        null,
                        PHOTOS_BY_CULTIVATION_ID,
                        "notificationMetricsByCultivationId는 null일 수 없습니다."
                ),
                new InvalidInputCase(
                        "photosByCultivationId가 null인 경우",
                        FEEDBACK_DATE,
                        CULTIVATION_ID,
                        OWNER_USER_ID,
                        SNAPSHOT,
                        REFERENCES_BY_ID,
                        NOTIFICATION_METRICS_BY_CULTIVATION_ID,
                        null,
                        "photosByCultivationId는 null일 수 없습니다."
                )
        );
    }

    private record InvalidContextSnapshotCase(
            String description,
            JsonNode snapshot
    ) {

        @Override
        public String toString() {
            return description;
        }
    }

    private record InvalidInputCase(
            String description,
            LocalDate feedbackDate,
            Long cultivationId,
            Long ownerUserId,
            DataGeneratorSnapshotResponse snapshot,
            Map<Long, MushroomReferenceInfoResponse> referencesById,
            Map<Long, DailyNotificationMetrics>
            notificationMetricsByCultivationId,
            Map<Long, DailyCultivationPhotoResponse>
            photosByCultivationId,
            String expectedMessage
    ) {

        @Override
        public String toString() {
            return description;
        }
    }
}
