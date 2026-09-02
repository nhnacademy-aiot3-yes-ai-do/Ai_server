package site.yesaido.ai_server.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.yesaido.ai_server.dto.daily_feedback.SensorChannelKey;
import site.yesaido.ai_server.dto.daily_feedback.SensorChannelStatistics;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    @SuppressWarnings("ConstantConditions")
    @DisplayName("유효성 검증 실패: 최소 > 평균 또는 null 불일치 시 예외")
    void create_invalid() {
        // 집계점이 있는데 null인 경우
        assertThatThrownBy(() -> new SensorChannelStatistics(channelKey, null, null, null, 5))
                .isInstanceOf(IllegalArgumentException.class);

        BigDecimal min = new BigDecimal("30.0");
        BigDecimal avg = new BigDecimal("20.0");
        BigDecimal max = new BigDecimal("25.0");

        // 최소값이 평균값보다 큰 경우
        assertThatThrownBy(() -> new SensorChannelStatistics(channelKey, min, avg, max, 5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
