package site.yesaido.ai_server.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.yesaido.ai_server.dto.client.sensor.snapshot.DataGeneratorSensorResponse;
import site.yesaido.ai_server.dto.client.sensor.snapshot.DataGeneratorSensorTypeResponse;
import site.yesaido.ai_server.dto.client.sensor.snapshot.DataGeneratorSnapshotResponse;
import site.yesaido.ai_server.dto.client.sensor.snapshot.DataGeneratorThresholdResponse;
import site.yesaido.ai_server.dto.daily_feedback.SensorChannelKey;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DailyFeedbackTargetResolverTest {

    private final DailyFeedbackTargetResolver resolver = new DailyFeedbackTargetResolver();

    private DataGeneratorSnapshotResponse createSampleSnapshot() {
        DataGeneratorSensorTypeResponse tempType = new DataGeneratorSensorTypeResponse("TEMPERATURE", "°C");
        DataGeneratorSensorTypeResponse humType = new DataGeneratorSensorTypeResponse("HUMIDITY", "%");

        DataGeneratorSensorResponse sensor1 = new DataGeneratorSensorResponse(
                1L, "EUI-001", "온습도 센서", "1구역", "중앙", "MOD-1", List.of(tempType, humType)
        );
        DataGeneratorSensorResponse sensor2 = new DataGeneratorSensorResponse(
                2L, "EUI-002", "온도 센서", "2구역", "입구", "MOD-2", List.of(tempType)
        );

        DataGeneratorThresholdResponse threshold1 = new DataGeneratorThresholdResponse(
                1L, "TEMPERATURE", "°C", new BigDecimal("15.0"), new BigDecimal("25.0")
        );
        DataGeneratorThresholdResponse threshold2 = new DataGeneratorThresholdResponse(
                3L, "HUMIDITY", "%", new BigDecimal("70.0"), new BigDecimal("90.0")
        );

        return new DataGeneratorSnapshotResponse(
                OffsetDateTime.now(ZoneOffset.ofHours(9)),
                List.of(sensor1, sensor2),
                List.of(threshold1, threshold2)
        );
    }

    @Test
    @DisplayName("resolveCultivationIds: 센서와 임계값에 포함된 모든 경작지 ID의 합집합 반환")
    void resolveCultivationIds_success() {
        DataGeneratorSnapshotResponse snapshot = createSampleSnapshot();

        List<Long> cultivationIds = resolver.resolveCultivationIds(snapshot);

        assertThat(cultivationIds).containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("resolveChannels: 특정 경작지의 센서 채널 키 목록 추출 및 정렬")
    void resolveChannels_success() {
        DataGeneratorSnapshotResponse snapshot = createSampleSnapshot();

        List<SensorChannelKey> channels = resolver.resolveChannels(snapshot, 1L);

        assertThat(channels).hasSize(2);
        assertThat(channels.getFirst().cultivationId()).isEqualTo(1L);
        assertThat(channels.getFirst().deviceEui()).isEqualTo("EUI-001");
    }

    @Test
    @DisplayName("resolveCurrentThresholds: 특정 경작지의 현재 임계값 목록 추출")
    void resolveCurrentThresholds_success() {
        DataGeneratorSnapshotResponse snapshot = createSampleSnapshot();

        List<DataGeneratorThresholdResponse> thresholds = resolver.resolveCurrentThresholds(snapshot, 1L);

        assertThat(thresholds).hasSize(1);
        assertThat(thresholds.getFirst().sensorType()).isEqualTo("TEMPERATURE");
        assertThat(thresholds.getFirst().minValue()).isEqualTo(new BigDecimal("15.0"));
    }

    @Test
    @DisplayName("예외: null 파라미터 전달 시 IllegalArgumentException")
    void invalidParams() {
        DataGeneratorSnapshotResponse snapshot = createSampleSnapshot();

        assertThatThrownBy(() -> resolver.resolveCultivationIds(null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> resolver.resolveChannels(null, 1L))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> resolver.resolveChannels(snapshot, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> resolver.resolveCurrentThresholds(null, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
