package site.yesaido.ai_server.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

@Entity
@Table(
        name = "growth_record",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_growth_record_cultivation_photo",
                        columnNames = "cultivation_photo_id"
                )
        },
        indexes = {
                @Index(
                        name = "idx_growth_record_cultivation_analyzed_at",
                        columnList = "cultivation_id, analyzed_at"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GrowthRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 다른 서비스의 재배 ID이므로 DB 외래키를 연결하지 않고 값만 보관한다.
    @Column(name = "cultivation_id", nullable = false, updatable = false)
    private Long cultivationId;

    // 사진 한 장당 분석 결과 하나만 저장하기 위한 멱등성 기준이다.
    @Column(name = "cultivation_photo_id", nullable = false, updatable = false)
    private Long cultivationPhotoId;

    // Vision 응답 전체를 가공하거나 유실하지 않고 JSONB로 보관한다.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "analysis_data",
            nullable = false,
            updatable = false,
            columnDefinition = "jsonb"
    )
    private JsonNode analysisData;

    @Column(name = "analyzed_at", nullable = false, updatable = false)
    private LocalDateTime analyzedAt;

    @Builder
    public GrowthRecord(
            Long cultivationId,
            Long cultivationPhotoId,
            JsonNode analysisData
    ) {
        this.cultivationId =
                Objects.requireNonNull(cultivationId, "cultivationId는 필수입니다.");
        this.cultivationPhotoId =
                Objects.requireNonNull(cultivationPhotoId, "cultivationPhotoId는 필수입니다.");
        this.analysisData =
                Objects.requireNonNull(analysisData, "analysisData는 필수입니다.").deepCopy();
        this.analyzedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }
}
