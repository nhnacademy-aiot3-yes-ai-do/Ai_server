package site.yesaido.ai_server.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.yesaido.ai_server.client.CultivationClient;
import site.yesaido.ai_server.dto.client.sensor.EnvironmentComplianceResponse;
import site.yesaido.ai_server.dto.daily_feedback.DailyEnvironmentCompliance;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@SuppressWarnings("ConstantConditions")
@ExtendWith(MockitoExtension.class)
class DailyEnvironmentComplianceServiceTest {

    @Mock
    private CultivationClient cultivationClient;

    @InjectMocks
    private DailyEnvironmentComplianceService service;

    @Test
    @DisplayName("정상 조회: 일일 환경 유지율 데이터 반환")
    void fetch_success() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        EnvironmentComplianceResponse response = new EnvironmentComplianceResponse(
                BigDecimal.valueOf(95.5), BigDecimal.valueOf(88.0), BigDecimal.valueOf(90.0), BigDecimal.valueOf(100.0)
        );

        given(cultivationClient.getDailyEnvironmentCompliance(1L, date, 100L)).willReturn(response);

        DailyEnvironmentCompliance result = service.fetch(1L, date, 100L);

        assertThat(result).isNotNull();
        assertThat(result.cultivationId()).isEqualTo(1L);
        assertThat(result.date()).isEqualTo(date);
        assertThat(result.temperatureCompliance()).isEqualTo(BigDecimal.valueOf(95.5));
    }

    @Test
    @DisplayName("예외: 응답이 null이거나 유효하지 않은 유지율 범위일 때 IllegalStateException")
    void fetch_invalidResponse() {
        LocalDate date = LocalDate.of(2026, 9, 1);

        // 1. null 응답
        given(cultivationClient.getDailyEnvironmentCompliance(1L, date, 100L)).willReturn(null);
        assertThatThrownBy(() -> service.fetch(1L, date, 100L))
                .isInstanceOf(IllegalStateException.class);

        // 2. 음수 유지율 (DailyEnvironmentCompliance 생성 시 IllegalArgumentException 발생 -> IllegalStateException으로 래핑)
        EnvironmentComplianceResponse invalidRangeResponse = new EnvironmentComplianceResponse(
                BigDecimal.valueOf(-10.0), BigDecimal.valueOf(88.0), BigDecimal.valueOf(90.0), BigDecimal.valueOf(100.0)
        );
        given(cultivationClient.getDailyEnvironmentCompliance(1L, date, 100L)).willReturn(invalidRangeResponse);
        assertThatThrownBy(() -> service.fetch(1L, date, 100L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("예외: 파라미터 null 검증")
    void fetch_nullParams() {
        LocalDate date = LocalDate.now();
        assertThatThrownBy(() -> service.fetch(null, date, 100L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.fetch(1L, null, 100L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.fetch(1L, date, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
