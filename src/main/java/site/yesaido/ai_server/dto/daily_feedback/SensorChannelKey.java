package site.yesaido.ai_server.dto.daily_feedback;

/**
 * 일일 피드백 통계에서 하나의 원본 센서 채널을 식별하는 불변 키입니다.
 *
 * <p>동일한 {@code deviceEui}가 서로 다른 경작지에 존재할 가능성까지
 * 차단하기 위해 {@code cultivationId}를 식별 기준에 포함합니다.
 * 하나의 장치가 여러 {@code sensorType}을 측정할 수 있으므로 센서 타입도
 * 필요하며, 같은 센서 타입이라도 단위가 다를 수 있으므로
 * {@code unit}까지 포함합니다.</p>
 *
 * <p>채널은 반드시 다음 네 필드 전체의 조합으로 식별합니다.</p>
 *
 * <p>{@code cultivationId + deviceEui + sensorType + unit}</p>
 *
 * <p>각 채널의 최솟값, 평균값, 최댓값과 15분 평균 집계점 수는
 * 이 네 필드 전체를 기준으로 독립적으로 계산해야 합니다.</p>
 *
 * <p>Rule Engine에서 EUI만을 기준으로 잘못 upsert할 때 발생할 수 있는
 * 것과 같은 센서 채널 혼선을 AI 일일 통계에서 방지하기 위한 키입니다.
 * record가 제공하는 값 기반 동등성을 그대로 사용하여 Map 키와
 * 중복 검사 키로 활용합니다.</p>
 *
 * @param cultivationId 센서 채널이 속한 경작지 ID
 * @param deviceEui 센서 장치 식별자
 * @param sensorType 기본 또는 커스텀 센서 타입의 원본 식별값
 * @param unit 센서 측정 단위의 원본 값
 */
public record SensorChannelKey(
        Long cultivationId,
        String deviceEui,
        String sensorType,
        String unit
) {

    public SensorChannelKey {
        if (cultivationId == null || cultivationId <= 0) {
            throw new IllegalArgumentException("cultivationId는 null이 아니며 0보다 커야 합니다.");
        }

        if (deviceEui == null || deviceEui.isBlank()) {
            throw new IllegalArgumentException("deviceEui는 null 또는 공백일 수 없습니다.");
        }

        if (sensorType == null || sensorType.isBlank()) {
            throw new IllegalArgumentException("sensorType은 null 또는 공백일 수 없습니다.");
        }

        if (unit == null || unit.isBlank()) {
            throw new IllegalArgumentException("unit은 null 또는 공백일 수 없습니다.");
        }
    }
}
