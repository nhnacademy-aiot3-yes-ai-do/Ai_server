package site.yesaido.ai_server.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.yesaido.ai_server.entity.DailyFeedbackOutbox;
import site.yesaido.ai_server.repository.DailyFeedbackOutboxRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 발행 가능한 PENDING Outbox를 여러 AI Pod 사이에서 안전하게 선점합니다.
 *
 * <p>PostgreSQL의 {@code FOR UPDATE SKIP LOCKED} 조회와 엔티티의
 * {@link DailyFeedbackOutbox#claim(LocalDateTime)} 상태 전이를 하나의
 * 새 트랜잭션에서 수행합니다. 다른 Pod가 이미 잠근 행은 기다리지 않고
 * 건너뛰므로 각 Pod가 서로 다른 Outbox를 선점할 수 있습니다.</p>
 *
 * <p>잠금 조회, {@link DailyFeedbackOutbox#claim(LocalDateTime)}
 * 상태 전이와 시도 횟수 증가는 하나의 REQUIRES_NEW 트랜잭션에서
 * 수행됩니다. 조회와 상태 전이가 서로 다른 트랜잭션에서 실행되면
 * 잠금이 먼저 해제되어 다른 Pod가 같은 행을 다시 조회할 수 있습니다.</p>
 *
 * <p>반환되는 {@link ClaimedOutbox}는 트랜잭션 밖에서 RabbitMQ 발행에
 * 사용할 불변 Snapshot입니다. RabbitMQ를 포함한 네트워크 호출은
 * DB 트랜잭션이 커밋되고 잠금이 해제된 뒤 별도 Relay가 수행합니다.</p>
 *
 * <p>이 서비스는 RabbitMQ 발행, 발행 결과 상태 변경과 오래된
 * SENDING 상태 복구를 담당하지 않습니다.</p>
 */
@Service
@RequiredArgsConstructor
public class DailyFeedbackOutboxClaimService {

    private final DailyFeedbackOutboxRepository dailyFeedbackOutboxRepository;

    /**
     * 현재 발행 가능한 PENDING Outbox를 SENDING 상태로 선점합니다.
     *
     * <p>조회된 모든 엔티티에 동일한 {@code claimedAt}을 적용하고,
     * 변경사항을 명시적으로 flush한 뒤 불변 Snapshot으로 변환합니다.
     * 조회, 상태 전이 또는 flush 중 하나라도 실패하면 예외를 숨기지
     * 않고 새 트랜잭션 전체를 롤백합니다.</p>
     *
     * @param claimedAt 선점 시각이자 발행 가능 여부를 판단할 기준 시각
     * @param batchSize 한 번에 선점할 최대 행 수
     * @return 선점이 완료된 Outbox의 불변 Snapshot 목록
     * @throws NullPointerException claimedAt이 null인 경우
     * @throws IllegalArgumentException batchSize가 0 이하인 경우
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<ClaimedOutbox> claimPending(LocalDateTime claimedAt, int batchSize) {
        LocalDateTime validatedClaimedAt = Objects.requireNonNull(claimedAt, "claimedAt은 null일 수 없습니다.");

        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize는 0보다 커야 합니다.");
        }

        List<DailyFeedbackOutbox> outboxes = dailyFeedbackOutboxRepository.findPendingForClaim(validatedClaimedAt, batchSize);

        for (DailyFeedbackOutbox outbox : outboxes) {
            outbox.claim(validatedClaimedAt);
        }

        dailyFeedbackOutboxRepository.flush();

        List<ClaimedOutbox> claimedOutboxes = new ArrayList<>(outboxes.size());

        for (DailyFeedbackOutbox outbox : outboxes) {
            claimedOutboxes.add(ClaimedOutbox.from(outbox));
        }

        return List.copyOf(claimedOutboxes);
    }

    /**
     * DB 선점이 완료된 Outbox의 RabbitMQ 발행용 불변 Snapshot입니다.
     *
     * <p>JPA 엔티티를 트랜잭션 밖으로 노출하지 않으며, 변경 가능한
     * {@link JsonNode} Payload는 생성 시와 접근 시 모두 방어적으로
     * 복사합니다.</p>
     *
     * @param outboxId 선점된 Outbox의 DB ID
     * @param eventId 외부 이벤트의 결정적인 ID
     * @param dailyFeedbackId 저장된 일일 피드백 ID
     * @param attemptCount 현재 발행 시도 횟수
     * @param payload RabbitMQ에 발행할 이벤트 Payload
     * @param claimedAt 이번 발행 작업이 선점한 시각
     */
    public static record ClaimedOutbox(
            Long outboxId,
            UUID eventId,
            Long dailyFeedbackId,
            int attemptCount,
            JsonNode payload,
            LocalDateTime claimedAt
    ) {

        public ClaimedOutbox {
            if (outboxId == null || outboxId <= 0) {
                throw new IllegalArgumentException("outboxId는 0보다 커야 합니다.");
            }

            Objects.requireNonNull(eventId, "eventId는 null일 수 없습니다.");

            if (dailyFeedbackId == null || dailyFeedbackId <= 0) {
                throw new IllegalArgumentException("dailyFeedbackId는 0보다 커야 합니다.");
            }

            if (attemptCount <= 0) {
                throw new IllegalArgumentException("attemptCount는 0보다 커야 합니다.");
            }

            JsonNode validatedPayload = Objects.requireNonNull(payload, "payload는 null일 수 없습니다.");

            if (!validatedPayload.isObject()) {
                throw new IllegalArgumentException("payload는 JSON object여야 합니다.");
            }

            payload = validatedPayload.deepCopy();
            Objects.requireNonNull(claimedAt, "claimedAt은 null일 수 없습니다.");
        }

        private static ClaimedOutbox from(DailyFeedbackOutbox outbox) {
            Objects.requireNonNull(outbox, "outbox는 null일 수 없습니다.");

            return new ClaimedOutbox(
                    outbox.getId(),
                    outbox.getEventId(),
                    outbox.getDailyFeedbackId(),
                    outbox.getAttemptCount(),
                    outbox.getPayload(),
                    outbox.getClaimedAt()
            );
        }

        /**
         * RabbitMQ 발행 Payload의 방어적 복사본을 반환합니다.
         *
         * @return Payload의 방어적 복사본
         */
        @Override
        public JsonNode payload() {
            return payload.deepCopy();
        }
    }
}
