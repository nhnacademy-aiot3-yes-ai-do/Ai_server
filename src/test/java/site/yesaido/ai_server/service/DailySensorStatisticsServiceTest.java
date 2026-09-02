package site.yesaido.ai_server.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.yesaido.ai_server.client.CultivationClient;
import site.yesaido.ai_server.dto.client.sensor.snapshot.DataGeneratorSnapshotResponse;
import site.yesaido.ai_server.dto.client.sensor.trend.SensorTrendPointListResponse;
import site.yesaido.ai_server.dto.daily_feedback.SensorChannelKey;
import site.yesaido.ai_server.dto.daily_feedback.SensorChannelStatistics;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class DailySensorStatisticsServiceTest {

    @Mock
    private CultivationClient cultivationClient;

    @Mock
    private DailyFeedbackTargetResolver targetResolver;

    @Mock
    private SensorChannelStatisticsCalculator statisticsCalculator;

    @InjectMocks
    private DailySensorStatisticsService service;

    @Test
    @DisplayName("정상 수집: 채널 목록이 있을 때 통계 리스트 반환")
    void collect_success() {
        DataGeneratorSnapshotResponse snapshot = new DataGeneratorSnapshotResponse(
                OffsetDateTime.now(ZoneOffset.ofHours(9)), List.of(), List.of()
        );
        SensorChannelKey channel = new SensorChannelKey(1L, "EUI-001", "TEMPERATURE", "°C");
        SensorTrendPointListResponse trendResponse = new SensorTrendPointListResponse(
                1L, "EUI-001", "TEMPERATURE", "°C", List.of()
        );
        SensorChannelStatistics stats = new SensorChannelStatistics(
                channel, BigDecimal.valueOf(18.0), BigDecimal.valueOf(20.0), BigDecimal.valueOf(22.0), 10
        );

        given(targetResolver.resolveChannels(snapshot, 1L)).willReturn(List.of(channel));
        given(cultivationClient.getSensorTrend(1L, "EUI-001", "TEMPERATURE", 100L)).willReturn(trendResponse);
        given(statisticsCalculator.calculate(channel, trendResponse)).willReturn(stats);

        List<SensorChannelStatistics> result = service.collect(snapshot, 1L, 100L);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().channelKey()).isEqualTo(channel);
        assertThat(result.getFirst().averageValue()).isEqualTo(BigDecimal.valueOf(20.0));
    }

    @Test
    @DisplayName("빈 채널 목록인 경우 빈 리스트 반환")
    void collect_emptyChannels() {
        DataGeneratorSnapshotResponse snapshot = new DataGeneratorSnapshotResponse(
                OffsetDateTime.now(ZoneOffset.ofHours(9)), List.of(), List.of()
        );
        given(targetResolver.resolveChannels(snapshot, 1L)).willReturn(List.of());

        List<SensorChannelStatistics> result = service.collect(snapshot, 1L, 100L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("예외: 파라미터 null 검증")
    void collect_invalidParams() {
        DataGeneratorSnapshotResponse snapshot = new DataGeneratorSnapshotResponse(
                OffsetDateTime.now(ZoneOffset.ofHours(9)), List.of(), List.of()
        );

        assertThatThrownBy(() -> service.collect(null, 1L, 100L))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.collect(snapshot, null, 100L))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.collect(snapshot, 1L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}