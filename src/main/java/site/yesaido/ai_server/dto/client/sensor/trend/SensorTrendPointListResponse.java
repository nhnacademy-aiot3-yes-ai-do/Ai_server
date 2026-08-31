package site.yesaido.ai_server.dto.client.sensor.trend;

import java.util.List;

/**
 * 하나의 {@code cultivationId + deviceEui + sensorType} 조합에 대한
 * Cultivation Server의 센서 추이 조회 응답입니다.
 *
 * <p>{@code unit}은 서버가 추이 데이터에서 확인한 원본 단위입니다.
 * 센서 타입과 단위는 커스텀 값을 포함할 수 있으므로 별도로 변환하거나
 * 정규화하지 않습니다.</p>
 *
 * <p>{@code responses}의 각 요소는 최근 24시간 원시 센서 데이터를
 * 15분 구간별 평균으로 집계한 측정점입니다. 따라서
 * {@code responses.size()}는 15분 평균 집계점의 개수이며,
 * 원시 센서 측정 횟수를 의미하지 않습니다.</p>
 *
 * <p>데이터가 없는 구간은 생략되므로 측정점 약 96개가 항상
 * 반환된다고 가정하면 안 됩니다. 빈 목록은 향후 서버가 무데이터 응답을
 * 정상적으로 반환하는 경우를 표현하기 위해 허용합니다.</p>
 *
 * <p>측정점 목록은 방어적으로 복사하여 응답 객체 생성 후
 * 외부에서 변경할 수 없도록 보관합니다.</p>
 *
 * @param cultivationId 추이 데이터를 조회한 경작지 ID
 * @param deviceEui 센서 장치 식별자
 * @param sensorType 조회한 센서 타입의 원본 식별값
 * @param unit 서버가 추이 데이터에서 확인한 원본 단위
 * @param responses 최근 24시간 데이터의 15분 구간별 평균 측정점 목록
 */
public record SensorTrendPointListResponse(
        Long cultivationId,
        String deviceEui,
        String sensorType,
        String unit,
        List<SensorTrendPointResponse> responses
) {

    public SensorTrendPointListResponse {
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

        if (responses == null) {
            throw new IllegalArgumentException("responses는 null일 수 없습니다.");
        }

        for (SensorTrendPointResponse response : responses) {
            if (response == null) {
                throw new IllegalArgumentException("responses에는 null 요소가 포함될 수 없습니다.");
            }
        }

        responses = List.copyOf(responses);
    }
}
