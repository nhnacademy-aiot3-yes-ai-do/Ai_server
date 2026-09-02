package site.yesaido.ai_server.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.yesaido.ai_server.dto.daily_feedback.DailyEnvironmentCompliance;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("ConstantConditions")
class DailyEnvironmentComplianceTest {

    @Test
    @DisplayName("DailyEnvironmentCompliance 정상 생성 검증")
    void create_success() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        DailyEnvironmentCompliance compliance = new DailyEnvironmentCompliance(
                1L, date,
                BigDecimal.valueOf(95.5), BigDecimal.valueOf(88.0),
                BigDecimal.valueOf(100.0), BigDecimal.valueOf(0.0)
        );

        assertThat(compliance.cultivationId()).isEqualTo(1L);
        assertThat(compliance.date()).isEqualTo(date);
        assertThat(compliance.temperatureCompliance()).isEqualTo(BigDecimal.valueOf(95.5));
        assertThat(compliance.humidityCompliance()).isEqualTo(BigDecimal.valueOf(88.0));
        assertThat(compliance.co2Compliance()).isEqualTo(BigDecimal.valueOf(100.0));
        assertThat(compliance.lightCompliance()).isEqualTo(BigDecimal.valueOf(0.0));
    }

    @Test
    @DisplayName("유지율 퍼센트 0~100 범위 벗어날 때 예외 검증")
    void create_invalidPercentage() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        BigDecimal invalidHigh = BigDecimal.valueOf(100.1);
        BigDecimal invalidLow = BigDecimal.valueOf(-0.1);

        assertThatThrownBy(() -> new DailyEnvironmentCompliance(1L, date, invalidHigh, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new DailyEnvironmentCompliance(1L, date, null, invalidLow, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
