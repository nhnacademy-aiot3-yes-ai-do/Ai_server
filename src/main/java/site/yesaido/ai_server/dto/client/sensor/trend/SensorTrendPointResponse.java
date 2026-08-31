package site.yesaido.ai_server.dto.client.sensor.trend;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Cultivation Server가 반환하는 센서 추이 응답의 측정점 DTO입니다.
 *
 * <p>이 값은 원시 센서 측정값이 아니라 최근 24시간 데이터를
 * 15분 구간별 평균으로 집계한 값입니다. 데이터가 없는 빈 구간은
 * 응답에 포함되지 않으므로 측정점이 반드시 15분 간격으로
 * 연속된다고 가정하면 안 됩니다.</p>
 *
 * <p>{@code measuredAt}은 시간대와 무관한 절대 시각인 {@link Instant}로
 * 보존하며, {@code value}는 외부 서비스가 전달한 수치 정밀도를 유지하기
 * 위해 {@link BigDecimal}로 보존합니다.</p>
 *
 * @param measuredAt 15분 평균 측정 구간의 절대 시각
 * @param value 해당 구간의 평균 센서 측정값
 */
public record SensorTrendPointResponse(
        Instant measuredAt,
        BigDecimal value
) {

    public SensorTrendPointResponse {
        if (measuredAt == null) {
            throw new IllegalArgumentException("measuredAt은 null일 수 없습니다.");
        }

        if (value == null) {
            throw new IllegalArgumentException("value는 null일 수 없습니다.");
        }
    }
}
