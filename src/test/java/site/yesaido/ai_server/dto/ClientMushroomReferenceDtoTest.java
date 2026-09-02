package site.yesaido.ai_server.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.yesaido.ai_server.dto.client.mushroom_reference.MushroomReferenceInfoResponse;
import site.yesaido.ai_server.dto.client.mushroom_reference.MushroomReferenceThresholdInfoResponse;
import site.yesaido.ai_server.dto.client.mushroom_reference.SensorTypeInfoResponse;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClientMushroomReferenceDtoTest {

    @Test
    @DisplayName("버섯 참조정보 계층 DTO 생성 및 getter 검증")
    void mushroomReferenceDto() {
        SensorTypeInfoResponse sensorType = new SensorTypeInfoResponse(1L, "TEMPERATURE", "°C");
        assertThat(sensorType.id()).isEqualTo(1L);
        assertThat(sensorType.type()).isEqualTo("TEMPERATURE");
        assertThat(sensorType.valueUnit()).isEqualTo("°C");

        MushroomReferenceThresholdInfoResponse threshold = new MushroomReferenceThresholdInfoResponse(
                10L, sensorType, "GROWTH", BigDecimal.valueOf(18), BigDecimal.valueOf(24)
        );
        assertThat(threshold.id()).isEqualTo(10L);
        assertThat(threshold.sensorType()).isEqualTo(sensorType);
        assertThat(threshold.thresholdType()).isEqualTo("GROWTH");
        assertThat(threshold.thresholdMin()).isEqualTo(BigDecimal.valueOf(18));
        assertThat(threshold.thresholdMax()).isEqualTo(BigDecimal.valueOf(24));

        MushroomReferenceInfoResponse info = new MushroomReferenceInfoResponse(
                100L, "표고버섯", "Shiitake", "Lentinula edodes", List.of(threshold)
        );
        assertThat(info.id()).isEqualTo(100L);
        assertThat(info.mushroomNameKo()).isEqualTo("표고버섯");
        assertThat(info.mushroomNameEn()).isEqualTo("Shiitake");
        assertThat(info.mushroomScientificName()).isEqualTo("Lentinula edodes");
        assertThat(info.thresholdInfoResponses()).hasSize(1);
    }
}
