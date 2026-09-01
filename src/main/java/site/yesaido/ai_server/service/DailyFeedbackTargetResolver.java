package site.yesaido.ai_server.service;

import org.springframework.stereotype.Component;
import site.yesaido.ai_server.dto.client.sensor.snapshot.DataGeneratorSensorResponse;
import site.yesaido.ai_server.dto.client.sensor.snapshot.DataGeneratorSensorTypeResponse;
import site.yesaido.ai_server.dto.client.sensor.snapshot.DataGeneratorSnapshotResponse;
import site.yesaido.ai_server.dto.client.sensor.snapshot.DataGeneratorThresholdResponse;
import site.yesaido.ai_server.dto.daily_feedback.SensorChannelKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Cultivation Server의 Data Generator Snapshot을 해석하여
 * 일일 피드백 대상 경작지와 센서 채널을 결정합니다.
 *
 * <p>이 클래스는 외부 시스템을 조회하지 않고 이미 전달받은 Snapshot을
 * 순수하게 해석하는 역할만 담당합니다. Snapshot은 피드백 대상 날짜의
 * 과거 상태가 아니라 배치 실행 시점의 활성 센서와 현재 임계값입니다.</p>
 *
 * <p>피드백 대상 경작지 ID는 센서 목록과 임계값 목록에 포함된
 * {@code cultivationId}의 합집합입니다. 따라서 임계값만 있거나
 * 센서만 있는 경작지도 대상에서 제외하지 않습니다. 임계값만 존재하는
 * 경작지는 대상에는 포함되지만 센서 채널 목록은 비어 있을 수 있습니다.</p>
 *
 * <p>대상 ID와 채널을 정렬하는 이유는 실행할 때마다 동일한
 * {@code contextSnapshot} 구조와 LLM 입력 순서를 만들기 위해서입니다.</p>
 *
 * <p>현재 Cultivation Server는 활성 재배 전체에서 동일한 EUI의 중복을
 * 먼저 거부합니다. 그러나 AI 내부에서는 향후 서버 계약 변경과 데이터
 * 혼선에 대비하여 채널을 반드시
 * {@code cultivationId + deviceEui + sensorType + unit} 네 필드 전체로
 * 식별합니다.</p>
 */
@Component
public class DailyFeedbackTargetResolver {

    /**
     * Snapshot의 센서와 임계값에 등장하는 모든 경작지 ID를 반환합니다.
     *
     * @param snapshot 실행 시점의 Data Generator Snapshot
     * @return 중복이 제거되고 오름차순으로 정렬된 수정 불가능한 경작지 ID 목록
     * @throws IllegalArgumentException snapshot이 null인 경우
     */
    public List<Long> resolveCultivationIds(DataGeneratorSnapshotResponse snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot은 null일 수 없습니다.");
        }

        Set<Long> cultivationIds = new TreeSet<>();

        for (DataGeneratorSensorResponse sensor : snapshot.sensors()) {
            cultivationIds.add(sensor.cultivationId());
        }

        for (DataGeneratorThresholdResponse threshold : snapshot.thresholds()) {
            cultivationIds.add(threshold.cultivationId());
        }

