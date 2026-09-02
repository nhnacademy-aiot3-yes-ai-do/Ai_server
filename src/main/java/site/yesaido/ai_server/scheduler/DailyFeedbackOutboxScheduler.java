package site.yesaido.ai_server.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import site.yesaido.ai_server.service.DailyFeedbackOutboxRecoveryService;
import site.yesaido.ai_server.service.DailyFeedbackOutboxRelayService;

/**
 * 일일 피드백 Outbox Relay와 오래된 SENDING 복구의 실행 시점을 관리합니다.
 *
 * <p>각 AI Server Pod에서 Scheduler가 동시에 실행되는 구조이며,
 * Scheduler 자체에는 별도의 분산 락을 두지 않습니다. 실제 Outbox 선점과
 * 복구의 동시성 제어는 Repository의 {@code FOR UPDATE SKIP LOCKED}가
 * 담당합니다.</p>
 *
 * <p>같은 Pod에서는 이전 실행이 끝난 시점부터 다음 실행 간격을 계산하도록
 * {@code fixedDelay}를 사용합니다. 이를 통해 한 작업의 실행이 길어져도
 * 해당 Pod에서 같은 작업이 연속으로 겹쳐 실행되는 것을 방지합니다.</p>
 *
 * <p>Scheduler는 실행 시점만 담당합니다. Outbox 상태 전이, RabbitMQ 발행,
 * 재시도 및 최종 실패 정책은 Relay Service와 Recovery Service가
 * 담당합니다.</p>
 *
 * <p>각 스케줄 메서드 안에서 {@link RuntimeException}을 처리하여 한 번의
 * 실행 실패가 이후 주기의 실행을 중단시키지 않도록 합니다. 복구할 수 없는
 * {@link Error}까지 숨기지 않도록 {@code Throwable}은 처리하지 않습니다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyFeedbackOutboxScheduler {

    private final DailyFeedbackOutboxRelayService
            dailyFeedbackOutboxRelayService;

    private final DailyFeedbackOutboxRecoveryService
            dailyFeedbackOutboxRecoveryService;

    /**
     * 발행 가능한 PENDING Outbox의 Relay 작업을 실행합니다.
     *
     * <p>실행 중 발생한 {@link RuntimeException}은 안전한 유형 정보만
     * 기록하고 처리하여 다음 실행 주기가 유지되도록 합니다.</p>
     */
    @Scheduled(
            fixedDelayString =
                    "${daily-feedback.outbox.relay-interval:5s}",
            initialDelayString =
                    "${daily-feedback.outbox.relay-interval:5s}"
    )
    public void relayPendingOutboxes() {
        try {
            DailyFeedbackOutboxRelayService.RelayBatchResult result =
                    dailyFeedbackOutboxRelayService.relayPending();

            if (result.claimedCount() == 0) {
                log.debug("일일 피드백 Outbox Relay 배치 완료: claimedCount={}, publishedCount={}, retryScheduledCount={}, failedCount={}, stateUpdateFailedCount={}",
                        0, result.publishedCount(), result.retryScheduledCount(), result.failedCount(), result.stateUpdateFailedCount());
            } else {
                log.info("일일 피드백 Outbox Relay 배치 완료: claimedCount={}, publishedCount={}, retryScheduledCount={}, failedCount={}, stateUpdateFailedCount={}",
                        result.claimedCount(), result.publishedCount(), result.retryScheduledCount(), result.failedCount(), result.stateUpdateFailedCount());
            }
        } catch (RuntimeException exception) {
            log.error("일일 피드백 Outbox Relay 실행 실패: exceptionType={}", exceptionType(exception));
        }
    }

    /**
     * 제한 시간을 초과해 SENDING 상태에 머문 Outbox의 복구 작업을 실행합니다.
     *
     * <p>실행 중 발생한 {@link RuntimeException}은 안전한 유형 정보만
     * 기록하고 처리하여 다음 실행 주기가 유지되도록 합니다.</p>
     */
    @Scheduled(
            fixedDelayString =
                    "${daily-feedback.outbox.recovery-interval:1m}",
            initialDelayString =
                    "${daily-feedback.outbox.recovery-interval:1m}"
    )
    public void recoverStaleSendingOutboxes() {
        try {
            DailyFeedbackOutboxRecoveryService.RecoveryBatchResult result =
                    dailyFeedbackOutboxRecoveryService.recoverStaleSending();

            if (result.selectedCount() == 0) {
                log.debug("일일 피드백 Outbox Recovery 배치 완료: selectedCount={}, retryScheduledCount={}, failedCount={}",
                        0, result.retryScheduledCount(), result.failedCount()
                );
            } else {
                log.info("일일 피드백 Outbox Recovery 배치 완료: selectedCount={}, retryScheduledCount={}, failedCount={}",
                        result.selectedCount(), result.retryScheduledCount(), result.failedCount()
                );
            }
        } catch (RuntimeException exception) {
            log.error("일일 피드백 Outbox Recovery 실행 실패: exceptionType={}", exceptionType(exception));
        }
    }

    private String exceptionType(RuntimeException exception) {
        String simpleName = exception.getClass().getSimpleName();

        if (simpleName == null || simpleName.isBlank()) {
            return RuntimeException.class.getSimpleName();
        }

        return simpleName;
    }
}
