package site.yesaido.ai_server.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.yesaido.ai_server.dto.client.sensor.snapshot.DataGeneratorSensorTypeResponse;
import site.yesaido.ai_server.dto.client.sensor.snapshot.DataGeneratorThresholdResponse;
import site.yesaido.ai_server.dto.client.sensor.trend.SensorTrendPointListResponse;
import site.yesaido.ai_server.dto.client.sensor.trend.SensorTrendPointResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("ConstantConditions")
class ClientSensorDtoTest {

    @Test
    @DisplayName("DataGeneratorSensorTypeResponse 생성 및 유효성 검증")
    void sensorTypeResponse() {
        DataGeneratorSensorTypeResponse type = new DataGeneratorSensorTypeResponse("TEMPERATURE", "°C");
        assertThat(type.sensorType()).isEqualTo("TEMPERATURE");
        assertThat(type.unit()).isEqualTo("°C");

        assertThatThrownBy(() -> new DataGeneratorSensorTypeResponse(null, "°C"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DataGeneratorSensorTypeResponse("TEMP", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("DataGeneratorThresholdResponse 생성 및 min > max 예외 검증")
    void thresholdResponse() {
        BigDecimal min = BigDecimal.valueOf(15);
        BigDecimal max = BigDecimal.valueOf(25);
        DataGeneratorThresholdResponse threshold = new DataGeneratorThresholdResponse(
                1L, "TEMPERATURE", "°C", min, max
        );

        assertThat(threshold.cultivationId()).isEqualTo(1L);
        assertThat(threshold.minValue()).isEqualTo(min);
        assertThat(threshold.maxValue()).isEqualTo(max);

        // min이 max보다 큰 경우 예외 발생
        assertThatThrownBy(() -> new DataGeneratorThresholdResponse(1L, "TEMPERATURE", "°C", max, min))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DataGeneratorThresholdResponse(null, "TEMPERATURE", "°C", min, max))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("SensorTrendPointResponse 및 ListResponse 생성 검증")
    void trendPointResponse() {
        Instant now = Instant.now();
        BigDecimal value = BigDecimal.valueOf(23.5);
        SensorTrendPointResponse point = new SensorTrendPointResponse(now, value);

        assertThat(point.measuredAt()).isEqualTo(now);
        assertThat(point.value()).isEqualTo(value);

        SensorTrendPointListResponse listResponse = new SensorTrendPointListResponse(
                1L, "EUI-001", "TEMPERATURE", "°C", List.of(point)
        );

        assertThat(listResponse.cultivationId()).isEqualTo(1L);
        assertThat(listResponse.deviceEui()).isEqualTo("EUI-001");
        assertThat(listResponse.responses()).hasSize(1);
    }
}
