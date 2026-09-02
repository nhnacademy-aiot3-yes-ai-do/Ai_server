package site.yesaido.ai_server.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

/**
 * 일일 피드백 완료 이벤트를 RabbitMQ에 발행하기 위한 Outbox 엔티티입니다.
 *
 * <p>일일 피드백과 JPA 연관관계를 맺지 않고 저장이 확정된 피드백의 ID만
 * 보관합니다. 발행 상태는 RabbitMQ 전송 과정만 나타내며 Notification 저장이나
 * Discord 최종 전송 상태를 의미하지 않습니다.</p>
 *
 * <p>신규 이벤트는 {@link DailyFeedbackOutboxStatus#PENDING} 상태로 생성하며,
 * 발행 과정의 상태 전이는 이 엔티티가 제공하는 메서드를 통해서만 수행합니다.</p>
 */
@Entity
@Table(
        name = "daily_feedback_outbox",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_daily_feedback_outbox_event_id",
                        columnNames = "event_id"
                ),
                @UniqueConstraint(
                        name = "uk_daily_feedback_outbox_feedback_id",
                        columnNames = "daily_feedback_id"
                )
        },
        indexes = {
                @Index(
                        name = "idx_daily_feedback_outbox_dispatch",
                        columnList = "status, next_attempt_at, id"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyFeedbackOutbox {

    private static final String DAILY_FEEDBACK_GENERATED_EVENT_TYPE =
            "DAILY_FEEDBACK_GENERATED";

    private static final String UNKNOWN_FAILURE_TYPE =
            "UnknownFailure";

    private static final int MAX_ERROR_TYPE_LENGTH = 255;

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(
            name = "daily_feedback_id",
            nullable = false,
            updatable = false)
    private Long dailyFeedbackId;

    @Column(
            name = "event_type",
            nullable = false,
            length = 80,
            updatable = false
    )
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "payload",
            nullable = false,
            updatable = false,
            columnDefinition = "jsonb"
    )
    private JsonNode payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DailyFeedbackOutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "last_error_type", length = 255)
    private String lastErrorType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private DailyFeedbackOutbox(
            UUID eventId,
            Long dailyFeedbackId,
            JsonNode payload
    ) {
        UUID validatedEventId = Objects.requireNonNull(
                eventId,
                "eventId는 null일 수 없습니다."
        );

        Long validatedDailyFeedbackId = Objects.requireNonNull(
                dailyFeedbackId,
                "dailyFeedbackId는 null일 수 없습니다."
        );

        if (validatedDailyFeedbackId <= 0) {
            throw new IllegalArgumentException(
                    "dailyFeedbackId는 0보다 커야 합니다."
            );
        }

        JsonNode validatedPayload = Objects.requireNonNull(
                payload,
                "payload는 null일 수 없습니다."
        );

        if (!validatedPayload.isObject()) {
            throw new IllegalArgumentException(
                    "payload는 JSON object여야 합니다."
            );
        }

        LocalDateTime now = LocalDateTime.now(SEOUL_ZONE);

        this.eventId = validatedEventId;
        this.dailyFeedbackId = validatedDailyFeedbackId;
        this.eventType = DAILY_FEEDBACK_GENERATED_EVENT_TYPE;
        this.payload = validatedPayload.deepCopy();
        this.status = DailyFeedbackOutboxStatus.PENDING;
        this.attemptCount = 0;
        this.nextAttemptAt = now;
        this.claimedAt = null;
        this.publishedAt = null;
        this.lastErrorType = null;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * 신규 일일 피드백 완료 이벤트를 발행 대기 상태로 생성합니다.
     *
     * @param eventId 외부 이벤트를 식별하는 ID
     * @param dailyFeedbackId 저장이 완료된 일일 피드백 ID
     * @param payload 발행할 이벤트 Payload
     * @return 신규 발행 대기 Outbox
     */
    public static DailyFeedbackOutbox pending(
            UUID eventId,
            Long dailyFeedbackId,
            JsonNode payload
    ) {
        return new DailyFeedbackOutbox(
                eventId,
                dailyFeedbackId,
                payload
        );
    }

    /**
     * 발행 대기 중인 이벤트를 현재 발행 작업이 선점합니다.
     *
     * <p>선점 시 발행 시도 횟수를 증가시키며, 이전 발행 실패 유형은
     * 재시도 이력 확인을 위해 유지합니다.</p>
     *
     * @param claimedAt 발행 작업이 이벤트를 선점한 시각
     */
    public void claim(LocalDateTime claimedAt) {
        requireStatus(DailyFeedbackOutboxStatus.PENDING);

        LocalDateTime validatedClaimedAt = Objects.requireNonNull(
                claimedAt,
                "claimedAt은 null일 수 없습니다."
        );

        if (validatedClaimedAt.isBefore(nextAttemptAt)) {
            throw new IllegalArgumentException(
                    "claimedAt은 nextAttemptAt보다 이전일 수 없습니다."
            );
        }

        int incrementedAttemptCount =
                Math.incrementExact(attemptCount);

        this.status = DailyFeedbackOutboxStatus.SENDING;
        this.attemptCount = incrementedAttemptCount;
        this.claimedAt = validatedClaimedAt;
        this.updatedAt = validatedClaimedAt;
    }

    /**
     * 현재 선점한 이벤트의 RabbitMQ 발행 성공을 기록합니다.
     *
     * <p>성공 시 마지막 오류 유형을 제거하지만, 마지막 선점 시각은
     * 발행 이력으로 유지합니다.</p>
     *
     * @param publishedAt RabbitMQ 발행이 성공한 시각
     */
    public void markPublished(LocalDateTime publishedAt) {
        requireStatus(DailyFeedbackOutboxStatus.SENDING);

        LocalDateTime validatedPublishedAt =
                validateTransitionTime(
                        publishedAt,
                        "publishedAt"
                );

        this.status = DailyFeedbackOutboxStatus.PUBLISHED;
        this.publishedAt = validatedPublishedAt;
        this.lastErrorType = null;
        this.updatedAt = validatedPublishedAt;
    }

    /**
     * 현재 발행 시도의 실패를 기록하고 다음 발행 시도를 예약합니다.
     *
     * <p>{@code lastErrorType}에는 예외 메시지나 URL이 아니라
     * 예외 클래스명만 전달해야 합니다. 발행 시도 횟수는 선점 시 이미
     * 증가했으므로 이 메서드에서는 변경하지 않습니다.</p>
     *
     * @param failureAt 현재 발행 시도가 실패한 시각
     * @param nextAttemptAt 다음 발행을 시도할 시각
     * @param lastErrorType 발행 실패를 나타내는 예외 클래스명
     */
    public void scheduleRetry(
            LocalDateTime failureAt,
            LocalDateTime nextAttemptAt,
            String lastErrorType
    ) {
        requireStatus(DailyFeedbackOutboxStatus.SENDING);

        LocalDateTime validatedFailureAt =
                validateTransitionTime(
                        failureAt,
                        "failureAt"
                );

        LocalDateTime validatedNextAttemptAt =
                Objects.requireNonNull(
                        nextAttemptAt,
                        "nextAttemptAt은 null일 수 없습니다."
                );

        if (validatedNextAttemptAt.isBefore(validatedFailureAt)) {
            throw new IllegalArgumentException(
                    "nextAttemptAt은 failureAt보다 이전일 수 없습니다."
            );
        }

        String normalizedLastErrorType =
                normalizeLastErrorType(lastErrorType);

        this.status = DailyFeedbackOutboxStatus.PENDING;
        this.nextAttemptAt = validatedNextAttemptAt;
        this.claimedAt = null;
        this.publishedAt = null;
        this.lastErrorType = normalizedLastErrorType;
        this.updatedAt = validatedFailureAt;
    }

    /**
     * 재시도 횟수를 모두 소진한 현재 발행 시도를 최종 실패로 기록합니다.
     *
     * <p>최대 재시도 횟수의 소진 여부는 발행 서비스가 판단합니다.
     * {@code lastErrorType}에는 예외 메시지나 URL이 아니라
     * 예외 클래스명만 전달해야 합니다.</p>
     *
     * @param failureAt 현재 발행 시도가 최종 실패한 시각
     * @param lastErrorType 발행 실패를 나타내는 예외 클래스명
     */
    public void markFailed(
            LocalDateTime failureAt,
            String lastErrorType
    ) {
        requireStatus(DailyFeedbackOutboxStatus.SENDING);

        LocalDateTime validatedFailureAt =
                validateTransitionTime(
                        failureAt,
                        "failureAt"
                );

        String normalizedLastErrorType =
                normalizeLastErrorType(lastErrorType);

        this.status = DailyFeedbackOutboxStatus.FAILED;
        this.lastErrorType = normalizedLastErrorType;
        this.updatedAt = validatedFailureAt;
    }

    /**
     * 발행할 이벤트 Payload의 방어적 복사본을 반환합니다.
     *
     * <p>{@link JsonNode}은 변경 가능한 객체이므로 내부 값을 직접 노출하지
     * 않습니다. JPA 기본 생성 직후처럼 값이 초기화되지 않았다면 null을
     * 반환합니다.</p>
     *
     * <p>{@link Id}가 필드에 선언되어 이 엔티티는 JPA field access 방식을
     * 사용하므로 명시적 getter가 영속성 매핑을 변경하지 않습니다.</p>
     *
     * @return Payload의 방어적 복사본 또는 null
     */
    public JsonNode getPayload() {
        return payload == null ? null : payload.deepCopy();
    }

    private void requireStatus(DailyFeedbackOutboxStatus requiredStatus) {
        if (status != requiredStatus) {
            throw new IllegalStateException("현재 상태는 " + status + "이며 필요한 상태는 " + requiredStatus + "입니다.");
        }
    }

    private LocalDateTime validateTransitionTime(LocalDateTime transitionAt, String fieldName) {
        LocalDateTime validatedTransitionAt = Objects.requireNonNull(transitionAt, fieldName + "은 null일 수 없습니다.");

        if (claimedAt != null && validatedTransitionAt.isBefore(claimedAt)) {
            throw new IllegalArgumentException(fieldName + "은 claimedAt보다 이전일 수 없습니다.");
        }

        return validatedTransitionAt;
    }

    /**
     * 발행 실패의 예외 유형을 저장 가능한 형태로 정규화합니다.
     *
     * <p>호출자는 예외 메시지나 URL이 아닌 예외 클래스명만 전달해야 합니다.</p>
     *
     * @param lastErrorType 발행 실패를 나타내는 예외 클래스명
     * @return 앞뒤 공백을 제거하고 최대 길이를 제한한 오류 유형
     */
    private String normalizeLastErrorType(String lastErrorType) {
        if (lastErrorType == null || lastErrorType.isBlank()) {
            return UNKNOWN_FAILURE_TYPE;
        }

        String normalizedLastErrorType = lastErrorType.strip();

        if (normalizedLastErrorType.length() <= MAX_ERROR_TYPE_LENGTH) {
            return normalizedLastErrorType;
        }

        return normalizedLastErrorType.substring(0, MAX_ERROR_TYPE_LENGTH);
    }
}
