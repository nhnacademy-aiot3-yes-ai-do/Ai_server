package site.yesaido.ai_server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.yesaido.ai_server.config.DailyFeedbackOutboxProperties;
import site.yesaido.ai_server.entity.DailyFeedbackOutbox;
import site.yesaido.ai_server.repository.DailyFeedbackOutboxRepository;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 장시간 SENDING 상태에 머문 일일 피드백 Outbox를 복구합니다.
 *
 * <p>Pod 종료, 네트워크 단절 또는 RabbitMQ 발행 성공 후 상태 저장 실패가
 * 발생하면 Outbox가 SENDING 상태에 남을 수 있습니다. 이 서비스는 설정된
 * stale 제한 시간을 지난 Outbox를 다시 발행 가능한 상태 또는 최종 실패
 * 상태로 전환합니다.</p>
 *
 * <p>RabbitMQ 발행 성공 여부를 복구 시점에 확정할 수 없으므로 복구된
 * 이벤트는 동일한 {@code eventId}로 다시 발행될 수 있습니다. 따라서
 * 전달 보장은 중복 발행 가능성을 포함하는 {@code at-least-once}
 * 방식입니다.</p>
 *
 * <p>Repository의 {@code FOR UPDATE SKIP LOCKED} 조회, 엔티티 상태 전이와
 * flush를 하나의 REQUIRES_NEW 트랜잭션에서 수행합니다. 다른 AI Pod가 이미
 * 잠근 행은 건너뛰므로 여러 Pod가 동시에 실행되어도 같은 행을 중복
 * 복구하지 않습니다.</p>
 *
 * <p>현재 발행 시도 횟수가 최대 시도 횟수보다 작으면 즉시 다시 선점할 수
 * 있도록 PENDING 상태로 전환하고, 최대 시도 횟수에 도달했으면 FAILED
 * 상태로 전환합니다. 복구 과정에서는 발행 시도 횟수를 증가시키지 않으며,
 * 다음 Relay가 PENDING Outbox를 선점할 때 증가합니다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyFeedbackOutboxRecoveryService {

    private static final String STALE_SENDING_TIMEOUT =
            "StaleSendingTimeout";

    private final DailyFeedbackOutboxRepository
            dailyFeedbackOutboxRepository;

    private final DailyFeedbackOutboxProperties
            dailyFeedbackOutboxProperties;

    private final Clock clock;

    /**
     * 오래된 SENDING Outbox를 한 배치 조회하여 복구합니다.
     *
     * <p>조회, 모든 상태 전이와 flush는 하나의 새 트랜잭션에서 수행됩니다.
     * 조회나 상태 전이 또는 flush가 실패하면 예외를 숨기지 않고 전파하여
     * 배치 전체를 롤백합니다.</p>
     *
     * @return 조회한 수와 재시도 예약 및 최종 실패 수를 담은 복구 결과
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RecoveryBatchResult recoverStaleSending() {
        LocalDateTime recoveredAt = LocalDateTime.now(clock);

        Duration staleTimeout =
                dailyFeedbackOutboxProperties.getStaleTimeout();

        LocalDateTime staleBefore =
                subtractWithLowerBound(
                        recoveredAt,
                        staleTimeout
                );

        List<DailyFeedbackOutbox> staleOutboxes =
                dailyFeedbackOutboxRepository
                        .findStaleSendingForRecovery(
                                staleBefore,
                                dailyFeedbackOutboxProperties
                                        .getBatchSize()
                        );

        int maxAttempts =
                dailyFeedbackOutboxProperties.getMaxAttempts();

        int retryScheduledCount = 0;
        int failedCount = 0;

        for (DailyFeedbackOutbox outbox : staleOutboxes) {
            RecoveryDisposition disposition;

            if (outbox.getAttemptCount() < maxAttempts) {
                outbox.scheduleRetry(
                        recoveredAt,
                        recoveredAt,
                        STALE_SENDING_TIMEOUT
                );

                retryScheduledCount++;
                disposition =
                        RecoveryDisposition.RETRY_SCHEDULED;
            } else {
                outbox.markFailed(
                        recoveredAt,
                        STALE_SENDING_TIMEOUT
                );

                failedCount++;
                disposition = RecoveryDisposition.FAILED;
            }

            logRecovery(outbox, disposition);
        }

        dailyFeedbackOutboxRepository.flush();

        RecoveryBatchResult result =
                new RecoveryBatchResult(
                        staleOutboxes.size(),
                        retryScheduledCount,
                        failedCount
                );

        log.info(
                "일일 피드백 Outbox 복구 배치 완료: "
                        + "selectedCount={}, "
                        + "retryScheduledCount={}, failedCount={}",
                result.selectedCount(),
                result.retryScheduledCount(),
                result.failedCount()
        );

        return result;
    }

    private LocalDateTime subtractWithLowerBound(
            LocalDateTime recoveredAt,
            Duration staleTimeout
    ) {
        try {
            return recoveredAt.minus(staleTimeout);
        } catch (DateTimeException | ArithmeticException exception) {
            return LocalDateTime.MIN;
        }
    }

    private void logRecovery(
            DailyFeedbackOutbox outbox,
            RecoveryDisposition disposition
    ) {
        log.info("일일 피드백 Outbox 복구 완료: outboxId={}, eventId={}, dailyFeedbackId={}, "
                        + "attemptCount={}, result={}",
                outbox.getId(),
                outbox.getEventId(),
                outbox.getDailyFeedbackId(),
                outbox.getAttemptCount(),
                disposition
        );
    }

    private enum RecoveryDisposition {
        RETRY_SCHEDULED,
        FAILED
    }

    /**
     * 한 번의 복구 배치에서 조회한 Outbox 수와 상태 전이 결과의
     * 개수를 나타냅니다.
     *
     * <p>모든 개수는 음수가 아니며, 재시도 예약 수와 최종 실패 수의 합은
     * 전체 조회 수와 일치합니다.</p>
     *
     * @param selectedCount 복구 대상으로 조회한 전체 Outbox 수
     * @param retryScheduledCount PENDING 상태로 전환한 Outbox 수
     * @param failedCount FAILED 상태로 전환한 Outbox 수
     */
    public record RecoveryBatchResult(int selectedCount, int retryScheduledCount, int failedCount) {

        public RecoveryBatchResult {
            if (selectedCount < 0 || retryScheduledCount < 0 || failedCount < 0) {
                throw new IllegalArgumentException("복구 배치 결과 개수는 음수일 수 없습니다.");
            }

            long processedCount = (long) retryScheduledCount + failedCount;

            if (processedCount != selectedCount) {
                throw new IllegalArgumentException("복구 처리 결과의 합은 조회 개수와 같아야 합니다.");
            }
        }
    }
}