        return List.copyOf(cultivationIds);
    }

    /**
     * Snapshot에서 특정 경작지에 등록된 모든 원본 센서 채널을 반환합니다.
     *
     * <p>임계값은 장치 EUI를 포함하지 않으므로 센서 채널로 변환하지 않습니다.
     * 채널은 센서 장치와 해당 장치의 센서 타입 목록으로만 생성합니다.</p>
     *
     * @param snapshot 실행 시점의 Data Generator Snapshot
     * @param cultivationId 채널을 찾을 경작지 ID
     * @return 장치 EUI, 센서 타입, 단위 순서로 정렬된 수정 불가능한 채널 목록
     * @throws IllegalArgumentException snapshot이 null이거나 cultivationId가 유효하지 않은 경우
     * @throws IllegalStateException 동일한 센서 채널 키가 중복된 경우
     */
    public List<SensorChannelKey> resolveChannels(DataGeneratorSnapshotResponse snapshot, Long cultivationId) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot은 null일 수 없습니다.");
        }

        if (cultivationId == null || cultivationId <= 0) {
            throw new IllegalArgumentException("cultivationId는 null이 아니며 0보다 커야 합니다.");
        }

        List<SensorChannelKey> channels = new ArrayList<>();
        Set<SensorChannelKey> uniqueChannels = new HashSet<>();

        for (DataGeneratorSensorResponse sensor : snapshot.sensors()) {
            if (!cultivationId.equals(sensor.cultivationId())) {
                continue;
            }

            for (DataGeneratorSensorTypeResponse sensorType : sensor.sensorTypes()) {
                SensorChannelKey channelKey = new SensorChannelKey(
                        sensor.cultivationId(),
                        sensor.deviceEui(),
                        sensorType.sensorType(),
                        sensorType.unit()
                );

                if (!uniqueChannels.add(channelKey)) {
                    throw new IllegalStateException(" Snapshot에 동일한 센서 채널이 중복되었습니다: cultivationId=%s, channelKey=%s "
                            .formatted(cultivationId, channelKey).strip());
                }

                channels.add(channelKey);
            }
        }

        channels.sort(Comparator.comparing(SensorChannelKey::deviceEui)
                .thenComparing(SensorChannelKey::sensorType)
                .thenComparing(SensorChannelKey::unit)
        );

        return List.copyOf(channels);
    }

    /**
     * Snapshot에서 특정 경작지의 현재 임계값만 추출합니다.
     *
     * <p>반환값은 과거 {@code feedbackDate}에 적용됐던 임계값 이력이 아니라
     * {@link DataGeneratorSnapshotResponse#snapshotAt()} 시점의 현재
     * 설정입니다.</p>
     *
     * <p>Context 조립 계층이 전체 Snapshot을 직접 순회하지 않도록
     * Snapshot 해석 책임을 이 Resolver에 모읍니다. 빈 목록은 오류가 아니라
     * 해당 경작지에 현재 설정된 임계값이 없다는 정상 상태입니다.</p>
     *
     * @param snapshot 실행 시점의 Data Generator Snapshot
     * @param cultivationId 현재 임계값을 찾을 경작지 ID
     * @return 센서 타입과 단위 순서로 정렬된 수정 불가능한 현재 임계값 목록
     * @throws IllegalArgumentException snapshot이 null이거나 cultivationId가 유효하지 않은 경우
     * @throws IllegalStateException Snapshot에 null 임계값이 있거나 반환 대상의 채널 조합이 중복된 경우
     */
    public List<DataGeneratorThresholdResponse> resolveCurrentThresholds(
            DataGeneratorSnapshotResponse snapshot,
            Long cultivationId
    ) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot은 null일 수 없습니다.");
        }

        if (cultivationId == null || cultivationId <= 0) {
            throw new IllegalArgumentException("cultivationId는 null이 아니며 0보다 커야 합니다.");
        }

        List<DataGeneratorThresholdResponse> thresholds = new ArrayList<>();
        Set<CurrentThresholdKey> uniqueThresholds = new HashSet<>();

        for (DataGeneratorThresholdResponse threshold : snapshot.thresholds()) {
            if (threshold == null) {
                throw new IllegalStateException("Snapshot의 thresholds에 null 요소가 포함되어 있습니다.");
            }

            if (!cultivationId.equals(threshold.cultivationId())) {
                continue;
            }

            CurrentThresholdKey thresholdKey = new CurrentThresholdKey(threshold.sensorType(), threshold.unit());

            if (!uniqueThresholds.add(thresholdKey)) {
                throw new IllegalStateException("Snapshot에 동일한 현재 임계값 채널이 중복되었습니다: cultivationId=%s, sensorType=%s, unit=%s"
                        .formatted(cultivationId, threshold.sensorType(), threshold.unit()));
            }

            thresholds.add(threshold);
        }

        thresholds.sort(
                Comparator
                        .comparing(DataGeneratorThresholdResponse::sensorType)
                        .thenComparing(DataGeneratorThresholdResponse::unit)
        );

        return List.copyOf(thresholds);
    }

    private record CurrentThresholdKey(
            String sensorType,
            String unit
    ){
    }
}
