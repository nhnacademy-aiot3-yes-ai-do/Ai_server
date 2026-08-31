package site.yesaido.ai_server.dto.client.sensor.snapshot;

/**
 * Cultivation Server의 Data Generator snapshot 응답에 포함된 센서 측정 채널입니다.
 *
 * <p>센서 측정값의 채널은 다음 값의 조합으로 식별합니다.</p>
 *
 * <p>{@code cultivationId + deviceEui + sensorType + unit}</p>
 *
 * <p>{@code sensorType}에는 기본 센서 유형뿐 아니라 사용자가 등록한
 * 커스텀 센서 유형도 전달될 수 있으므로 문자열 원본을 그대로 보존합니다.</p>
 *
 * @param sensorType Cultivation 및 InfluxDB에서 사용하는 센서 채널 유형
 * @param unit 해당 센서 채널 측정값의 단위
 */
public record DataGeneratorSensorTypeResponse(
        String sensorType,
        String unit
) {

    public DataGeneratorSensorTypeResponse {
        if (sensorType == null || sensorType.isBlank()) {
            throw new IllegalArgumentException("sensorType은 null이거나 blank일 수 없습니다.");
        }

        if (unit == null || unit.isBlank()) {
            throw new IllegalArgumentException("unit은 null이거나 blank일 수 없습니다.");
        }
    }
}
