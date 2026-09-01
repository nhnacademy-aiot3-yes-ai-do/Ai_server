package site.yesaido.ai_server.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.yesaido.ai_server.client.CultivationClient;
import site.yesaido.ai_server.dto.client.mushroom_reference.MushroomReferenceInfoResponse;
import site.yesaido.ai_server.dto.client.sensor.snapshot.DataGeneratorSnapshotResponse;
import site.yesaido.ai_server.dto.cultivation.DailyCultivationPhotoResponse;
import site.yesaido.ai_server.dto.daily_feedback.DailyFeedbackBatchResult;
import site.yesaido.ai_server.dto.daily_feedback.DailyFeedbackBatchResult.CultivationResult;
import site.yesaido.ai_server.dto.daily_feedback.DailyFeedbackBatchResult.CultivationStatus;
import site.yesaido.ai_server.dto.daily_feedback.DailyFeedbackBatchResult.FailureStage;
import site.yesaido.ai_server.dto.daily_feedback.DailyNotificationMetrics;
import site.yesaido.ai_server.entity.DailyFeedback;
import site.yesaido.ai_server.service.DailyFeedbackPersistenceService.PersistenceResult;
import site.yesaido.ai_server.service.DailyFeedbackPersistenceService.PersistenceStatus;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyFeedbackBatchServiceTest {

    private static final LocalDate FEEDBACK_DATE =
            LocalDate.of(2026, 9, 1);

    private static final OffsetDateTime SNAPSHOT_AT =
            OffsetDateTime.of(
                    2026,
                    9,
                    2,
                    0,
                    5,
                    0,
                    0,
                    ZoneOffset.ofHours(9)
            );

    private static final Long FIRST_CULTIVATION_ID = 10L;
    private static final Long SECOND_CULTIVATION_ID = 20L;
    private static final Long EXTRA_CULTIVATION_ID = 999L;

    private static final Long FIRST_OWNER_USER_ID = 110L;
    private static final Long SECOND_OWNER_USER_ID = 120L;

    private static final List<Long> TARGET_IDS =
            List.of(
                    FIRST_CULTIVATION_ID,
                    SECOND_CULTIVATION_ID
            );

    private static final DataGeneratorSnapshotResponse SNAPSHOT =
            new DataGeneratorSnapshotResponse(
                    SNAPSHOT_AT,
                    List.of(),
                    List.of()
            );

    private static final Map<Long, MushroomReferenceInfoResponse>
            REFERENCES_BY_ID = Map.of();

    private static final Map<Long, DailyNotificationMetrics>
            NOTIFICATION_METRICS_BY_CULTIVATION_ID = Map.of();

    private static final String SENSITIVE_FAILURE_MESSAGE =
            "https://batch-test.invalid/image"
                    + "?X-Amz-Signature=fake-sensitive-signature";

    private static final DailyCultivationPhotoResponse TARGET_PHOTO =
            new DailyCultivationPhotoResponse(
                    FIRST_CULTIVATION_ID,
                    1_010L,
                    "https://target-photo.invalid/image"
                            + "?X-Amz-Signature=fake-target-signature",
                    SNAPSHOT_AT.plusMinutes(30)
            );

    private static final DailyCultivationPhotoResponse EXTRA_PHOTO =
            new DailyCultivationPhotoResponse(
                    EXTRA_CULTIVATION_ID,
                    1_999L,
                    "https://extra-photo.invalid/image"
                            + "?X-Amz-Signature=fake-extra-signature",
                    SNAPSHOT_AT.plusMinutes(30)
            );

    @Mock
    private CultivationClient cultivationClient;

    @Mock
    private DailyFeedbackTargetResolver targetResolver;

    @Mock
    private DailyMushroomReferenceService mushroomReferenceService;

    @Mock
    private DailyNotificationMetricsService notificationMetricsService;

    @Mock
    private DailyVisionAnalysisService dailyVisionAnalysisService;

    @Mock
    private CultivationOwnerService cultivationOwnerService;

    @Mock
    private DailyFeedbackProcessor processor;

    @Captor
    private ArgumentCaptor<Map<Long, DailyCultivationPhotoResponse>>
            photosMapCaptor;

    private DailyFeedbackBatchService service;

    @BeforeEach
    void setUp() {
        service = new DailyFeedbackBatchService(
                cultivationClient,
                targetResolver,
                mushroomReferenceService,
                notificationMetricsService,
                dailyVisionAnalysisService,
                cultivationOwnerService,
                processor
        );
    }

    @Test
    @DisplayName("feedbackDate가 null이면 모든 의존성을 호출하지 않는다")
    void rejectsNullFeedbackDateBeforeCallingDependencies() {
        // 준비
        // null 입력 외에 별도의 준비는 필요하지 않습니다.

        // 실행
        IllegalArgumentException exception =
                catchThrowableOfType(
                        IllegalArgumentException.class,
                        () -> service.execute(null)
                );

        // 검증
        assertThat(exception)
                .hasMessage(
                        "feedbackDate는 null일 수 없습니다."
                );

        verifyNoInteractions(
                cultivationClient,
                targetResolver,
                mushroomReferenceService,
                notificationMetricsService,
                dailyVisionAnalysisService,
                cultivationOwnerService,
                processor
        );
    }

    @Test
    @DisplayName("Snapshot 최상위 응답이 null이면 이후 처리를 시작하지 않는다")
    void rejectsNullSnapshotResponse() {
        // 준비
        when(
                cultivationClient.getDataGeneratorSnapshot()
        ).thenReturn(
                (DataGeneratorSnapshotResponse) null
        );

        // 실행
        IllegalStateException exception =
                catchThrowableOfType(
                        IllegalStateException.class,
                        () -> service.execute(FEEDBACK_DATE)
                );

        // 검증
        assertThat(exception)
                .hasMessage(
                        "Data Generator Snapshot 응답이 null입니다."
                );

        verify(cultivationClient)
                .getDataGeneratorSnapshot();

        verifyNoInteractions(
                targetResolver,
                mushroomReferenceService,
                notificationMetricsService,
                dailyVisionAnalysisService,
                cultivationOwnerService,
                processor
        );
    }

    @Test
    @DisplayName("대상 경작지가 없으면 공통 추가 조회 없이 빈 배치 결과를 반환한다")
    void returnsEmptyBatchResultWhenNoTargetsExist() {
        // 준비
        when(
                cultivationClient.getDataGeneratorSnapshot()
        ).thenReturn(SNAPSHOT);

        when(
                targetResolver.resolveCultivationIds(SNAPSHOT)
        ).thenReturn(List.of());

        // 실행
        DailyFeedbackBatchResult result =
                service.execute(FEEDBACK_DATE);

        // 검증
        assertThat(result.feedbackDate())
                .isEqualTo(FEEDBACK_DATE);

        assertThat(result.snapshotAt())
                .isEqualTo(SNAPSHOT_AT);

        assertThat(result.targetCount())
                .isZero();

        assertThat(result.createdCount())
                .isZero();

        assertThat(result.existingCount())
                .isZero();

        assertThat(result.failedCount())
                .isZero();

        assertThat(result.results())
                .isEmpty();

        assertThatThrownBy(
                () -> result.results().add(
                        CultivationResult.created(
                                FIRST_CULTIVATION_ID
                        )
                )
        ).isInstanceOf(
                UnsupportedOperationException.class
        );

        InOrder inOrder = inOrder(
                cultivationClient,
                targetResolver
        );

        inOrder.verify(cultivationClient)
                .getDataGeneratorSnapshot();

        inOrder.verify(targetResolver)
                .resolveCultivationIds(SNAPSHOT);

        inOrder.verifyNoMoreInteractions();

        verifyNoInteractions(
                mushroomReferenceService,
                notificationMetricsService,
                dailyVisionAnalysisService,
                cultivationOwnerService,
                processor
        );
    }

    @Test
    @DisplayName("공통 데이터를 한 번씩 조회하고 대상 순서대로 CREATED와 EXISTING을 매핑한다")
    void processesMixedBatchInDeterministicOrder() {
        // 준비
        stubSuccessfulCommonData(
                TARGET_IDS,
                Map.of()
        );

        PersistenceResult createdResult =
                validPersistenceResult(
                        1_001L,
                        FIRST_CULTIVATION_ID,
                        FEEDBACK_DATE,
                        PersistenceStatus.CREATED
                );

        PersistenceResult existingResult =
                validPersistenceResult(
                        1_002L,
                        SECOND_CULTIVATION_ID,
                        FEEDBACK_DATE,
                        PersistenceStatus.EXISTING
                );

        stubOwnerAndProcessor(
                FIRST_CULTIVATION_ID,
                FIRST_OWNER_USER_ID,
                createdResult
        );

        stubOwnerAndProcessor(
                SECOND_CULTIVATION_ID,
                SECOND_OWNER_USER_ID,
                existingResult
        );

        // 실행
        DailyFeedbackBatchResult result =
                service.execute(FEEDBACK_DATE);

        // 검증
        assertThat(result.feedbackDate())
                .isEqualTo(FEEDBACK_DATE);

        assertThat(result.snapshotAt())
                .isEqualTo(SNAPSHOT_AT);

        assertThat(result.targetCount())
                .isEqualTo(2);

        assertThat(result.createdCount())
                .isEqualTo(1);

        assertThat(result.existingCount())
                .isEqualTo(1);

        assertThat(result.failedCount())
                .isZero();

        assertThat(result.results())
                .containsExactly(
                        CultivationResult.created(
                                FIRST_CULTIVATION_ID
                        ),
                        CultivationResult.existing(
                                SECOND_CULTIVATION_ID
                        )
                );

        verifyCommonCallOrder(TARGET_IDS);

        InOrder targetOrder = inOrder(
                cultivationOwnerService,
                processor
        );

        targetOrder.verify(cultivationOwnerService)
                .findOwnerUserId(
                        FIRST_CULTIVATION_ID
                );

        targetOrder.verify(processor)
                .process(
                        eq(FEEDBACK_DATE),
                        eq(FIRST_CULTIVATION_ID),
                        eq(FIRST_OWNER_USER_ID),
                        same(SNAPSHOT),
                        same(REFERENCES_BY_ID),
                        same(
                                NOTIFICATION_METRICS_BY_CULTIVATION_ID
                        ),
                        anyMap()
                );

        targetOrder.verify(cultivationOwnerService)
                .findOwnerUserId(
                        SECOND_CULTIVATION_ID
                );

        targetOrder.verify(processor)
                .process(
                        eq(FEEDBACK_DATE),
                        eq(SECOND_CULTIVATION_ID),
                        eq(SECOND_OWNER_USER_ID),
                        same(SNAPSHOT),
                        same(REFERENCES_BY_ID),
                        same(
                                NOTIFICATION_METRICS_BY_CULTIVATION_ID
                        ),
                        anyMap()
                );

        targetOrder.verifyNoMoreInteractions();
    }

    @Test
    @DisplayName("Processor에는 대상 사진 교집합의 동일한 불변 Map만 전달한다")
    void passesSameImmutableTargetPhotoIntersectionToProcessors() {
        // 준비
        Map<Long, DailyCultivationPhotoResponse>
                allPhotosByCultivationId =
                new LinkedHashMap<>();

        allPhotosByCultivationId.put(
                EXTRA_CULTIVATION_ID,
                EXTRA_PHOTO
        );

        allPhotosByCultivationId.put(
                FIRST_CULTIVATION_ID,
                TARGET_PHOTO
        );

        stubSuccessfulCommonData(
                TARGET_IDS,
                allPhotosByCultivationId
        );

        stubOwnerAndProcessor(
                FIRST_CULTIVATION_ID,
                FIRST_OWNER_USER_ID,
                validPersistenceResult(
                        1_011L,
                        FIRST_CULTIVATION_ID,
                        FEEDBACK_DATE,
                        PersistenceStatus.CREATED
                )
        );

        stubOwnerAndProcessor(
                SECOND_CULTIVATION_ID,
                SECOND_OWNER_USER_ID,
                validPersistenceResult(
                        1_012L,
                        SECOND_CULTIVATION_ID,
                        FEEDBACK_DATE,
                        PersistenceStatus.EXISTING
                )
        );

        // 실행
        DailyFeedbackBatchResult result =
                service.execute(FEEDBACK_DATE);

        // 검증
        verify(
                processor,
                times(2)
        ).process(
                eq(FEEDBACK_DATE),
                anyLong(),
                anyLong(),
                same(SNAPSHOT),
                same(REFERENCES_BY_ID),
                same(
                        NOTIFICATION_METRICS_BY_CULTIVATION_ID
                ),
                photosMapCaptor.capture()
        );

        List<Map<Long, DailyCultivationPhotoResponse>>
                passedPhotoMaps =
                photosMapCaptor.getAllValues();

        assertThat(passedPhotoMaps)
                .hasSize(2);

        Map<Long, DailyCultivationPhotoResponse>
                firstPassedMap =
                passedPhotoMaps.getFirst();

        Map<Long, DailyCultivationPhotoResponse>
                secondPassedMap =
                passedPhotoMaps.get(1);

        assertThat(secondPassedMap)
                .isSameAs(firstPassedMap);

        assertThat(firstPassedMap.keySet())
                .containsExactly(
                        FIRST_CULTIVATION_ID
                );

        assertThat(
                firstPassedMap.get(
                        FIRST_CULTIVATION_ID
                )
        ).isSameAs(TARGET_PHOTO);

        assertThat(firstPassedMap)
                .doesNotContainKeys(
                        SECOND_CULTIVATION_ID,
                        EXTRA_CULTIVATION_ID
                );

        assertThatThrownBy(
                () -> firstPassedMap.put(
                        SECOND_CULTIVATION_ID,
                        TARGET_PHOTO
                )
        ).isInstanceOf(
                UnsupportedOperationException.class
        );

        assertThat(result.targetCount())
                .isEqualTo(2);

        assertThat(result.createdCount())
                .isEqualTo(1);

        assertThat(result.existingCount())
                .isEqualTo(1);

        assertThat(result.results())
                .hasSize(2);

        assertThat(result.toString())
                .doesNotContain(
                        TARGET_PHOTO.presignedUrl()
                )
                .doesNotContain(
                        EXTRA_PHOTO.presignedUrl()
                )
                .doesNotContain("X-Amz-Signature");

        verify(cultivationOwnerService)
                .findOwnerUserId(
                        SECOND_CULTIVATION_ID
                );

        verify(
                cultivationOwnerService,
                never()
        ).findOwnerUserId(
                EXTRA_CULTIVATION_ID
        );
    }

    @Test
    @DisplayName("OWNER 조회 실패를 격리하고 다음 경작지를 계속 처리한다")
    void isolatesOwnerResolutionFailure() {
        // 준비
        stubSuccessfulCommonData(
                TARGET_IDS,
                Map.of()
        );

        RuntimeException ownerFailure =
                new IllegalStateException(
                        SENSITIVE_FAILURE_MESSAGE
                );

        when(
                cultivationOwnerService.findOwnerUserId(
                        FIRST_CULTIVATION_ID
                )
        ).thenThrow(ownerFailure);

        stubOwnerAndProcessor(
                SECOND_CULTIVATION_ID,
                SECOND_OWNER_USER_ID,
                validPersistenceResult(
                        1_021L,
                        SECOND_CULTIVATION_ID,
                        FEEDBACK_DATE,
                        PersistenceStatus.CREATED
                )
        );

        // 실행
        DailyFeedbackBatchResult result =
                service.execute(FEEDBACK_DATE);

        // 검증
        assertThat(result.targetCount())
                .isEqualTo(2);

        assertThat(result.createdCount())
                .isEqualTo(1);

        assertThat(result.existingCount())
                .isZero();

        assertThat(result.failedCount())
                .isEqualTo(1);

        CultivationResult failedResult =
                result.results().getFirst();

        assertThat(failedResult.cultivationId())
                .isEqualTo(FIRST_CULTIVATION_ID);

        assertThat(failedResult.status())
                .isEqualTo(CultivationStatus.FAILED);

        assertThat(failedResult.failureStage())
                .isEqualTo(
                        FailureStage.OWNER_RESOLUTION
                );

        assertThat(failedResult.exceptionType())
                .isEqualTo("IllegalStateException")
                .doesNotContain(SENSITIVE_FAILURE_MESSAGE)
                .doesNotContain("https://")
                .doesNotContain("X-Amz-Signature");

        assertThat(result.results().get(1))
                .isEqualTo(
                        CultivationResult.created(
                                SECOND_CULTIVATION_ID
                        )
                );

        assertThat(result.toString())
                .doesNotContain(SENSITIVE_FAILURE_MESSAGE)
                .doesNotContain("https://")
                .doesNotContain("X-Amz-Signature");

        verify(
                processor,
                never()
        ).process(
                eq(FEEDBACK_DATE),
                eq(FIRST_CULTIVATION_ID),
                anyLong(),
                same(SNAPSHOT),
                same(REFERENCES_BY_ID),
                same(
                        NOTIFICATION_METRICS_BY_CULTIVATION_ID
                ),
                anyMap()
        );

        verify(processor)
                .process(
                        eq(FEEDBACK_DATE),
                        eq(SECOND_CULTIVATION_ID),
                        eq(SECOND_OWNER_USER_ID),
                        same(SNAPSHOT),
                        same(REFERENCES_BY_ID),
                        same(
                                NOTIFICATION_METRICS_BY_CULTIVATION_ID
                        ),
                        anyMap()
                );
    }

    @Test
    @DisplayName("Processor 실패를 격리하고 다음 경작지의 OWNER와 Processor를 계속 호출한다")
    void isolatesProcessorFailure() {
        // 준비
        stubSuccessfulCommonData(
                TARGET_IDS,
                Map.of()
        );

        when(
                cultivationOwnerService.findOwnerUserId(
                        FIRST_CULTIVATION_ID
                )
        ).thenReturn(FIRST_OWNER_USER_ID);

        RuntimeException processorFailure =
                new IllegalArgumentException(
                        SENSITIVE_FAILURE_MESSAGE
                );

        when(
                processor.process(
                        eq(FEEDBACK_DATE),
                        eq(FIRST_CULTIVATION_ID),
                        eq(FIRST_OWNER_USER_ID),
                        same(SNAPSHOT),
                        same(REFERENCES_BY_ID),
                        same(
                                NOTIFICATION_METRICS_BY_CULTIVATION_ID
                        ),
                        anyMap()
                )
        ).thenThrow(processorFailure);

        stubOwnerAndProcessor(
                SECOND_CULTIVATION_ID,
                SECOND_OWNER_USER_ID,
                validPersistenceResult(
                        1_031L,
                        SECOND_CULTIVATION_ID,
                        FEEDBACK_DATE,
                        PersistenceStatus.EXISTING
                )
        );

        // 실행
        DailyFeedbackBatchResult result =
                service.execute(FEEDBACK_DATE);

        // 검증
        assertThat(result.targetCount())
                .isEqualTo(2);

        assertThat(result.createdCount())
                .isZero();

        assertThat(result.existingCount())
                .isEqualTo(1);

        assertThat(result.failedCount())
                .isEqualTo(1);

        CultivationResult failedResult =
                result.results().getFirst();

        assertThat(failedResult.cultivationId())
                .isEqualTo(FIRST_CULTIVATION_ID);

        assertThat(failedResult.status())
                .isEqualTo(CultivationStatus.FAILED);

        assertThat(failedResult.failureStage())
                .isEqualTo(
                        FailureStage.CULTIVATION_PROCESSING
                );

        assertThat(failedResult.exceptionType())
                .isEqualTo("IllegalArgumentException")
                .doesNotContain(SENSITIVE_FAILURE_MESSAGE)
                .doesNotContain("https://")
                .doesNotContain("X-Amz-Signature");

        assertThat(result.results().get(1))
                .isEqualTo(
                        CultivationResult.existing(
                                SECOND_CULTIVATION_ID
                        )
                );

        InOrder inOrder = inOrder(
                cultivationOwnerService,
                processor
        );

        inOrder.verify(cultivationOwnerService)
                .findOwnerUserId(
                        FIRST_CULTIVATION_ID
                );

        inOrder.verify(processor)
                .process(
                        eq(FEEDBACK_DATE),
                        eq(FIRST_CULTIVATION_ID),
                        eq(FIRST_OWNER_USER_ID),
                        same(SNAPSHOT),
                        same(REFERENCES_BY_ID),
                        same(
                                NOTIFICATION_METRICS_BY_CULTIVATION_ID
                        ),
                        anyMap()
                );

        inOrder.verify(cultivationOwnerService)
                .findOwnerUserId(
                        SECOND_CULTIVATION_ID
                );

        inOrder.verify(processor)
                .process(
                        eq(FEEDBACK_DATE),
                        eq(SECOND_CULTIVATION_ID),
                        eq(SECOND_OWNER_USER_ID),
                        same(SNAPSHOT),
                        same(REFERENCES_BY_ID),
                        same(
                                NOTIFICATION_METRICS_BY_CULTIVATION_ID
                        ),
                        anyMap()
                );

        inOrder.verifyNoMoreInteractions();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(InvalidProcessingResultCase.class)
    @DisplayName("잘못된 Processor 반환 계약을 대상별 처리 실패로 격리한다")
    void isolatesInvalidProcessorResult(
            InvalidProcessingResultCase invalidCase
    ) {
        // 준비
        stubSuccessfulCommonData(
                TARGET_IDS,
                Map.of()
        );

        stubOwnerAndProcessor(
                FIRST_CULTIVATION_ID,
                FIRST_OWNER_USER_ID,
                invalidPersistenceResult(invalidCase)
        );

        stubOwnerAndProcessor(
                SECOND_CULTIVATION_ID,
                SECOND_OWNER_USER_ID,
                validPersistenceResult(
                        1_041L,
                        SECOND_CULTIVATION_ID,
                        FEEDBACK_DATE,
                        PersistenceStatus.CREATED
                )
        );

        // 실행
        DailyFeedbackBatchResult result =
                service.execute(FEEDBACK_DATE);

        // 검증
        assertThat(result.targetCount())
                .isEqualTo(2);

        assertThat(result.createdCount())
                .isEqualTo(1);

        assertThat(result.existingCount())
                .isZero();

        assertThat(result.failedCount())
                .isEqualTo(1);

        CultivationResult failedResult =
                result.results().getFirst();

        assertThat(failedResult.cultivationId())
                .isEqualTo(FIRST_CULTIVATION_ID);

        assertThat(failedResult.status())
                .isEqualTo(CultivationStatus.FAILED);

        assertThat(failedResult.failureStage())
                .isEqualTo(
                        FailureStage.CULTIVATION_PROCESSING
                );

        assertThat(failedResult.exceptionType())
                .isEqualTo("IllegalStateException");

        assertThat(result.results().get(1))
                .isEqualTo(
                        CultivationResult.created(
                                SECOND_CULTIVATION_ID
                        )
                );

        verify(cultivationOwnerService)
                .findOwnerUserId(
                        SECOND_CULTIVATION_ID
                );

        verify(processor)
                .process(
                        eq(FEEDBACK_DATE),
                        eq(SECOND_CULTIVATION_ID),
                        eq(SECOND_OWNER_USER_ID),
                        same(SNAPSHOT),
                        same(REFERENCES_BY_ID),
                        same(
                                NOTIFICATION_METRICS_BY_CULTIVATION_ID
                        ),
                        anyMap()
                );
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(CommonFailureCase.class)
    @DisplayName("공통 조회 실패는 같은 예외 객체를 상위 계층으로 전파한다")
    void propagatesCommonDataFailure(
            CommonFailureCase failureCase
    ) {
        // 준비
        RuntimeException commonFailure =
                new IllegalStateException(
                        "공통 조회 실패 테스트: "
                                + failureCase.name()
                );

        stubCommonFailure(
                failureCase,
                commonFailure
        );

        // 실행
        RuntimeException thrown =
                catchThrowableOfType(
                        RuntimeException.class,
                        () -> service.execute(FEEDBACK_DATE)
                );

        // 검증
        assertThat(thrown)
                .isSameAs(commonFailure);

        verifyCommonFailureBoundary(failureCase);

        verifyNoInteractions(
                cultivationOwnerService,
                processor
        );
    }

    @Test
    @DisplayName("Error는 경작지 실패로 변환하지 않고 즉시 전파한다")
    void propagatesErrorWithoutConvertingItToFailedResult() {
        // 준비
        stubSuccessfulCommonData(
                TARGET_IDS,
                Map.of()
        );

        when(
                cultivationOwnerService.findOwnerUserId(
                        FIRST_CULTIVATION_ID
                )
        ).thenReturn(FIRST_OWNER_USER_ID);

        AssertionError fatalError =
                new AssertionError(
                        "테스트용 치명적 Error"
                );

        when(
                processor.process(
                        eq(FEEDBACK_DATE),
                        eq(FIRST_CULTIVATION_ID),
                        eq(FIRST_OWNER_USER_ID),
                        same(SNAPSHOT),
                        same(REFERENCES_BY_ID),
                        same(
                                NOTIFICATION_METRICS_BY_CULTIVATION_ID
                        ),
                        anyMap()
                )
        ).thenThrow(fatalError);

        // 실행
        AssertionError thrown =
                catchThrowableOfType(
                        AssertionError.class,
                        () -> service.execute(FEEDBACK_DATE)
                );

        // 검증
        assertThat(thrown)
                .isSameAs(fatalError);

        verify(processor)
                .process(
                        eq(FEEDBACK_DATE),
                        eq(FIRST_CULTIVATION_ID),
                        eq(FIRST_OWNER_USER_ID),
                        same(SNAPSHOT),
                        same(REFERENCES_BY_ID),
                        same(
                                NOTIFICATION_METRICS_BY_CULTIVATION_ID
                        ),
                        anyMap()
                );

        verify(
                cultivationOwnerService,
                never()
        ).findOwnerUserId(
                SECOND_CULTIVATION_ID
        );

        verify(
                processor,
                never()
        ).process(
                eq(FEEDBACK_DATE),
                eq(SECOND_CULTIVATION_ID),
                anyLong(),
                same(SNAPSHOT),
                same(REFERENCES_BY_ID),
                same(
                        NOTIFICATION_METRICS_BY_CULTIVATION_ID
                ),
                anyMap()
        );
    }

    @Test
    @DisplayName("OWNER 조회 단계의 Error는 격리하지 않고 즉시 전파한다")
    void propagatesOwnerResolutionErrorWithoutContinuingBatch() {
        // 준비
        stubSuccessfulCommonData(
                TARGET_IDS,
                Map.of()
        );

        AssertionError fatalError =
                new AssertionError(
                        "OWNER 조회 단계 테스트용 치명적 Error"
                );

        when(
                cultivationOwnerService.findOwnerUserId(
                        FIRST_CULTIVATION_ID
                )
        ).thenThrow(fatalError);

        // 실행
        AssertionError thrown =
                catchThrowableOfType(
                        AssertionError.class,
                        () -> service.execute(FEEDBACK_DATE)
                );

        // 검증
        assertThat(thrown)
                .isSameAs(fatalError);

        verify(cultivationOwnerService)
                .findOwnerUserId(
                        FIRST_CULTIVATION_ID
                );

        verifyNoInteractions(processor);

        verify(
                cultivationOwnerService,
                never()
        ).findOwnerUserId(
                SECOND_CULTIVATION_ID
        );
    }

    @Test
    @DisplayName("execute 메서드는 NOT_SUPPORTED 트랜잭션 전파 정책을 사용한다")
    void usesNotSupportedTransactionPropagation()
            throws NoSuchMethodException {
        // 준비
        Method executeMethod =
                DailyFeedbackBatchService.class
                        .getDeclaredMethod(
                                "execute",
                                LocalDate.class
                        );

        // 실행
        Transactional transactional =
                executeMethod.getAnnotation(
                        Transactional.class
                );

        // 검증
        assertThat(transactional)
                .isNotNull();

        assertThat(transactional.propagation())
                .isEqualTo(
                        Propagation.NOT_SUPPORTED
                );
    }

    private void stubSuccessfulCommonData(
            List<Long> targetIds,
            Map<Long, DailyCultivationPhotoResponse>
                    photosByCultivationId
    ) {
        when(
                cultivationClient.getDataGeneratorSnapshot()
        ).thenReturn(SNAPSHOT);

        when(
                targetResolver.resolveCultivationIds(SNAPSHOT)
        ).thenReturn(targetIds);

        when(
                mushroomReferenceService.fetchAllById()
        ).thenReturn(REFERENCES_BY_ID);

        when(
                notificationMetricsService.fetchDailyMetrics(
                        FEEDBACK_DATE,
                        targetIds
                )
        ).thenReturn(
                NOTIFICATION_METRICS_BY_CULTIVATION_ID
        );

        when(
                dailyVisionAnalysisService
                        .fetchPhotosByCultivationId(
                                FEEDBACK_DATE
                        )
        ).thenReturn(photosByCultivationId);
    }

    private void stubOwnerAndProcessor(
            Long cultivationId,
            Long ownerUserId,
            PersistenceResult persistenceResult
    ) {
        when(
                cultivationOwnerService.findOwnerUserId(
                        cultivationId
                )
        ).thenReturn(ownerUserId);

        when(
                processor.process(
                        eq(FEEDBACK_DATE),
                        eq(cultivationId),
                        eq(ownerUserId),
                        same(SNAPSHOT),
                        same(REFERENCES_BY_ID),
                        same(
                                NOTIFICATION_METRICS_BY_CULTIVATION_ID
                        ),
                        anyMap()
                )
        ).thenReturn(persistenceResult);
    }

    private PersistenceResult validPersistenceResult(
            Long databaseId,
            Long cultivationId,
            LocalDate feedbackDate,
            PersistenceStatus status
    ) {
        DailyFeedback feedback =
                mock(DailyFeedback.class);

        when(feedback.getId())
                .thenReturn(databaseId);

        when(feedback.getCultivationId())
                .thenReturn(cultivationId);

        when(feedback.getFeedbackDate())
                .thenReturn(feedbackDate);

        return new PersistenceResult(
                feedback,
                status
        );
    }

    private PersistenceResult invalidPersistenceResult(
            InvalidProcessingResultCase invalidCase
    ) {
        return switch (invalidCase) {
            case NULL_RESULT -> null;

            case NULL_FEEDBACK ->
                    mock(PersistenceResult.class);

            case NULL_STATUS -> {
                PersistenceResult result =
                        mock(PersistenceResult.class);

                DailyFeedback feedback =
                        mock(DailyFeedback.class);

                when(result.feedback())
                        .thenReturn(feedback);

                yield result;
            }

            case NULL_FEEDBACK_ID -> {
                DailyFeedback feedback =
                        mock(DailyFeedback.class);

                yield new PersistenceResult(
                        feedback,
                        PersistenceStatus.CREATED
                );
            }

            case NON_POSITIVE_FEEDBACK_ID -> {
                DailyFeedback feedback =
                        mock(DailyFeedback.class);

                when(feedback.getId())
                        .thenReturn(0L);

                yield new PersistenceResult(
                        feedback,
                        PersistenceStatus.CREATED
                );
            }

            case MISMATCHED_CULTIVATION_ID -> {
                DailyFeedback feedback =
                        mock(DailyFeedback.class);

                when(feedback.getId())
                        .thenReturn(1_051L);

                when(feedback.getCultivationId())
                        .thenReturn(EXTRA_CULTIVATION_ID);

                yield new PersistenceResult(
                        feedback,
                        PersistenceStatus.CREATED
                );
            }

            case MISMATCHED_FEEDBACK_DATE -> {
                DailyFeedback feedback =
                        mock(DailyFeedback.class);

                when(feedback.getId())
                        .thenReturn(1_052L);

                when(feedback.getCultivationId())
                        .thenReturn(FIRST_CULTIVATION_ID);

                when(feedback.getFeedbackDate())
                        .thenReturn(
                                FEEDBACK_DATE.minusDays(1)
                        );

                yield new PersistenceResult(
                        feedback,
                        PersistenceStatus.CREATED
                );
            }
        };
    }

    private void verifyCommonCallOrder(
            List<Long> targetIds
    ) {
        InOrder inOrder = inOrder(
                cultivationClient,
                targetResolver,
                mushroomReferenceService,
                notificationMetricsService,
                dailyVisionAnalysisService
        );

        inOrder.verify(cultivationClient)
                .getDataGeneratorSnapshot();

        inOrder.verify(targetResolver)
                .resolveCultivationIds(SNAPSHOT);

        inOrder.verify(mushroomReferenceService)
                .fetchAllById();

        inOrder.verify(notificationMetricsService)
                .fetchDailyMetrics(
                        FEEDBACK_DATE,
                        targetIds
                );

        inOrder.verify(dailyVisionAnalysisService)
                .fetchPhotosByCultivationId(
                        FEEDBACK_DATE
                );

        inOrder.verifyNoMoreInteractions();
    }

    private void stubCommonFailure(
            CommonFailureCase failureCase,
            RuntimeException failure
    ) {
        switch (failureCase) {
            case SNAPSHOT_FETCH ->
                    when(
                            cultivationClient
                                    .getDataGeneratorSnapshot()
                    ).thenThrow(failure);

            case TARGET_RESOLUTION -> {
                when(
                        cultivationClient
                                .getDataGeneratorSnapshot()
                ).thenReturn(SNAPSHOT);

                when(
                        targetResolver
                                .resolveCultivationIds(
                                        SNAPSHOT
                                )
                ).thenThrow(failure);
            }

            case MUSHROOM_REFERENCE -> {
                when(
                        cultivationClient
                                .getDataGeneratorSnapshot()
                ).thenReturn(SNAPSHOT);

                when(
                        targetResolver
                                .resolveCultivationIds(
                                        SNAPSHOT
                                )
                ).thenReturn(TARGET_IDS);

                when(
                        mushroomReferenceService
                                .fetchAllById()
                ).thenThrow(failure);
            }

            case NOTIFICATION_METRICS -> {
                when(
                        cultivationClient
                                .getDataGeneratorSnapshot()
                ).thenReturn(SNAPSHOT);

                when(
                        targetResolver
                                .resolveCultivationIds(
                                        SNAPSHOT
                                )
                ).thenReturn(TARGET_IDS);

                when(
                        mushroomReferenceService
                                .fetchAllById()
                ).thenReturn(REFERENCES_BY_ID);

                when(
                        notificationMetricsService
                                .fetchDailyMetrics(
                                        FEEDBACK_DATE,
                                        TARGET_IDS
                                )
                ).thenThrow(failure);
            }

            case DAILY_PHOTOS -> {
                when(
                        cultivationClient
                                .getDataGeneratorSnapshot()
                ).thenReturn(SNAPSHOT);

                when(
                        targetResolver
                                .resolveCultivationIds(
                                        SNAPSHOT
                                )
                ).thenReturn(TARGET_IDS);

                when(
                        mushroomReferenceService
                                .fetchAllById()
                ).thenReturn(REFERENCES_BY_ID);

                when(
                        notificationMetricsService
                                .fetchDailyMetrics(
                                        FEEDBACK_DATE,
                                        TARGET_IDS
                                )
                ).thenReturn(
                        NOTIFICATION_METRICS_BY_CULTIVATION_ID
                );

                when(
                        dailyVisionAnalysisService
                                .fetchPhotosByCultivationId(
                                        FEEDBACK_DATE
                                )
                ).thenThrow(failure);
            }
        }
    }

    private void verifyCommonFailureBoundary(
            CommonFailureCase failureCase
    ) {
        InOrder inOrder = inOrder(
                cultivationClient,
                targetResolver,
                mushroomReferenceService,
                notificationMetricsService,
                dailyVisionAnalysisService
        );

        inOrder.verify(cultivationClient)
                .getDataGeneratorSnapshot();

        switch (failureCase) {
            case SNAPSHOT_FETCH -> {
                // Snapshot 호출 이후 즉시 실패합니다.
            }

            case TARGET_RESOLUTION ->
                    inOrder.verify(targetResolver)
                            .resolveCultivationIds(
                                    SNAPSHOT
                            );

            case MUSHROOM_REFERENCE -> {
                inOrder.verify(targetResolver)
                        .resolveCultivationIds(
                                SNAPSHOT
                        );

                inOrder.verify(mushroomReferenceService)
                        .fetchAllById();
            }

            case NOTIFICATION_METRICS -> {
                inOrder.verify(targetResolver)
                        .resolveCultivationIds(
                                SNAPSHOT
                        );

                inOrder.verify(mushroomReferenceService)
                        .fetchAllById();

                inOrder.verify(notificationMetricsService)
                        .fetchDailyMetrics(
                                FEEDBACK_DATE,
                                TARGET_IDS
                        );
            }

            case DAILY_PHOTOS -> {
                inOrder.verify(targetResolver)
                        .resolveCultivationIds(
                                SNAPSHOT
                        );

                inOrder.verify(mushroomReferenceService)
                        .fetchAllById();

                inOrder.verify(notificationMetricsService)
                        .fetchDailyMetrics(
                                FEEDBACK_DATE,
                                TARGET_IDS
                        );

                inOrder.verify(dailyVisionAnalysisService)
                        .fetchPhotosByCultivationId(
                                FEEDBACK_DATE
                        );
            }
        }

        inOrder.verifyNoMoreInteractions();
    }

    private enum InvalidProcessingResultCase {
        NULL_RESULT,
        NULL_FEEDBACK,
        NULL_STATUS,
        NULL_FEEDBACK_ID,
        NON_POSITIVE_FEEDBACK_ID,
        MISMATCHED_CULTIVATION_ID,
        MISMATCHED_FEEDBACK_DATE
    }

    private enum CommonFailureCase {
        SNAPSHOT_FETCH,
        TARGET_RESOLUTION,
        MUSHROOM_REFERENCE,
        NOTIFICATION_METRICS,
        DAILY_PHOTOS
    }
}
