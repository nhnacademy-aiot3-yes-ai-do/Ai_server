package site.yesaido.ai_server.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.yesaido.ai_server.dto.daily_feedback.DailyFeedbackBatchResult;
import site.yesaido.ai_server.dto.daily_feedback.DailyFeedbackBatchResult.CultivationResult;
import site.yesaido.ai_server.dto.daily_feedback.DailyFeedbackBatchResult.FailureStage;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyFeedbackScheduledBatchServiceTest {

    private static final ZoneId SEOUL_ZONE =
            ZoneId.of("Asia/Seoul");

    private static final Clock FIXED_CLOCK =
            Clock.fixed(
                    Instant.parse("2026-09-03T00:00:00Z"),
                    SEOUL_ZONE
            );

    private static final LocalDate FEEDBACK_DATE =
            LocalDate.of(2026, 9, 2);

    private static final OffsetDateTime SNAPSHOT_AT =
            OffsetDateTime.of(
                    2026,
                    9,
                    3,
                    9,
                    0,
                    0,
                    0,
                    ZoneOffset.ofHours(9)
            );

    private static final Long FIRST_CULTIVATION_ID = 10L;

    private static final Long SECOND_CULTIVATION_ID = 20L;

    private static final String INVALID_BATCH_RESULT_MESSAGE =
            "일일 피드백 배치 결과 계약이 올바르지 않습니다.";

    private static final String PARTIAL_FAILURE_MESSAGE =
            "일일 피드백 배치에 실패한 대상이 포함되어 있습니다.";

    private static final String SENSITIVE_FAILURE_MESSAGE =
            "https://batch-test.invalid/failure"
                    + "?X-Amz-Signature=sensitive-token";

    @Mock
    private DailyFeedbackBatchService dailyFeedbackBatchService;

    private DailyFeedbackScheduledBatchService service;

    @BeforeEach
    void setUp() {
        service = new DailyFeedbackScheduledBatchService(
                dailyFeedbackBatchService,
                FIXED_CLOCK
        );
    }

    @Test
    @DisplayName("서울 기준 전날의 신규 및 기존 피드백 배치를 정상 반환한다")
    void executesPreviousDayBatchSuccessfully() {
        // 준비
        DailyFeedbackBatchResult expectedResult =
                DailyFeedbackBatchResult.from(
                        FEEDBACK_DATE,
                        SNAPSHOT_AT,
                        List.of(
                                CultivationResult.created(
                                        FIRST_CULTIVATION_ID
                                ),
                                CultivationResult.existing(
                                        SECOND_CULTIVATION_ID
                                )
                        )
                );

        when(dailyFeedbackBatchService.execute(FEEDBACK_DATE))
                .thenReturn(expectedResult);

        // 실행
        DailyFeedbackBatchResult actualResult =
                service.executePreviousDay();

        // 검증
        assertThat(actualResult).isSameAs(expectedResult);
        assertThat(actualResult.targetCount()).isEqualTo(2);
        assertThat(actualResult.createdCount()).isEqualTo(1);
        assertThat(actualResult.existingCount()).isEqualTo(1);
        assertThat(actualResult.failedCount()).isZero();

        verify(dailyFeedbackBatchService).execute(FEEDBACK_DATE);
    }

    @Test
    @DisplayName("대상이 없는 전날 배치도 정상 결과로 반환한다")
    void returnsEmptyPreviousDayBatch() {
        // 준비
        DailyFeedbackBatchResult expectedResult =
                DailyFeedbackBatchResult.from(
                        FEEDBACK_DATE,
                        SNAPSHOT_AT,
                        List.of()
                );

        when(dailyFeedbackBatchService.execute(FEEDBACK_DATE))
                .thenReturn(expectedResult);

        // 실행
        DailyFeedbackBatchResult actualResult =
                service.executePreviousDay();

        // 검증
        assertThat(actualResult).isSameAs(expectedResult);
        assertThat(actualResult.targetCount()).isZero();
        assertThat(actualResult.createdCount()).isZero();
        assertThat(actualResult.existingCount()).isZero();
        assertThat(actualResult.failedCount()).isZero();
        assertThat(actualResult.results()).isEmpty();

        verify(dailyFeedbackBatchService).execute(FEEDBACK_DATE);
    }

    @Test
    @DisplayName("대상별 실패가 포함되면 안전한 예외로 CronJob 재시도를 유도한다")
    void rejectsPartiallyFailedBatch() {
        // 준비
        RuntimeException processingFailure =
                new RuntimeException(SENSITIVE_FAILURE_MESSAGE);

        DailyFeedbackBatchResult failedResult =
                DailyFeedbackBatchResult.from(
                        FEEDBACK_DATE,
                        SNAPSHOT_AT,
                        List.of(
                                CultivationResult.created(
                                        FIRST_CULTIVATION_ID
                                ),
                                CultivationResult.failed(
                                        SECOND_CULTIVATION_ID,
                                        FailureStage.CULTIVATION_PROCESSING,
                                        processingFailure
                                )
                        )
                );

        when(dailyFeedbackBatchService.execute(FEEDBACK_DATE))
                .thenReturn(failedResult);

        // 실행
        IllegalStateException exception =
                catchThrowableOfType(
                        IllegalStateException.class,
                        service::executePreviousDay
                );

        // 검증
        assertThat(exception)
                .hasMessage(PARTIAL_FAILURE_MESSAGE);

        assertThat(exception.getMessage())
                .doesNotContain(SENSITIVE_FAILURE_MESSAGE)
                .doesNotContain("sensitive-token")
                .doesNotContain("X-Amz-Signature")
                .doesNotContain("https://");

        verify(dailyFeedbackBatchService).execute(FEEDBACK_DATE);
    }

    @Test
    @DisplayName("BatchService RuntimeException은 동일한 인스턴스로 전파한다")
    void propagatesBatchServiceRuntimeException() {
        // 준비
        RuntimeException expectedException =
                new RuntimeException("batch service failure");

        when(dailyFeedbackBatchService.execute(FEEDBACK_DATE))
                .thenThrow(expectedException);

        // 실행
        RuntimeException propagatedException =
                catchThrowableOfType(
                        RuntimeException.class,
                        service::executePreviousDay
                );

        // 검증
        assertThat(propagatedException)
                .isSameAs(expectedException);

        verify(dailyFeedbackBatchService).execute(FEEDBACK_DATE);
    }

    @Test
    @DisplayName("BatchService Error는 숨기지 않고 동일한 인스턴스로 전파한다")
    void propagatesBatchServiceError() {
        // 준비
        AssertionError expectedError =
                new AssertionError("fatal batch failure");

        when(dailyFeedbackBatchService.execute(FEEDBACK_DATE))
                .thenThrow(expectedError);

        // 실행
        AssertionError propagatedError =
                catchThrowableOfType(
                        AssertionError.class,
                        service::executePreviousDay
                );

        // 검증
        assertThat(propagatedError).isSameAs(expectedError);

        verify(dailyFeedbackBatchService).execute(FEEDBACK_DATE);
    }

    @Test
    @DisplayName("BatchService가 null 결과를 반환하면 계약 오류로 거부한다")
    void rejectsNullBatchResult() {
        // 준비
        when(dailyFeedbackBatchService.execute(FEEDBACK_DATE))
                .thenReturn(null);

        // 실행
        IllegalStateException exception =
                catchThrowableOfType(
                        IllegalStateException.class,
                        service::executePreviousDay
                );

        // 검증
        assertThat(exception)
                .hasMessage(INVALID_BATCH_RESULT_MESSAGE);

        verify(dailyFeedbackBatchService).execute(FEEDBACK_DATE);
    }

    @Test
    @DisplayName("결과의 feedbackDate가 실행 대상 날짜와 다르면 거부한다")
    void rejectsMismatchedFeedbackDate() {
        // 준비
        LocalDate mismatchedDate = FEEDBACK_DATE.minusDays(1);

        DailyFeedbackBatchResult mismatchedResult =
                DailyFeedbackBatchResult.from(
                        mismatchedDate,
                        SNAPSHOT_AT,
                        List.of()
                );

        when(dailyFeedbackBatchService.execute(FEEDBACK_DATE))
                .thenReturn(mismatchedResult);

        // 실행
        IllegalStateException exception =
                catchThrowableOfType(
                        IllegalStateException.class,
                        service::executePreviousDay
                );

        // 검증
        assertThat(exception)
                .hasMessage(INVALID_BATCH_RESULT_MESSAGE);

        verify(dailyFeedbackBatchService).execute(FEEDBACK_DATE);
    }

    @ParameterizedTest(name = "[{index}] {0}가 음수이면 거부한다")
    @EnumSource(NegativeCountField.class)
    @DisplayName("음수인 배치 count는 결과 계약 오류로 거부한다")
    void rejectsNegativeCount(
            NegativeCountField negativeCountField
    ) {
        // 준비
        DailyFeedbackBatchResult invalidResult =
                createNegativeCountResult(negativeCountField);

        when(dailyFeedbackBatchService.execute(FEEDBACK_DATE))
                .thenReturn(invalidResult);

        // 실행
        IllegalStateException exception =
                catchThrowableOfType(
                        IllegalStateException.class,
                        service::executePreviousDay
                );

        // 검증
        assertThat(exception)
                .hasMessage(INVALID_BATCH_RESULT_MESSAGE);

        verify(dailyFeedbackBatchService).execute(FEEDBACK_DATE);
    }

    @Test
    @DisplayName("상태별 count 합계가 targetCount와 다르면 거부한다")
    void rejectsMismatchedCountSum() {
        // 준비
        DailyFeedbackBatchResult invalidResult =
                mock(DailyFeedbackBatchResult.class);

        when(invalidResult.feedbackDate())
                .thenReturn(FEEDBACK_DATE);
        when(invalidResult.targetCount())
                .thenReturn(2);
        when(invalidResult.createdCount())
                .thenReturn(1);
        when(invalidResult.existingCount())
                .thenReturn(0);
        when(invalidResult.failedCount())
                .thenReturn(0);

        when(dailyFeedbackBatchService.execute(FEEDBACK_DATE))
                .thenReturn(invalidResult);

        // 실행
        IllegalStateException exception =
                catchThrowableOfType(
                        IllegalStateException.class,
                        service::executePreviousDay
                );

        // 검증
        assertThat(exception)
                .hasMessage(INVALID_BATCH_RESULT_MESSAGE);

        verify(dailyFeedbackBatchService).execute(FEEDBACK_DATE);
    }

    @Test
    @DisplayName("연도 경계에서도 서울 기준 전날을 정확히 계산한다")
    void calculatesPreviousDayAcrossYearBoundary() {
        // 준비
        Clock yearBoundaryClock =
                Clock.fixed(
                        Instant.parse("2026-12-31T15:30:00Z"),
                        SEOUL_ZONE
                );

        DailyFeedbackScheduledBatchService yearBoundaryService =
                new DailyFeedbackScheduledBatchService(
                        dailyFeedbackBatchService,
                        yearBoundaryClock
                );

        LocalDate expectedFeedbackDate =
                LocalDate.of(2026, 12, 31);

        OffsetDateTime yearBoundarySnapshotAt =
                OffsetDateTime.of(
                        2027,
                        1,
                        1,
                        0,
                        30,
                        0,
                        0,
                        ZoneOffset.ofHours(9)
                );

        DailyFeedbackBatchResult expectedResult =
                DailyFeedbackBatchResult.from(
                        expectedFeedbackDate,
                        yearBoundarySnapshotAt,
                        List.of()
                );

        when(
                dailyFeedbackBatchService.execute(
                        expectedFeedbackDate
                )
        ).thenReturn(expectedResult);

        // 실행
        DailyFeedbackBatchResult actualResult =
                yearBoundaryService.executePreviousDay();

        // 검증
        assertThat(actualResult).isSameAs(expectedResult);
        assertThat(actualResult.feedbackDate())
                .isEqualTo(expectedFeedbackDate);

        verify(dailyFeedbackBatchService)
                .execute(expectedFeedbackDate);
    }

    @Test
    @DisplayName("전날 배치 실행은 기존 트랜잭션을 중단한다")
    void usesNotSupportedTransactionPropagation()
            throws NoSuchMethodException {
        // 준비
        Method method = DailyFeedbackScheduledBatchService.class
                .getDeclaredMethod("executePreviousDay");

        // 실행
        Transactional transactional =
                method.getAnnotation(Transactional.class);

        // 검증
        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation())
                .isEqualTo(Propagation.NOT_SUPPORTED);

        verifyNoInteractions(dailyFeedbackBatchService);
    }

    private DailyFeedbackBatchResult createNegativeCountResult(
            NegativeCountField negativeCountField
    ) {
        DailyFeedbackBatchResult result =
                mock(DailyFeedbackBatchResult.class);

        when(result.feedbackDate()).thenReturn(FEEDBACK_DATE);

        switch (negativeCountField) {
            case TARGET_COUNT ->
                    when(result.targetCount()).thenReturn(-1);

            case CREATED_COUNT -> {
                when(result.targetCount()).thenReturn(0);
                when(result.createdCount()).thenReturn(-1);
            }

            case EXISTING_COUNT -> {
                when(result.targetCount()).thenReturn(0);
                when(result.createdCount()).thenReturn(0);
                when(result.existingCount()).thenReturn(-1);
            }

            case FAILED_COUNT -> {
                when(result.targetCount()).thenReturn(0);
                when(result.createdCount()).thenReturn(0);
                when(result.existingCount()).thenReturn(0);
                when(result.failedCount()).thenReturn(-1);
            }
        }

        return result;
    }

    private enum NegativeCountField {
        TARGET_COUNT,
        CREATED_COUNT,
        EXISTING_COUNT,
        FAILED_COUNT
    }
}
