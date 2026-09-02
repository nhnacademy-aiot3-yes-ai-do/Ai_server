package site.yesaido.ai_server.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.yesaido.ai_server.config.DailyFeedbackOutboxProperties;
import site.yesaido.ai_server.entity.DailyFeedbackOutbox;
import site.yesaido.ai_server.repository.DailyFeedbackOutboxRepository;
import site.yesaido.ai_server.service.DailyFeedbackOutboxRecoveryService.RecoveryBatchResult;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyFeedbackOutboxRecoveryServiceTest {

    private static final int BATCH_SIZE = 20;

    private static final int MAX_ATTEMPTS = 3;

    private static final long FIRST_OUTBOX_ID = 100L;

    private static final long SECOND_OUTBOX_ID = 101L;

    private static final long FIRST_DAILY_FEEDBACK_ID = 1001L;

    private static final long SECOND_DAILY_FEEDBACK_ID = 1002L;

    private static final UUID FIRST_EVENT_ID = UUID.fromString(
            "123e4567-e89b-12d3-a456-426614174000"
    );

    private static final UUID SECOND_EVENT_ID = UUID.fromString(
            "223e4567-e89b-12d3-a456-426614174000"
    );

    private static final Instant FIXED_INSTANT =
            Instant.parse("2026-09-03T00:00:00Z");

    private static final ZoneId SEOUL_ZONE =
            ZoneId.of("Asia/Seoul");

    private static final Clock FIXED_CLOCK =
            Clock.fixed(FIXED_INSTANT, SEOUL_ZONE);

    private static final LocalDateTime FIXED_NOW =
            LocalDateTime.of(2026, 9, 3, 9, 0);

    private static final Duration STALE_TIMEOUT =
            Duration.ofMinutes(2);

    private static final LocalDateTime STALE_BEFORE =
            LocalDateTime.of(2026, 9, 3, 8, 58);

    private static final String STALE_SENDING_TIMEOUT =
            "StaleSendingTimeout";

    @Mock
    private DailyFeedbackOutboxRepository
            dailyFeedbackOutboxRepository;

    @Mock
    private DailyFeedbackOutboxProperties
            dailyFeedbackOutboxProperties;

    private DailyFeedbackOutboxRecoveryService service;

    @BeforeEach
    void setUp() {
        service = new DailyFeedbackOutboxRecoveryService(
                dailyFeedbackOutboxRepository,
                dailyFeedbackOutboxProperties,
                FIXED_CLOCK
        );
    }

    @Test
    @DisplayName("복구 대상이 없으면 모든 결과가 0이고 Repository를 flush한다")
    void returnEmptyResultWhenThereIsNoStaleOutbox() {
        // 준비
        stubRecoveryBatch(
                STALE_TIMEOUT,
                STALE_BEFORE,
                List.of()
        );

        // 실행
        RecoveryBatchResult result =
                service.recoverStaleSending();

        // 검증
        verify(dailyFeedbackOutboxRepository)
                .findStaleSendingForRecovery(
                        STALE_BEFORE,
                        BATCH_SIZE
                );

        verify(dailyFeedbackOutboxRepository)
                .flush();

        assertRecoveryCounts(
                result,
                0,
                0,
                0
        );
    }

    @Test
    @DisplayName("최대 시도 횟수 미만인 Outbox는 즉시 재시도를 예약한다")
    void scheduleImmediateRetryBelowMaximumAttempts() {
        // 준비
        DailyFeedbackOutbox outbox =
                successfulOutbox(
                        FIRST_OUTBOX_ID,
                        FIRST_EVENT_ID,
                        FIRST_DAILY_FEEDBACK_ID,
                        1
                );

        stubRecoveryBatch(
                STALE_TIMEOUT,
                STALE_BEFORE,
                List.of(outbox)
        );

        // 실행
        RecoveryBatchResult result =
                service.recoverStaleSending();

        // 검증
        verify(outbox)
                .scheduleRetry(
                        FIXED_NOW,
                        FIXED_NOW,
                        STALE_SENDING_TIMEOUT
                );

        verify(outbox, never())
                .markFailed(
                        any(LocalDateTime.class),
                        anyString()
                );

        verify(outbox, never())
                .claim(any(LocalDateTime.class));

        verify(dailyFeedbackOutboxRepository)
                .flush();

        assertRecoveryCounts(
                result,
                1,
                1,
                0
        );
    }

    @Test
    @DisplayName("최대 시도 횟수에 도달한 Outbox는 최종 실패 처리한다")
    void markOutboxFailedAtMaximumAttempts() {
        // 준비
        DailyFeedbackOutbox outbox =
                successfulOutbox(
                        FIRST_OUTBOX_ID,
                        FIRST_EVENT_ID,
                        FIRST_DAILY_FEEDBACK_ID,
                        MAX_ATTEMPTS
                );

        stubRecoveryBatch(
                STALE_TIMEOUT,
                STALE_BEFORE,
                List.of(outbox)
        );

        // 실행
        RecoveryBatchResult result =
                service.recoverStaleSending();

        // 검증
        verify(outbox)
                .markFailed(
                        FIXED_NOW,
                        STALE_SENDING_TIMEOUT
                );

        verify(outbox, never())
                .scheduleRetry(
                        any(LocalDateTime.class),
                        any(LocalDateTime.class),
                        anyString()
                );

        verify(outbox, never())
                .claim(any(LocalDateTime.class));

        verify(dailyFeedbackOutboxRepository)
                .flush();

        assertRecoveryCounts(
                result,
                1,
                0,
                1
        );
    }

    @Test
    @DisplayName("최대 시도 횟수를 초과한 Outbox도 최종 실패 처리한다")
    void markOutboxFailedAboveMaximumAttempts() {
        // 준비
        DailyFeedbackOutbox outbox =
                successfulOutbox(
                        FIRST_OUTBOX_ID,
                        FIRST_EVENT_ID,
                        FIRST_DAILY_FEEDBACK_ID,
                        MAX_ATTEMPTS + 1
                );

        stubRecoveryBatch(
                STALE_TIMEOUT,
                STALE_BEFORE,
                List.of(outbox)
        );

        // 실행
        RecoveryBatchResult result =
                service.recoverStaleSending();

        // 검증
        verify(outbox)
                .markFailed(
                        FIXED_NOW,
                        STALE_SENDING_TIMEOUT
                );

        verify(outbox, never())
                .scheduleRetry(
                        any(LocalDateTime.class),
                        any(LocalDateTime.class),
                        anyString()
                );

        verify(outbox, never())
                .claim(any(LocalDateTime.class));

        verify(dailyFeedbackOutboxRepository)
                .flush();

        assertRecoveryCounts(
                result,
                1,
                0,
                1
        );
    }

    @Test
    @DisplayName("혼합 배치는 재시도와 최종 실패를 집계하고 한 번만 flush한다")
    void recoverMixedBatchAndFlushOnce() {
        // 준비
        DailyFeedbackOutbox retryOutbox =
                successfulOutbox(
                        FIRST_OUTBOX_ID,
                        FIRST_EVENT_ID,
                        FIRST_DAILY_FEEDBACK_ID,
                        1
                );

        DailyFeedbackOutbox failedOutbox =
                successfulOutbox(
                        SECOND_OUTBOX_ID,
                        SECOND_EVENT_ID,
                        SECOND_DAILY_FEEDBACK_ID,
                        MAX_ATTEMPTS
                );

        stubRecoveryBatch(
                STALE_TIMEOUT,
                STALE_BEFORE,
                List.of(
                        retryOutbox,
                        failedOutbox
                )
        );

        // 실행
        RecoveryBatchResult result =
                service.recoverStaleSending();

        // 검증
        verify(retryOutbox)
                .scheduleRetry(
                        FIXED_NOW,
                        FIXED_NOW,
                        STALE_SENDING_TIMEOUT
                );

        verify(retryOutbox, never())
                .markFailed(
                        any(LocalDateTime.class),
                        anyString()
                );

        verify(failedOutbox)
                .markFailed(
                        FIXED_NOW,
                        STALE_SENDING_TIMEOUT
                );

        verify(failedOutbox, never())
                .scheduleRetry(
                        any(LocalDateTime.class),
                        any(LocalDateTime.class),
                        anyString()
                );

        verify(retryOutbox, never())
                .claim(any(LocalDateTime.class));

        verify(failedOutbox, never())
                .claim(any(LocalDateTime.class));

        verify(dailyFeedbackOutboxRepository, times(1))
                .flush();

        assertRecoveryCounts(
                result,
                2,
                1,
                1
        );
    }

    @Test
    @DisplayName("Repository 조회 실패는 같은 예외를 전파하고 flush하지 않는다")
    void propagateRepositorySelectionFailure() {
        // 준비
        RuntimeException repositoryFailure =
                new RuntimeException("repository failure");

        when(dailyFeedbackOutboxProperties.getStaleTimeout())
                .thenReturn(STALE_TIMEOUT);

        when(dailyFeedbackOutboxProperties.getBatchSize())
                .thenReturn(BATCH_SIZE);

        when(
                dailyFeedbackOutboxRepository
                        .findStaleSendingForRecovery(
                                STALE_BEFORE,
                                BATCH_SIZE
                        )
        ).thenThrow(repositoryFailure);

        // 실행
        RuntimeException propagatedException =
                catchThrowableOfType(
                        RuntimeException.class,
                        service::recoverStaleSending
                );

        // 검증
        assertThat(propagatedException)
                .isSameAs(repositoryFailure);

        verify(dailyFeedbackOutboxRepository, never())
                .flush();

        verify(
                dailyFeedbackOutboxProperties,
                never()
        ).getMaxAttempts();
    }

    @Test
    @DisplayName("재시도 상태 전이 실패는 이후 Outbox와 flush를 처리하지 않는다")
    void stopBatchWhenRetryTransitionFails() {
        // 준비
        DailyFeedbackOutbox firstOutbox =
                outboxWithAttemptCount(1);

        DailyFeedbackOutbox secondOutbox =
                mock(DailyFeedbackOutbox.class);

        RuntimeException transitionFailure =
                new RuntimeException("schedule retry failure");

        stubRecoveryBatch(
                STALE_TIMEOUT,
                STALE_BEFORE,
                List.of(
                        firstOutbox,
                        secondOutbox
                )
        );

        doThrow(transitionFailure)
                .when(firstOutbox)
                .scheduleRetry(
                        FIXED_NOW,
                        FIXED_NOW,
                        STALE_SENDING_TIMEOUT
                );

        // 실행
        RuntimeException propagatedException =
                catchThrowableOfType(
                        RuntimeException.class,
                        service::recoverStaleSending
                );

        // 검증
        assertThat(propagatedException)
                .isSameAs(transitionFailure);

        verify(firstOutbox)
                .scheduleRetry(
                        FIXED_NOW,
                        FIXED_NOW,
                        STALE_SENDING_TIMEOUT
                );

        verify(firstOutbox, never())
                .markFailed(
                        any(LocalDateTime.class),
                        anyString()
                );

        verify(firstOutbox, never())
                .claim(any(LocalDateTime.class));

        verifyNoInteractions(secondOutbox);

        verify(dailyFeedbackOutboxRepository, never())
                .flush();
    }

    @Test
    @DisplayName("최종 실패 상태 전이 실패는 같은 예외를 전파하고 flush하지 않는다")
    void propagateFailedTransitionFailure() {
        // 준비
        DailyFeedbackOutbox outbox =
                outboxWithAttemptCount(MAX_ATTEMPTS);

        RuntimeException transitionFailure =
                new RuntimeException("mark failed failure");

        stubRecoveryBatch(
                STALE_TIMEOUT,
                STALE_BEFORE,
                List.of(outbox)
        );

        doThrow(transitionFailure)
                .when(outbox)
                .markFailed(
                        FIXED_NOW,
                        STALE_SENDING_TIMEOUT
                );

        // 실행
        RuntimeException propagatedException =
                catchThrowableOfType(
                        RuntimeException.class,
                        service::recoverStaleSending
                );

        // 검증
        assertThat(propagatedException)
                .isSameAs(transitionFailure);

        verify(outbox)
                .markFailed(
                        FIXED_NOW,
                        STALE_SENDING_TIMEOUT
                );

        verify(outbox, never())
                .scheduleRetry(
                        any(LocalDateTime.class),
                        any(LocalDateTime.class),
                        anyString()
                );

        verify(outbox, never())
                .claim(any(LocalDateTime.class));

        verify(dailyFeedbackOutboxRepository, never())
                .flush();
    }

    @Test
    @DisplayName("flush 실패는 상태 전이 후 같은 예외를 상위로 전파한다")
    void propagateFlushFailureAfterStateTransition() {
        // 준비
        DailyFeedbackOutbox outbox =
                successfulOutbox(
                        FIRST_OUTBOX_ID,
                        FIRST_EVENT_ID,
                        FIRST_DAILY_FEEDBACK_ID,
                        1
                );

        RuntimeException flushFailure =
                new RuntimeException("flush failure");

        stubRecoveryBatch(
                STALE_TIMEOUT,
                STALE_BEFORE,
                List.of(outbox)
        );

        doThrow(flushFailure)
                .when(dailyFeedbackOutboxRepository)
                .flush();

        // 실행
        RuntimeException propagatedException =
                catchThrowableOfType(
                        RuntimeException.class,
                        service::recoverStaleSending
                );

        // 검증
        assertThat(propagatedException)
                .isSameAs(flushFailure);

        verify(outbox)
                .scheduleRetry(
                        FIXED_NOW,
                        FIXED_NOW,
                        STALE_SENDING_TIMEOUT
                );

        verify(outbox, never())
                .markFailed(
                        any(LocalDateTime.class),
                        anyString()
                );

        verify(outbox, never())
                .claim(any(LocalDateTime.class));

        verify(dailyFeedbackOutboxRepository)
                .flush();
    }

    @Test
    @DisplayName("staleBefore는 고정 현재 시각에서 staleTimeout을 정확히 차감한다")
    void calculateStaleBeforeFromFixedClock() {
        // 준비
        stubRecoveryBatch(
                STALE_TIMEOUT,
                STALE_BEFORE,
                List.of()
        );

        // 실행
        RecoveryBatchResult result =
                service.recoverStaleSending();

        // 검증
        verify(dailyFeedbackOutboxRepository)
                .findStaleSendingForRecovery(
                        LocalDateTime.of(
                                2026,
                                9,
                                3,
                                8,
                                58
                        ),
                        BATCH_SIZE
                );

        verify(dailyFeedbackOutboxRepository)
                .flush();

        assertRecoveryCounts(
                result,
                0,
                0,
                0
        );
    }

    @Test
    @DisplayName("staleBefore가 날짜 범위를 벗어나면 LocalDateTime.MIN으로 포화한다")
    void saturateStaleBeforeAtLocalDateTimeMinimum() {
        // 준비
        Duration excessiveStaleTimeout =
                Duration.ofSeconds(Long.MAX_VALUE);

        stubRecoveryBatch(
                excessiveStaleTimeout,
                LocalDateTime.MIN,
                List.of()
        );

        // 실행
        RecoveryBatchResult result =
                service.recoverStaleSending();

        // 검증
        verify(dailyFeedbackOutboxRepository)
                .findStaleSendingForRecovery(
                        LocalDateTime.MIN,
                        BATCH_SIZE
                );

        verify(dailyFeedbackOutboxRepository)
                .flush();

        assertRecoveryCounts(
                result,
                0,
                0,
                0
        );
    }

    @ParameterizedTest(name = "음수 필드: {3}")
    @MethodSource("negativeRecoveryBatchResultCases")
    @DisplayName("RecoveryBatchResult는 각 필드의 음수 값을 거부한다")
    void rejectNegativeRecoveryBatchResultCount(
            int selectedCount,
            int retryScheduledCount,
            int failedCount,
            String fieldName
    ) {
        // 준비

        // 실행
        IllegalArgumentException exception =
                catchThrowableOfType(
                        IllegalArgumentException.class,
                        () -> new RecoveryBatchResult(
                                selectedCount,
                                retryScheduledCount,
                                failedCount
                        )
                );

        // 검증
        assertThat(exception)
                .as(fieldName + " 음수 검증")
                .hasMessage(
                        "복구 배치 결과 개수는 음수일 수 없습니다."
                );
    }

    @Test
    @DisplayName("처리 결과 합계가 selectedCount와 다르면 예외가 발생한다")
    void rejectMismatchedRecoveryBatchResultTotal() {
        // 준비

        // 실행
        IllegalArgumentException exception =
                catchThrowableOfType(
                        IllegalArgumentException.class,
                        () -> new RecoveryBatchResult(
                                2,
                                1,
                                0
                        )
                );

        // 검증
        assertThat(exception)
                .hasMessage(
                        "복구 처리 결과의 합은 조회 개수와 같아야 합니다."
                );
    }

    @Test
    @DisplayName("유효한 RecoveryBatchResult는 모든 값을 정확히 반환한다")
    void preserveValidRecoveryBatchResultValues() {
        // 준비
        int selectedCount = 3;
        int retryScheduledCount = 2;
        int failedCount = 1;

        // 실행
        RecoveryBatchResult result =
                new RecoveryBatchResult(
                        selectedCount,
                        retryScheduledCount,
                        failedCount
                );

        // 검증
        assertThat(result.selectedCount())
                .isEqualTo(selectedCount);

        assertThat(result.retryScheduledCount())
                .isEqualTo(retryScheduledCount);

        assertThat(result.failedCount())
                .isEqualTo(failedCount);
    }

    private void stubRecoveryBatch(
            Duration staleTimeout,
            LocalDateTime expectedStaleBefore,
            List<DailyFeedbackOutbox> outboxes
    ) {
        when(dailyFeedbackOutboxProperties.getStaleTimeout())
                .thenReturn(staleTimeout);

        when(dailyFeedbackOutboxProperties.getBatchSize())
                .thenReturn(BATCH_SIZE);

        when(
                dailyFeedbackOutboxRepository
                        .findStaleSendingForRecovery(
                                expectedStaleBefore,
                                BATCH_SIZE
                        )
        ).thenReturn(outboxes);

        when(dailyFeedbackOutboxProperties.getMaxAttempts())
                .thenReturn(MAX_ATTEMPTS);
    }

    private DailyFeedbackOutbox successfulOutbox(
            long outboxId,
            UUID eventId,
            long dailyFeedbackId,
            int attemptCount
    ) {
        DailyFeedbackOutbox outbox =
                mock(DailyFeedbackOutbox.class);

        when(outbox.getId())
                .thenReturn(outboxId);

        when(outbox.getEventId())
                .thenReturn(eventId);

        when(outbox.getDailyFeedbackId())
                .thenReturn(dailyFeedbackId);

        when(outbox.getAttemptCount())
                .thenReturn(attemptCount);

        return outbox;
    }

    private DailyFeedbackOutbox outboxWithAttemptCount(
            int attemptCount
    ) {
        DailyFeedbackOutbox outbox =
                mock(DailyFeedbackOutbox.class);

        when(outbox.getAttemptCount())
                .thenReturn(attemptCount);

        return outbox;
    }

    private void assertRecoveryCounts(
            RecoveryBatchResult result,
            int selectedCount,
            int retryScheduledCount,
            int failedCount
    ) {
        assertThat(result.selectedCount())
                .isEqualTo(selectedCount);

        assertThat(result.retryScheduledCount())
                .isEqualTo(retryScheduledCount);

        assertThat(result.failedCount())
                .isEqualTo(failedCount);
    }

    private static Stream<Arguments>
    negativeRecoveryBatchResultCases() {
        return Stream.of(
                Arguments.of(
                        -1,
                        0,
                        0,
                        "selectedCount"
                ),
                Arguments.of(
                        0,
                        -1,
                        0,
                        "retryScheduledCount"
                ),
                Arguments.of(
                        0,
                        0,
                        -1,
                        "failedCount"
                )
        );
    }
}
