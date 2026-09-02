package site.yesaido.ai_server.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.yesaido.ai_server.dto.client.sensor.EnvironmentComplianceResponse;
import site.yesaido.ai_server.dto.client.sensor.SensorTypeAverageListResponse;
import site.yesaido.ai_server.dto.client.sensor.SensorTypeAverageResponse;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SensorAverageClientDtoTest {

    @Test
    @DisplayName("EnvironmentComplianceResponse 생성 및 getter 검증")
    void environmentComplianceResponse() {
        EnvironmentComplianceResponse response = new EnvironmentComplianceResponse(
                BigDecimal.valueOf(90.0), BigDecimal.valueOf(80.0),
                BigDecimal.valueOf(85.0), BigDecimal.valueOf(95.0)
        );

        assertThat(response.temperatureCompliance()).isEqualTo(BigDecimal.valueOf(90.0));
        assertThat(response.humidityCompliance()).isEqualTo(BigDecimal.valueOf(80.0));
        assertThat(response.co2Compliance()).isEqualTo(BigDecimal.valueOf(85.0));
        assertThat(response.lightCompliance()).isEqualTo(BigDecimal.valueOf(95.0));
    }

    @Test
    @DisplayName("SensorTypeAverageResponse 및 ListResponse 생성 검증")
    void sensorTypeAverageResponse() {
        SensorTypeAverageResponse avg = new SensorTypeAverageResponse(
                1L, "TEMPERATURE", "°C", 23.5
        );

        assertThat(avg.cultivationId()).isEqualTo(1L);
        assertThat(avg.sensorType()).isEqualTo("TEMPERATURE");
        assertThat(avg.unit()).isEqualTo("°C");
        assertThat(avg.averageValue()).isEqualTo(23.5);

        SensorTypeAverageListResponse list = new SensorTypeAverageListResponse(List.of(avg));
        // 💡 실제 필드명: sensorTypeAverages()
        assertThat(list.sensorTypeAverages()).hasSize(1);
    }
}
