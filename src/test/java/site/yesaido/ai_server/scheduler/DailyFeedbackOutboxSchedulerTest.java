package site.yesaido.ai_server.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;
import site.yesaido.ai_server.service.DailyFeedbackOutboxRecoveryService;
import site.yesaido.ai_server.service.DailyFeedbackOutboxRecoveryService.RecoveryBatchResult;
import site.yesaido.ai_server.service.DailyFeedbackOutboxRelayService;
import site.yesaido.ai_server.service.DailyFeedbackOutboxRelayService.RelayBatchResult;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyFeedbackOutboxSchedulerTest {

    private static final String RELAY_SCHEDULE_INTERVAL =
            "${daily-feedback.outbox.relay-interval:5s}";

    private static final String RECOVERY_SCHEDULE_INTERVAL =
            "${daily-feedback.outbox.recovery-interval:1m}";

    @Mock
    private DailyFeedbackOutboxRelayService
            dailyFeedbackOutboxRelayService;

    @Mock
    private DailyFeedbackOutboxRecoveryService
            dailyFeedbackOutboxRecoveryService;

    private DailyFeedbackOutboxScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new DailyFeedbackOutboxScheduler(
                dailyFeedbackOutboxRelayService,
                dailyFeedbackOutboxRecoveryService
        );
    }

    @Test
    @DisplayName("비어 있는 Relay 배치는 예외 없이 종료한다")
    void completeEmptyRelayBatch() {
        // 준비
        RelayBatchResult result =
                new RelayBatchResult(0, 0, 0, 0, 0);

        when(dailyFeedbackOutboxRelayService.relayPending())
                .thenReturn(result);

        // 실행
        assertDoesNotThrow(scheduler::relayPendingOutboxes);

        // 검증
        verify(dailyFeedbackOutboxRelayService).relayPending();
        verifyNoInteractions(dailyFeedbackOutboxRecoveryService);
    }

    @Test
    @DisplayName("처리된 Relay 배치는 예외 없이 종료한다")
    void completeProcessedRelayBatch() {
        // 준비
        RelayBatchResult result =
                new RelayBatchResult(4, 2, 1, 1, 0);

        when(dailyFeedbackOutboxRelayService.relayPending())
                .thenReturn(result);

        // 실행
        assertDoesNotThrow(scheduler::relayPendingOutboxes);

        // 검증
        verify(dailyFeedbackOutboxRelayService).relayPending();
        verifyNoInteractions(dailyFeedbackOutboxRecoveryService);
    }

    @Test
    @DisplayName("Relay RuntimeException은 외부로 전파하지 않는다")
    void suppressRelayRuntimeException() {
        // 준비
        when(dailyFeedbackOutboxRelayService.relayPending())
                .thenThrow(new RuntimeException("relay failure"));

        // 실행
        assertDoesNotThrow(scheduler::relayPendingOutboxes);

        // 검증
        verify(dailyFeedbackOutboxRelayService).relayPending();
        verifyNoInteractions(dailyFeedbackOutboxRecoveryService);
    }

    @Test
    @DisplayName("Relay가 null 결과를 반환해도 정상 복귀한다")
    void suppressNullRelayResultFailure() {
        // 준비
        when(dailyFeedbackOutboxRelayService.relayPending())
                .thenReturn(null);

        // 실행
        assertDoesNotThrow(scheduler::relayPendingOutboxes);

        // 검증
        verify(dailyFeedbackOutboxRelayService).relayPending();
        verifyNoInteractions(dailyFeedbackOutboxRecoveryService);
    }

    @Test
    @DisplayName("비어 있는 Recovery 배치는 예외 없이 종료한다")
    void completeEmptyRecoveryBatch() {
        // 준비
        RecoveryBatchResult result =
                new RecoveryBatchResult(0, 0, 0);

        when(dailyFeedbackOutboxRecoveryService.recoverStaleSending())
                .thenReturn(result);

        // 실행
        assertDoesNotThrow(
                scheduler::recoverStaleSendingOutboxes
        );

        // 검증
        verify(dailyFeedbackOutboxRecoveryService)
                .recoverStaleSending();
        verifyNoInteractions(dailyFeedbackOutboxRelayService);
    }

    @Test
    @DisplayName("처리된 Recovery 배치는 예외 없이 종료한다")
    void completeProcessedRecoveryBatch() {
        // 준비
        RecoveryBatchResult result =
                new RecoveryBatchResult(3, 2, 1);

        when(dailyFeedbackOutboxRecoveryService.recoverStaleSending())
                .thenReturn(result);

        // 실행
        assertDoesNotThrow(
                scheduler::recoverStaleSendingOutboxes
        );

        // 검증
        verify(dailyFeedbackOutboxRecoveryService)
                .recoverStaleSending();
        verifyNoInteractions(dailyFeedbackOutboxRelayService);
    }

    @Test
    @DisplayName("Recovery RuntimeException은 외부로 전파하지 않는다")
    void suppressRecoveryRuntimeException() {
        // 준비
        when(dailyFeedbackOutboxRecoveryService.recoverStaleSending())
                .thenThrow(new RuntimeException("recovery failure"));

        // 실행
        assertDoesNotThrow(
                scheduler::recoverStaleSendingOutboxes
        );

        // 검증
        verify(dailyFeedbackOutboxRecoveryService)
                .recoverStaleSending();
        verifyNoInteractions(dailyFeedbackOutboxRelayService);
    }

    @Test
    @DisplayName("Relay Error는 숨기지 않고 동일한 인스턴스로 전파한다")
    void propagateRelayError() {
        // 준비
        AssertionError expectedError =
                new AssertionError("relay fatal error");

        when(dailyFeedbackOutboxRelayService.relayPending())
                .thenThrow(expectedError);

        // 실행
        AssertionError propagatedError = catchThrowableOfType(
                AssertionError.class,
                scheduler::relayPendingOutboxes
        );

        // 검증
        assertThat(propagatedError).isSameAs(expectedError);
        verify(dailyFeedbackOutboxRelayService).relayPending();
        verifyNoInteractions(dailyFeedbackOutboxRecoveryService);
    }

    @Test
    @DisplayName("Relay 스케줄은 설정된 fixedDelay와 initialDelay를 사용한다")
    void configureRelaySchedule() throws NoSuchMethodException {
        // 준비
        Method method = DailyFeedbackOutboxScheduler.class
                .getDeclaredMethod("relayPendingOutboxes");

        // 실행
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        // 검증
        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedDelayString())
                .isEqualTo(RELAY_SCHEDULE_INTERVAL);
        assertThat(scheduled.initialDelayString())
                .isEqualTo(RELAY_SCHEDULE_INTERVAL);
    }

    @Test
    @DisplayName("Recovery 스케줄은 설정된 fixedDelay와 initialDelay를 사용한다")
    void configureRecoverySchedule() throws NoSuchMethodException {
        // 준비
        Method method = DailyFeedbackOutboxScheduler.class
                .getDeclaredMethod("recoverStaleSendingOutboxes");

        // 실행
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        // 검증
        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedDelayString())
                .isEqualTo(RECOVERY_SCHEDULE_INTERVAL);
        assertThat(scheduled.initialDelayString())
                .isEqualTo(RECOVERY_SCHEDULE_INTERVAL);
    }
}
