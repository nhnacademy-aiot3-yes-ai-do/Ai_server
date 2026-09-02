package site.yesaido.ai_server.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.yesaido.ai_server.dto.daily_feedback.SensorChannelKey;
import site.yesaido.ai_server.dto.daily_feedback.SensorChannelStatistics;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("ConstantConditions")
class SensorChannelStatisticsTest {

    private final SensorChannelKey channelKey = new SensorChannelKey(1L, "EUI-01", "TEMPERATURE", "°C");

    @Test
    @DisplayName("집계점이 있을 때 정상 생성 검증")
    void create_withPoints() {
        SensorChannelStatistics stats = new SensorChannelStatistics(
                channelKey,
                new BigDecimal("15.0"),
                new BigDecimal("20.0"),
                new BigDecimal("25.0"),
                10
        );

        assertThat(stats.channelKey()).isEqualTo(channelKey);
        assertThat(stats.minimumValue()).isEqualTo(new BigDecimal("15.0"));
        assertThat(stats.averageValue()).isEqualTo(new BigDecimal("20.0"));
        assertThat(stats.maximumValue()).isEqualTo(new BigDecimal("25.0"));
        assertThat(stats.aggregationPointCount()).isEqualTo(10);
    }

    @Test
    @DisplayName("집계점이 0개일 때 모든 통계값이 null이어야 정상 생성")
    void create_zeroPoints() {
        SensorChannelStatistics stats = new SensorChannelStatistics(
                channelKey, null, null, null, 0
        );

        assertThat(stats.aggregationPointCount()).isZero();
        assertThat(stats.minimumValue()).isNull();
    }

    @Test
    @DisplayName("유효성 검증 실패 분기들 전수 검증")
    void create_validationFailures() {
        SensorChannelKey nullKey = null;
        BigDecimal val = new BigDecimal("20.0");
        BigDecimal min = new BigDecimal("10.0");
        BigDecimal avg = new BigDecimal("20.0");
        BigDecimal max = new BigDecimal("30.0");

        // 1. channelKey null
        assertThatThrownBy(() -> new SensorChannelStatistics(nullKey, min, avg, max, 1))
                .isInstanceOf(IllegalArgumentException.class);

        // 2. aggregationPointCount 음수
        assertThatThrownBy(() -> new SensorChannelStatistics(channelKey, min, avg, max, -1))
                .isInstanceOf(IllegalArgumentException.class);

        // 3. 0포인트인데 값이 존재하는 경우
        assertThatThrownBy(() -> new SensorChannelStatistics(channelKey, val, null, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SensorChannelStatistics(channelKey, null, val, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SensorChannelStatistics(channelKey, null, null, val, 0))
                .isInstanceOf(IllegalArgumentException.class);

        // 4. 포인트가 있는데 일부 값이 null인 경우
        assertThatThrownBy(() -> new SensorChannelStatistics(channelKey, null, avg, max, 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SensorChannelStatistics(channelKey, min, null, max, 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SensorChannelStatistics(channelKey, min, avg, null, 5))
                .isInstanceOf(IllegalArgumentException.class);

        // 5. 정렬 순서 위반 (min > avg 또는 avg > max)
        assertThatThrownBy(() -> new SensorChannelStatistics(channelKey, max, avg, min, 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SensorChannelStatistics(channelKey, min, max, avg, 5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
