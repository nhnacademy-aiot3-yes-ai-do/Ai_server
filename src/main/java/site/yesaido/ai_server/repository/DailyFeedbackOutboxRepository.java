package site.yesaido.ai_server.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.yesaido.ai_server.entity.DailyFeedbackOutbox;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 일일 피드백 완료 이벤트 Outbox를 저장하고 조회하는 Repository입니다.
 *
 * <p>{@code dailyFeedbackId}에는 UNIQUE 제약이 있으므로 하나의 일일 피드백에
 * 대응하는 Outbox는 최대 한 건만 존재합니다.</p>
 *
 * <p>잠금 조회 쿼리는 반드시 활성 DB 트랜잭션 안에서 호출해야 합니다.
 * PostgreSQL의 {@code SKIP LOCKED}는 다른 Pod가 이미 잠근 행을 기다리지
 * 않고 건너뛰어 여러 Pod가 같은 Outbox를 동시에 처리하지 않도록 합니다.</p>
 *
 * <p>조회만으로 선점이 완료되는 것은 아닙니다. 같은 트랜잭션 안에서
 * 엔티티의 claim 또는 복구 상태 전이를 수행한 뒤 커밋해야 변경된 상태와
 * 잠금 해제가 원자적으로 처리됩니다.</p>
 */
public interface DailyFeedbackOutboxRepository extends JpaRepository<DailyFeedbackOutbox, Long> {

    /**
     * 일일 피드백 ID에 대응하는 Outbox를 조회합니다.
     *
     * @param dailyFeedbackId 저장된 일일 피드백 ID
     * @return 해당 Outbox가 있으면 이를 포함하는 Optional
     */
    Optional<DailyFeedbackOutbox> findByDailyFeedbackId(Long dailyFeedbackId);

    /**
     * 현재 발행 가능한 PENDING Outbox를 잠금과 함께 조회합니다.
     *
     * <p>다른 트랜잭션이 이미 잠근 행은 기다리지 않고 건너뜁니다.
     * 호출자는 같은 트랜잭션 안에서 각 엔티티에
     * {@link DailyFeedbackOutbox#claim(LocalDateTime)}을 적용하고
     * 커밋해야 합니다.</p>
     *
     * @param claimedAt 선점 및 발행 가능 여부를 판단할 기준 시각
     * @param batchSize 한 번에 조회할 최대 행 수
     * @return 다른 트랜잭션이 잠그지 않은 발행 대상 목록
     */
    @Query(
            value = """
                      SELECT *
                      FROM daily_feedback_outbox
                      WHERE status = 'PENDING'
                        AND next_attempt_at <= :claimedAt
                      ORDER BY next_attempt_at ASC, id ASC
                      LIMIT :batchSize
                      FOR UPDATE SKIP LOCKED
                      """,
            nativeQuery = true
    )
    List<DailyFeedbackOutbox> findPendingForClaim(@Param("claimedAt") LocalDateTime claimedAt, @Param("batchSize") int batchSize);

    /**
     * 선점 후 장시간 SENDING 상태에 머문 Outbox를 복구 대상으로 조회합니다.
     *
     * <p>다른 트랜잭션이 이미 잠근 행은 기다리지 않고 건너뜁니다.
     * 호출자는 같은 트랜잭션 안에서 복구 상태 전이를 적용하고
     * 커밋해야 합니다.</p>
     *
     * @param staleBefore 오래된 선점을 판정하는 기준 시각
     * @param batchSize 한 번에 조회할 최대 행 수
     * @return 다른 트랜잭션이 잠그지 않은 복구 대상 목록
     */
    @Query(
            value = """
                      SELECT *
                      FROM daily_feedback_outbox
                      WHERE status = 'SENDING'
                        AND claimed_at IS NOT NULL
                        AND claimed_at <= :staleBefore
                      ORDER BY claimed_at ASC, id ASC
                      LIMIT :batchSize
                      FOR UPDATE SKIP LOCKED
                      """,
            nativeQuery = true
    )
    List<DailyFeedbackOutbox> findStaleSendingForRecovery(@Param("staleBefore") LocalDateTime staleBefore, @Param("batchSize") int batchSize);
}