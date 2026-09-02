package site.yesaido.ai_server.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.yesaido.ai_server.entity.DailyFeedback;
import site.yesaido.ai_server.repository.DailyFeedbackRepository;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * 일일 피드백의 조회와 멱등 저장만 담당하는 서비스입니다.
 *
 * <p>같은 cultivationId와 feedbackDate에 대해 여러 Pod가 동시에
 * 저장을 시도하더라도 DB UNIQUE 제약을 최종 기준으로 사용하여
 * 하나의 피드백만 보존합니다.</p>
 *
 * <p>외부 데이터 수집, LLM 호출, Context JSON 변환,
 * RabbitMQ 이벤트 발행은 담당하지 않습니다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class DailyFeedbackPersistenceService {

    private final DailyFeedbackRepository dailyFeedbackRepository;
    private final DailyFeedbackAtomicWriter dailyFeedbackAtomicWriter;

    /**
     * 같은 경작지와 날짜에 이미 저장된 피드백을 조회합니다.
     *
     * <p>Context 수집과 LLM 호출 전에 사용하여 이미 생성된 피드백을
     * 불필요하게 다시 생성하지 않도록 합니다.</p>
     *
     * @param cultivationId 조회할 경작지 ID
     * @param feedbackDate 조회할 피드백 대상 날짜
     * @return 저장된 피드백 또는 빈 Optional
     */
    public Optional<DailyFeedback> findExisting(Long cultivationId, LocalDate feedbackDate) {
        validateKey(cultivationId, feedbackDate);

        return dailyFeedbackRepository.findByCultivationIdAndFeedbackDate(cultivationId, feedbackDate);
    }

    /**
     * 신규 피드백과 PENDING Outbox를 저장하거나 동일 키의 기존 피드백을
     * 반환합니다.
     *
     * <p>선조회 이후 다른 Pod가 먼저 저장할 수 있으므로 신규 저장은
     * {@link DailyFeedbackAtomicWriter}의 REQUIRES_NEW 트랜잭션에서
     * 수행합니다. 피드백 또는 Outbox의 UNIQUE 충돌이 발생하면
     * 새 트랜잭션은 예외가 이 메서드로 전달되기 전에 완전히 롤백됩니다.</p>
     *
     * <p>롤백이 완료된 뒤 같은 경작지와 날짜의 피드백을 재조회하여
     * 기존 행이 확인되면 정상적인 동시 실행 결과로 처리합니다.
     * 기존 행이 없다면 다른 DB 제약 위반일 수 있으므로 원래
     * {@link DataIntegrityViolationException}을 다시 던집니다.</p>
     *
     * @param candidate 저장되지 않은 신규 DailyFeedback 엔티티
     * @param ownerUserId 완료 이벤트를 받을 경작지 OWNER 사용자 ID
     * @return 실제 DB 기준 피드백과 이번 호출의 저장 결과
     */
    public PersistenceResult saveOrGet(DailyFeedback candidate, Long ownerUserId) {
        Objects.requireNonNull(candidate, "candidate는 null일 수 없습니다.");

        if (candidate.getId() != null) {
            throw new IllegalArgumentException("candidate는 아직 저장되지 않은 신규 엔티티여야 합니다.");
        }

        validateOwnerUserId(ownerUserId);

        Long cultivationId = candidate.getCultivationId();
        LocalDate feedbackDate = candidate.getFeedbackDate();

        validateKey(cultivationId, feedbackDate);

        Optional<DailyFeedback> existing = dailyFeedbackRepository.findByCultivationIdAndFeedbackDate(cultivationId, feedbackDate);

        if (existing.isPresent()) {
            return new PersistenceResult(existing.get(), PersistenceStatus.EXISTING);
        }

        try {
            DailyFeedback saved = dailyFeedbackAtomicWriter.saveWithPendingOutbox(candidate, ownerUserId);

            return new PersistenceResult(saved, PersistenceStatus.CREATED);
        } catch (DataIntegrityViolationException conflict) {
            Optional<DailyFeedback> concurrentlySaved =
                    dailyFeedbackRepository.findByCultivationIdAndFeedbackDate(cultivationId, feedbackDate);

            if (concurrentlySaved.isPresent()) {
                return new PersistenceResult(concurrentlySaved.get(), PersistenceStatus.EXISTING);
            }

            throw conflict;
        }
    }

    private void validateKey(Long cultivationId, LocalDate feedbackDate) {
        if (cultivationId == null || cultivationId <= 0) {
            throw new IllegalArgumentException("cultivationId는 null이 아니며 0보다 커야 합니다.");
        }

        if (feedbackDate == null) {
            throw new IllegalArgumentException("feedbackDate는 null일 수 없습니다.");
        }
    }

    private void validateOwnerUserId(Long ownerUserId) {
        if (ownerUserId == null || ownerUserId <= 0) {
            throw new IllegalArgumentException("ownerUserId는 null이 아니며 0보다 커야 합니다.");
        }
    }

    /**
     * 이번 저장 시도의 결과입니다.
     */
    public enum PersistenceStatus {
        CREATED,
        EXISTING
    }

    /**
     * 후속 처리에는 전달받은 후보가 아니라 DB에서 확정된 피드백을 사용합니다.
     *
     * @param feedback 실제 저장되거나 조회된 DB 기준 피드백
     * @param status 신규 저장 또는 기존 행 반환 여부
     */
    public record PersistenceResult(DailyFeedback feedback, PersistenceStatus status) {

        public PersistenceResult {
            Objects.requireNonNull(feedback, "feedback은 null일 수 없습니다.");
            Objects.requireNonNull(status, "status는 null일 수 없습니다.");
        }
    }
}
