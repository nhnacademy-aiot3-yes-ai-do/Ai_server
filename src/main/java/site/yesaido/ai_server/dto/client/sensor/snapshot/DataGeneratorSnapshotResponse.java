package site.yesaido.ai_server.dto.client.sensor.snapshot;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Cultivation Server가 조회 시점에 생성한 Data Generator 최상위 snapshot입니다.
 *
 * <p>이 snapshot은 과거 특정 날짜의 상태가 아니라 조회 시점에 활성 상태인
 * 경작지의 센서 장치·측정 채널과 현재 임계값을 나타냅니다.</p>
 *
 * <p>현재 계약에서 일일 피드백 대상 경작지 ID는 {@code sensors}와
 * {@code thresholds}에 포함된 {@code cultivationId}의 합집합으로 찾습니다.
 * 센서 장치 없이 임계값만 있는 경작지와 임계값 없이 센서 채널만 있는
 * 경작지를 모두 DTO 계약상 허용합니다.</p>
 *
 * <p>센서 채널에 대응하는 임계값이 없다면 역직렬화 자체는 성공하며,
 * 이후 일일 피드백 조립 계층에서 해당 채널의 참고 유지율을
 * {@code UNAVAILABLE}로 처리해야 합니다.</p>
 *
 * @param snapshotAt snapshot을 생성한 절대 시각
 * @param sensors 활성 경작지에 등록된 센서 장치와 측정 채널 목록
 * @param thresholds 활성 경작지에 설정된 센서 타입별 현재 임계값 목록
 */
public record DataGeneratorSnapshotResponse(
        OffsetDateTime snapshotAt,
        List<DataGeneratorSensorResponse> sensors,
        List<DataGeneratorThresholdResponse> thresholds
) {

    public DataGeneratorSnapshotResponse {
        if (snapshotAt == null) {
            throw new IllegalArgumentException("snapshotAt은 필수이며 null일 수 없습니다.");
        }

        if (!ZoneOffset.ofHours(9).equals(snapshotAt.getOffset())) {
            throw new IllegalArgumentException("snapshotAt의 offset은 +09:00이어야 합니다.");
        }

        if (sensors == null) {
            throw new IllegalArgumentException("sensors는 필수이며 null일 수 없습니다.");
        }

        if (thresholds == null) {
            throw new IllegalArgumentException("thresholds는 필수이며 null일 수 없습니다.");
        }

        Set<String> deviceEuis = new HashSet<>();

        for (DataGeneratorSensorResponse sensor : sensors) {
            if (sensor == null) {
                throw new IllegalArgumentException("sensors에는 null 요소가 포함될 수 없습니다.");
            }

            if (!deviceEuis.add(sensor.deviceEui())) {
                throw new IllegalArgumentException("sensors에는 동일한 deviceEui가 중복될 수 없습니다.");
            }
        }

        Set<ThresholdKey> thresholdKeys = new HashSet<>();

        for (DataGeneratorThresholdResponse threshold : thresholds) {
            if (threshold == null) {
                throw new IllegalArgumentException("thresholds에는 null 요소가 포함될 수 없습니다.");
            }

            ThresholdKey thresholdKey = new ThresholdKey(threshold.cultivationId(), threshold.sensorType(), threshold.unit());

            if (!thresholdKeys.add(thresholdKey)) {
                throw new IllegalArgumentException("thresholds에는 동일한 경작지와 센서 채널 조합이 중복될 수 없습니다.");
            }
        }

        sensors = List.copyOf(sensors);
        thresholds = List.copyOf(thresholds);
    }

    private record ThresholdKey(
            Long cultivationId,
            String sensorType,
            String unit
    ) {
    }
}
