package site.yesaido.ai_server.dto.client.sensor.snapshot;

import java.util.HashSet;
import java.util.List;

/**
 * Cultivation Server의 Data Generator snapshot에 포함된 센서 장치 정보입니다.
 *
 * <p>장치의 각 측정 채널은 다음 값의 조합으로 식별합니다.</p>
 *
 * <p>{@code cultivationId + deviceEui + sensorType + unit}</p>
 *
 * <p>{@code deviceName}, {@code location}, {@code locationDetail},
 * {@code deviceModel}은 설명용 메타데이터이므로 null 또는 blank일 수 있으며,
 * Cultivation Server가 전달한 값을 임의로 보정하지 않습니다.</p>
 *
 * <p>채널 연결이 없는 센서 장치도 snapshot에 포함될 수 있으므로
 * {@code sensorTypes}의 빈 목록은 정상적인 값으로 허용합니다.</p>
 *
 * @param cultivationId 센서 장치가 등록된 경작지 ID
 * @param deviceEui 센서 장치를 식별하는 EUI
 * @param deviceName 센서 장치 이름
 * @param location 센서 장치가 설치된 위치
 * @param locationDetail 센서 장치 설치 위치의 상세 설명
 * @param deviceModel 센서 장치 모델명
 * @param sensorTypes 해당 장치가 측정하는 센서 채널 목록
 */
public record DataGeneratorSensorResponse(
        Long cultivationId,
        String deviceEui,
        String deviceName,
        String location,
        String locationDetail,
        String deviceModel,
        List<DataGeneratorSensorTypeResponse> sensorTypes
) {

    public DataGeneratorSensorResponse {
        if (cultivationId == null || cultivationId <= 0) {
            throw new IllegalArgumentException("cultivationId는 필수이며 0보다 커야 합니다.");
        }

        if (deviceEui == null || deviceEui.isBlank()) {
            throw new IllegalArgumentException("deviceEui는 null이거나 blank일 수 없습니다.");
        }

        if (sensorTypes == null) {
            throw new IllegalArgumentException("sensorTypes는 필수이며 null일 수 없습니다.");
        }

        HashSet<DataGeneratorSensorTypeResponse> uniqueSensorTypes = new HashSet<>();

        for (DataGeneratorSensorTypeResponse sensorType : sensorTypes) {
            if (sensorType == null) {
                throw new IllegalArgumentException("sensorTypes에는 null 요소가 포함될 수 없습니다.");
            }

            if (!uniqueSensorTypes.add(sensorType)) {
                throw new IllegalArgumentException("sensorTypes에는 동일한 sensorType과 unit 조합이 중복될 수 없습니다.");
            }
        }

        sensorTypes = List.copyOf(sensorTypes);
    }
}
