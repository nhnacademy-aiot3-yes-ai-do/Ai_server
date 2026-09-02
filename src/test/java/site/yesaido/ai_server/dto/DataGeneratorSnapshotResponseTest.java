package site.yesaido.ai_server.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.yesaido.ai_server.dto.client.sensor.snapshot.DataGeneratorSensorResponse;
import site.yesaido.ai_server.dto.client.sensor.snapshot.DataGeneratorSnapshotResponse;
import site.yesaido.ai_server.dto.client.sensor.snapshot.DataGeneratorThresholdResponse;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("ConstantConditions")
class DataGeneratorSnapshotResponseTest {

    private static final ZoneOffset SEOUL_OFFSET = ZoneOffset.ofHours(9);

    @Test
    @DisplayName("DataGeneratorSnapshotResponse 정상 생성 검증")
    void create_success() {
        OffsetDateTime now = OffsetDateTime.now(SEOUL_OFFSET);
        DataGeneratorSensorResponse sensor = new DataGeneratorSensorResponse(
                1L, "EUI-01", "온도센서", "구역A", "상단", "MODEL-1", List.of()
        );
        DataGeneratorThresholdResponse threshold = new DataGeneratorThresholdResponse(
                1L, "TEMPERATURE", "°C", BigDecimal.valueOf(18), BigDecimal.valueOf(25)
        );

        DataGeneratorSnapshotResponse response = new DataGeneratorSnapshotResponse(
                now, List.of(sensor), List.of(threshold)
        );

        assertThat(response.snapshotAt()).isEqualTo(now);
        assertThat(response.sensors()).hasSize(1);
        assertThat(response.thresholds()).hasSize(1);
    }

    @Test
    @DisplayName("유효성 검증 실패 케이스들 (null, 타임존, 중복 EUI/임계값 등)")
    void create_validationFailures() {
        OffsetDateTime seoulNow = OffsetDateTime.now(SEOUL_OFFSET);
        OffsetDateTime utcNow = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime nullTime = null;

        DataGeneratorSensorResponse sensor1 = new DataGeneratorSensorResponse(1L, "EUI-01", "이름", "위치", "상세", "모델", List.of());
        DataGeneratorSensorResponse sensor2 = new DataGeneratorSensorResponse(1L, "EUI-01", "이름2", "위치2", "상세2", "모델2", List.of());
        List<DataGeneratorSensorResponse> validSensors = List.of(sensor1);
        List<DataGeneratorSensorResponse> dupSensors = List.of(sensor1, sensor2);
        List<DataGeneratorSensorResponse> nullSensors = null;
        List<DataGeneratorSensorResponse> nullElemSensors = Collections.singletonList(null);

        DataGeneratorThresholdResponse th1 = new DataGeneratorThresholdResponse(1L, "TEMPERATURE", "°C", BigDecimal.valueOf(18), BigDecimal.valueOf(25));
        DataGeneratorThresholdResponse th2 = new DataGeneratorThresholdResponse(1L, "TEMPERATURE", "°C", BigDecimal.valueOf(20), BigDecimal.valueOf(28));
        List<DataGeneratorThresholdResponse> validThresholds = List.of(th1);
        List<DataGeneratorThresholdResponse> dupThresholds = List.of(th1, th2);
        List<DataGeneratorThresholdResponse> nullThresholds = null;
        List<DataGeneratorThresholdResponse> nullElemThresholds = Collections.singletonList(null);

        // 1. snapshotAt null
        assertThatThrownBy(() -> new DataGeneratorSnapshotResponse(nullTime, validSensors, validThresholds))
                .isInstanceOf(IllegalArgumentException.class);

        // 2. 타임존 불일치 (UTC)
        assertThatThrownBy(() -> new DataGeneratorSnapshotResponse(utcNow, validSensors, validThresholds))
                .isInstanceOf(IllegalArgumentException.class);

        // 3. sensors null
        assertThatThrownBy(() -> new DataGeneratorSnapshotResponse(seoulNow, nullSensors, validThresholds))
                .isInstanceOf(IllegalArgumentException.class);

        // 4. thresholds null
        assertThatThrownBy(() -> new DataGeneratorSnapshotResponse(seoulNow, validSensors, nullThresholds))
                .isInstanceOf(IllegalArgumentException.class);

        // 5. sensors null 요소 포함
        assertThatThrownBy(() -> new DataGeneratorSnapshotResponse(seoulNow, nullElemSensors, validThresholds))
                .isInstanceOf(IllegalArgumentException.class);

        // 6. sensors 중복 deviceEui
        assertThatThrownBy(() -> new DataGeneratorSnapshotResponse(seoulNow, dupSensors, validThresholds))
                .isInstanceOf(IllegalArgumentException.class);

        // 7. thresholds null 요소 포함
        assertThatThrownBy(() -> new DataGeneratorSnapshotResponse(seoulNow, validSensors, nullElemThresholds))
                .isInstanceOf(IllegalArgumentException.class);

        // 8. thresholds 중복 키
        assertThatThrownBy(() -> new DataGeneratorSnapshotResponse(seoulNow, validSensors, dupThresholds))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
