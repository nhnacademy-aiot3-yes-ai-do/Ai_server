package site.yesaido.ai_server.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.yesaido.ai_server.entity.DailyFeedbackOutbox;
import site.yesaido.ai_server.entity.DailyFeedbackOutboxStatus;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * RabbitMQ 발행 결과를 일일 피드백 Outbox에 기록하는 서비스입니다.
 *
 * <p>RabbitMQ 네트워크 호출은 DB 트랜잭션 밖에서 이미 완료된 상태이며,
 * 이 서비스는 발행 성공, 재시도 예약 또는 최종 실패 상태만 각각 짧은
 * REQUIRES_NEW 트랜잭션으로 저장합니다.</p>
 *
 * <p>상태 변경 전 Outbox 행에 {@link LockModeType#PESSIMISTIC_WRITE}
 * 잠금을 적용하여 다른 Pod의 복구 작업이나 상태 변경과 충돌하지
 * 않도록 합니다.</p>
 *
 * <p>{@code attemptCount}는 발행 작업의 선점 토큰으로 사용됩니다.
 * 선점할 때마다 정확히 증가하므로 이전 발행 작업이 늦게 완료되더라도
 * 새로운 발행 작업의 상태를 덮어쓸 수 없습니다. 시각은 PostgreSQL과
 * Java 사이에서 정밀도가 달라질 수 있으므로 선점 식별값으로 사용하지
 * 않습니다.</p>
 *
 * <p>RabbitMQ 발행 후 상태 저장에 실패하면 Outbox는 SENDING 상태로
 * 남을 수 있으며, 이후 오래된 SENDING 복구 작업이 다시 처리합니다.
 * 따라서 이 구조의 전달 보장은 exactly-once가 아닌
 * {@code at-least-once}입니다.</p>
 */
@Service
@RequiredArgsConstructor
public class DailyFeedbackOutboxStateService {

    private final EntityManager entityManager;

    /**
     * RabbitMQ 발행 성공을 PUBLISHED 상태로 기록합니다.
     *
     * <p>행 잠금 후 현재 상태와 발행 시도의 {@code attemptCount}를
     * 검증합니다. 이전 발행 작업의 선점 토큰이면 상태를 변경하지
     * 않고 실패합니다.</p>
     *
     * @param outboxId 상태를 변경할 Outbox ID
     * @param expectedAttemptCount 발행 작업이 선점할 때 받은 시도 횟수
     * @param publishedAt RabbitMQ 발행이 성공한 시각
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(Long outboxId, int expectedAttemptCount, LocalDateTime publishedAt) {
        validateOutboxId(outboxId);
        validateExpectedAttemptCount(expectedAttemptCount);

        LocalDateTime validatedPublishedAt = Objects.requireNonNull(publishedAt, "publishedAt은 null일 수 없습니다.");

        DailyFeedbackOutbox outbox = loadClaimedOutbox(outboxId, expectedAttemptCount);

        outbox.markPublished(validatedPublishedAt);

        entityManager.flush();
    }

    /**
     * RabbitMQ 발행 실패를 재시도 예약 또는 최종 실패 상태로 기록합니다.
     *
     * <p>현재 시도 횟수가 최대 시도 횟수 이상이면 FAILED로 전환합니다.
     * 아직 시도 가능 횟수가 남아 있으면 전달받은 다음 시도 시각으로
     * 재시도를 예약하고 PENDING 상태로 되돌립니다.</p>
     *
     * <p>{@code lastErrorType}에는 예외 메시지나 URL이 아닌
     * 예외 클래스명만 전달해야 합니다. 실제 정규화와 길이 제한은
     * Outbox 엔티티가 담당합니다.</p>
     *
     * @param outboxId 상태를 변경할 Outbox ID
     * @param expectedAttemptCount 발행 작업이 선점할 때 받은 시도 횟수
     * @param failureAt RabbitMQ 발행이 실패한 시각
     * @param nextAttemptAt 다음 발행을 시도할 시각
     * @param maxAttempts 허용할 최대 발행 시도 횟수
     * @param lastErrorType 발행 실패를 나타내는 예외 클래스명
     * @return 재시도 예약 또는 최종 실패 처리 결과
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FailureDisposition recordFailure(
            Long outboxId,
            int expectedAttemptCount,
            LocalDateTime failureAt,
            LocalDateTime nextAttemptAt,
            int maxAttempts,
            String lastErrorType
    ) {
        validateOutboxId(outboxId);
        validateExpectedAttemptCount(expectedAttemptCount);

        LocalDateTime validatedFailureAt = Objects.requireNonNull(failureAt, "failureAt은 null일 수 없습니다.");

        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts는 0보다 커야 합니다.");
        }

        DailyFeedbackOutbox outbox = loadClaimedOutbox(outboxId, expectedAttemptCount);

        if (outbox.getAttemptCount() >= maxAttempts) {
            outbox.markFailed(validatedFailureAt, lastErrorType);

            entityManager.flush();

            return FailureDisposition.FAILED;
        }

        LocalDateTime validatedNextAttemptAt = Objects.requireNonNull(nextAttemptAt, "nextAttemptAt은 null일 수 없습니다.");

        outbox.scheduleRetry(validatedFailureAt, validatedNextAttemptAt, lastErrorType);

        entityManager.flush();

        return FailureDisposition.RETRY_SCHEDULED;
    }

    private void validateOutboxId(Long outboxId) {
        if (outboxId == null || outboxId <= 0) {
            throw new IllegalArgumentException("outboxId는 0보다 커야 합니다.");
        }
    }

    private void validateExpectedAttemptCount(int expectedAttemptCount) {
        if (expectedAttemptCount <= 0) {
            throw new IllegalArgumentException("expectedAttemptCount는 0보다 커야 합니다.");
        }
    }

    private DailyFeedbackOutbox loadClaimedOutbox(Long outboxId, int expectedAttemptCount) {
        DailyFeedbackOutbox outbox = entityManager.find(
                DailyFeedbackOutbox.class, outboxId, LockModeType.PESSIMISTIC_WRITE);

        if (outbox == null) {
            throw new IllegalStateException("Outbox를 찾을 수 없습니다. outboxId=" + outboxId);
        }

        if (outbox.getStatus() != DailyFeedbackOutboxStatus.SENDING) {
            throw new IllegalStateException("Outbox 상태가 올바르지 않습니다. outboxId=" + outboxId
                    + ", status=" + outbox.getStatus()
                    + ", requiredStatus=" + DailyFeedbackOutboxStatus.SENDING);
        }

        if (outbox.getAttemptCount() != expectedAttemptCount) {
            throw new IllegalStateException("Outbox 선점 토큰이 일치하지 않습니다. outboxId=" + outboxId
                    + ", expectedAttemptCount=" + expectedAttemptCount
                    + ", actualAttemptCount=" + outbox.getAttemptCount());
        }

        return outbox;
    }

    /**
     * RabbitMQ 발행 실패 이후 저장된 Outbox 상태입니다.
     */
    public enum FailureDisposition {

        /**
         * 아직 발행 시도가 남아 다음 재시도를 예약한 상태입니다.
         */
        RETRY_SCHEDULED,

        /**
         * 최대 발행 시도 횟수를 소진하여 최종 실패한 상태입니다.
         */
        FAILED
    }
}
