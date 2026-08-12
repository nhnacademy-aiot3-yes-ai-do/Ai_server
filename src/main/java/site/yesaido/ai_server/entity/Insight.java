package site.yesaido.ai_server.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "insight",
        uniqueConstraints = { @UniqueConstraint(name = "uk_insight_cultivation", columnNames = {"cultivation_id"})
        },
        indexes = {
                @Index(name = "idx_insight_mushroom_temp", columnList = "mushroom_id, avg_temperature")
        })
@Getter
@NoArgsConstructor
public class Insight {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cultivation_id", nullable = false)
    private Long cultivationId; // 재배 ID

    @Column(name = "mushroom_id", nullable = false)
    private Long mushroomId; // 버섯 참조 ID

    @Column(name = "avg_temperature", nullable = false, precision = 5, scale = 2)
    private BigDecimal avgTemperature;

    @Column(name = "avg_humidity", nullable = false, precision = 5, scale = 2)
    private BigDecimal avgHumidity;

    @Column(name = "avg_co2", nullable = false, precision = 8, scale = 2)
    private BigDecimal avgCo2;

    @Column(name = "avg_light", nullable = false, precision = 6, scale = 2)
    private BigDecimal avgLight;

    @Column(name = "harvest_weight_grams", nullable = false, precision = 6, scale = 2)
    private BigDecimal harvestWeightGrams; // 수확량

    @Column(name = "growth_score")
    private Integer growthScore; // 환경 점수

    @Column(columnDefinition = "TEXT", nullable = false)
    private String summary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Insight(Long cultivationId, Long mushroomId, BigDecimal avgTemperature, BigDecimal avgHumidity,
                   BigDecimal avgCo2, BigDecimal avgLight, BigDecimal harvestWeightGrams, Integer growthScore, String summary) {
        this.cultivationId = cultivationId;
        this.mushroomId = mushroomId;
        this.avgTemperature = avgTemperature;
        this.avgHumidity = avgHumidity;
        this.avgCo2 = avgCo2;
        this.avgLight = avgLight;
        this.harvestWeightGrams = harvestWeightGrams;
        this.growthScore = growthScore;
        this.summary = summary;
        this.createdAt = LocalDateTime.now(ZoneId.of("Asia/Seoul")); // 수확 완료 시점 시각 자동 입력
    }
}
