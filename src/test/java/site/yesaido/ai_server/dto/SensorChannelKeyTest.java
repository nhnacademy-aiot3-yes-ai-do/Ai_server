package site.yesaido.ai_server.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.yesaido.ai_server.dto.daily_feedback.SensorChannelKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SensorChannelKeyTest {

    @Test
    @DisplayName("SensorChannelKey 생성 및 필드 일치 검증")
    void create_success() {
        SensorChannelKey key = new SensorChannelKey(1L, "EUI-01", "TEMPERATURE", "°C");

        assertThat(key.cultivationId()).isEqualTo(1L);
        assertThat(key.deviceEui()).isEqualTo("EUI-01");
        assertThat(key.sensorType()).isEqualTo("TEMPERATURE");
        assertThat(key.unit()).isEqualTo("°C");
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    @DisplayName("SensorChannelKey 유효성 검증 실패 시 IllegalArgumentException")
    void create_invalid() {
        assertThatThrownBy(() -> new SensorChannelKey(null, "EUI-01", "TEMPERATURE", "°C"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SensorChannelKey(1L, "", "TEMPERATURE", "°C"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SensorChannelKey(1L, "EUI-01", null, "°C"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SensorChannelKey(1L, "EUI-01", "TEMPERATURE", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
