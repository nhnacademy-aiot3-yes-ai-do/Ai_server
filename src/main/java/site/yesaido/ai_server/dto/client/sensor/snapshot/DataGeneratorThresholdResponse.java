package site.yesaido.ai_server.dto.client.sensor.snapshot;

import java.math.BigDecimal;

/**
 * Cultivation Server의 Data Generator snapshot에 포함된 현재 센서 임계값입니다.
 *
 * <p>임계값은 다음 값의 조합으로 식별합니다.</p>
 *
 * <p>{@code cultivationId + sensorType + unit}</p>
 *
 * <p>EnvironmentSetting은 장치 EUI별 설정이 아니라 경작지의 센서 타입별
 * 설정입니다. 따라서 같은 경작지에서 동일한 {@code sensorType + unit}을
 * 측정하는 여러 EUI에 이 임계값이 공통으로 적용됩니다.</p>
 *
 * <p>이 값은 과거 임계값 이력이 아니라 snapshot을 조회한 시점의 현재
 * 설정값입니다. 일일 피드백에서 해당 날짜 전체에 계속 적용된 임계값으로
 * 표현하면 안 됩니다.</p>
 *
 * @param cultivationId 임계값이 설정된 경작지 ID
 * @param sensorType Cultivation 및 InfluxDB에서 사용하는 센서 채널 유형
 * @param unit 해당 센서 채널 측정값의 단위
 * @param minValue snapshot 조회 시점에 설정된 최솟값
 * @param maxValue snapshot 조회 시점에 설정된 최댓값
 */
public record DataGeneratorThresholdResponse(
        Long cultivationId,
        String sensorType,
        String unit,
        BigDecimal minValue,
        BigDecimal maxValue
) {

    public DataGeneratorThresholdResponse {
        if (cultivationId == null || cultivationId <= 0) {
            throw new IllegalArgumentException("cultivationId는 필수이며 0보다 커야 합니다.");
        }

        if (sensorType == null || sensorType.isBlank()) {
            throw new IllegalArgumentException("sensorType은 null이거나 blank일 수 없습니다.");
        }

        if (unit == null || unit.isBlank()) {
            throw new IllegalArgumentException("unit은 null이거나 blank일 수 없습니다.");
        }

        if (minValue == null) {
            throw new IllegalArgumentException("minValue는 필수이며 null일 수 없습니다.");
        }

        if (maxValue == null) {
            throw new IllegalArgumentException("maxValue는 필수이며 null일 수 없습니다.");
        }

        if (minValue.compareTo(maxValue) > 0) {
            throw new IllegalArgumentException("minValue는 maxValue보다 클 수 없습니다.");
        }
    }
}
