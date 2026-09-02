package site.yesaido.ai_server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.yesaido.ai_server.entity.DailyFeedback;
import site.yesaido.ai_server.entity.DailyFeedbackOutbox;
import site.yesaido.ai_server.rabbitmq.event.AiEvent.DailyFeedbackGeneratedEvent;
import site.yesaido.ai_server.rabbitmq.event.DailyFeedbackGeneratedEventFactory;
import site.yesaido.ai_server.repository.DailyFeedbackOutboxRepository;
import site.yesaido.ai_server.repository.DailyFeedbackRepository;

import java.util.Objects;

/**
 * 일일 피드백과 PENDING Outbox를 하나의 트랜잭션으로 저장하는 서비스입니다.
 *
 * <p>호출 계층과 별도의 Spring Bean으로 구성하여 public 메서드 호출이
 * Spring 트랜잭션 프록시를 통과하도록 합니다. 같은 클래스 내부의
 * self-invocation으로 트랜잭션 적용이 우회되는 상황을 방지합니다.</p>
 *
 * <p>외부 API, Vision, LLM 및 RabbitMQ 호출은 이 트랜잭션 안에서
 * 수행하지 않습니다. 외부 데이터 수집과 피드백 생성이 모두 끝난 뒤
 * DB 저장만 짧게 수행합니다.</p>
 *
 * <p>일일 피드백과 PENDING Outbox는 함께 커밋되며, 이벤트 생성,
 * JSON 변환 또는 Outbox 저장 중 하나라도 실패하면 함께 롤백됩니다.</p>
 */
@Service
@RequiredArgsConstructor
public class DailyFeedbackAtomicWriter {

    private final DailyFeedbackRepository dailyFeedbackRepository;
    private final DailyFeedbackOutboxRepository dailyFeedbackOutboxRepository;
    private final DailyFeedbackGeneratedEventFactory dailyFeedbackGeneratedEventFactory;
    private final ObjectMapper objectMapper;

    /**
     * 신규 일일 피드백과 이에 대응하는 PENDING Outbox를 함께 저장합니다.
     *
     * <p>피드백을 먼저 flush하여 DB ID를 확정한 뒤, 저장된 피드백으로
     * 결정적인 완료 이벤트를 생성합니다. 생성된 이벤트를 Jackson 2
     * {@link JsonNode}으로 변환하여 Outbox Payload로 저장합니다.</p>
     *
     * <p>이 메서드에서 발생한 RuntimeException은 숨기지 않습니다.
     * 어느 단계에서든 실패하면 새 트랜잭션 전체가 롤백됩니다.</p>
     *
     * @param candidate 아직 저장되지 않은 신규 일일 피드백
     * @param ownerUserId 알림을 받을 경작지 OWNER 사용자 ID
     * @return DB에 저장된 일일 피드백
     * @throws NullPointerException candidate가 null인 경우
     * @throws IllegalArgumentException candidate가 이미 저장됐거나
     *                                  ownerUserId가 올바르지 않은 경우
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DailyFeedback saveWithPendingOutbox(DailyFeedback candidate, Long ownerUserId) {
        Objects.requireNonNull(candidate, "candidate는 null일 수 없습니다.");

        if (candidate.getId() != null) {
            throw new IllegalArgumentException("candidate는 아직 저장되지 않은 신규 엔티티여야 합니다.");
        }

        if (ownerUserId == null || ownerUserId <= 0) {
            throw new IllegalArgumentException("ownerUserId는 0보다 커야 합니다.");
        }

        DailyFeedback saved = dailyFeedbackRepository.saveAndFlush(candidate);

        DailyFeedbackGeneratedEvent event = dailyFeedbackGeneratedEventFactory.create(saved, ownerUserId);

        JsonNode payload = objectMapper.valueToTree(event);

        DailyFeedbackOutbox pendingOutbox = DailyFeedbackOutbox.pending(event.eventId(), saved.getId(), payload);

        dailyFeedbackOutboxRepository.saveAndFlush(pendingOutbox);

        return saved;
    }
}