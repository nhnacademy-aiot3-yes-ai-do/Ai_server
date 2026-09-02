package site.yesaido.ai_server.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

@Entity
@Table(
        name = "daily_feedback",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_daily_feedback_cultivation_date",
                        columnNames = {
                                "cultivation_id",
                                "feedback_date"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_daily_feedback_feedback_date",
                        columnList = "feedback_date"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 다른 서비스의 재배 ID이므로 연관관계 없이 생성 당시 값을 불변으로 보관한다.
    @Column(name = "cultivation_id", nullable = false, updatable = false)
    private Long cultivationId;

    // 재배별 일일 피드백의 멱등성 기준이므로 생성 후 변경하지 않는다.
    @Column(name = "feedback_date", nullable = false, updatable = false)
    private LocalDate feedbackDate;

    // 피드백 생성 당시 Vision 분석 반영 여부를 이력으로 보존한다.
    @Column(
            name = "has_vision_analysis",
            nullable = false,
            updatable = false
    )
    private boolean hasVisionAnalysis;

    // 생성된 일일 피드백 원문을 사후 변경 없이 보존한다.
    @Column(
            name = "content",
            nullable = false,
            updatable = false,
            columnDefinition = "TEXT"
    )
    private String content;

    // 피드백 생성 근거를 재현할 수 있도록 당시 입력 문맥 전체를 JSONB로 보존한다.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "context_snapshot",
            nullable = false,
            updatable = false,
            columnDefinition = "jsonb"
    )
    private JsonNode contextSnapshot;

    // 실제 피드백이 생성된 최초 시각을 불변 이력으로 보존한다.
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public DailyFeedback(Long cultivationId, LocalDate feedbackDate, boolean hasVisionAnalysis, String content, JsonNode contextSnapshot) {
        Long validatedCultivationId = Objects.requireNonNull(cultivationId, "cultivationId는 필수이며 null일 수 없습니다.");

        if (validatedCultivationId <= 0) {
            throw new IllegalArgumentException("cultivationId는 0보다 커야 합니다.");
        }

        LocalDate validatedFeedbackDate = Objects.requireNonNull(feedbackDate, "feedbackDate는 필수이며 null일 수 없습니다.");

        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content는 null이거나 빈 문자열 또는 공백일 수 없습니다.");
        }

        JsonNode validatedContextSnapshot = Objects.requireNonNull(contextSnapshot, "contextSnapshot은 필수이며 null일 수 없습니다.");

        if (!validatedContextSnapshot.isObject()) {
            throw new IllegalArgumentException("contextSnapshot은 JSON object여야 합니다.");
        }

        this.cultivationId = validatedCultivationId;
        this.feedbackDate = validatedFeedbackDate;
        this.hasVisionAnalysis = hasVisionAnalysis;
        this.content = content;
        this.contextSnapshot = validatedContextSnapshot.deepCopy();
        this.createdAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }

    /**
     * 피드백 생성 당시 저장된 Context Snapshot의 복사본을 반환합니다.
     *
     * <p>{@link JsonNode}은 변경 가능한 객체이므로 내부 필드를 직접
     * 반환하지 않습니다. 호출자가 반환받은 JSON을 수정하더라도
     * 엔티티 내부 Snapshot에는 영향을 주지 않습니다.</p>
     *
     * <p>JPA 기본 생성자로 생성된 직후처럼 필드가 아직 초기화되지 않은
     * 상태에서는 null을 반환합니다.</p>
     *
     * <p>{@link Id}가 필드에 선언되어 이 엔티티는 JPA field access
     * 방식을 사용합니다. 따라서 이 getter를 명시적으로 구현해도
     * 영속성 필드 매핑 방식은 변경되지 않습니다.</p>
     *
     * @return Context Snapshot의 방어적 복사본 또는 미초기화 상태이면 null
     */
    public JsonNode getContextSnapshot() {
        return contextSnapshot == null ? null : contextSnapshot.deepCopy();
    }
}
