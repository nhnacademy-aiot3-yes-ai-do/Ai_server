package site.yesaido.ai_server.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import site.yesaido.ai_server.dto.client.sensor.trend.SensorTrendPointListResponse;
import site.yesaido.ai_server.dto.client.sensor.trend.SensorTrendPointResponse;
import site.yesaido.ai_server.dto.daily_feedback.SensorChannelKey;
import site.yesaido.ai_server.dto.daily_feedback.SensorChannelStatistics;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("센서 채널 통계 계산기 테스트")
class SensorChannelStatisticsCalculatorTest {

    private static final Long CULTIVATION_ID = 7L;
    private static final String DEVICE_EUI = "EUI-001";
    private static final String SENSOR_TYPE = "TEMPERATURE";
    private static final String UNIT = "C";

    private SensorChannelStatisticsCalculator calculator;

    @BeforeEach
    void setUpCalculator() {
        calculator = new SensorChannelStatisticsCalculator();
    }

    @Test
    @DisplayName("시간순이 아닌 15분 집계점으로 최솟값·평균값·최댓값을 계산한다")
    void calculatesStatisticsFromUnorderedAggregationPoints() {
        // 준비
        SensorChannelKey requestedChannel = requestedChannel();

        List<SensorTrendPointResponse> points = List.of(
                point("2026-08-31T00:30:00Z", "2"),
                point("2026-08-31T00:00:00Z", "1"),
                point("2026-08-31T00:15:00Z", "2")
        );

        SensorTrendPointListResponse response = trendResponse(requestedChannel, points);

        BigDecimal sum = new BigDecimal("1")
                .add(new BigDecimal("2"))
                .add(new BigDecimal("2"));

        BigDecimal expectedAverage = sum.divide(BigDecimal.valueOf(points.size()), MathContext.DECIMAL128);

        // 실행
        SensorChannelStatistics statistics = calculator.calculate(requestedChannel, response);

        // 검증
        assertThat(statistics.channelKey()).isSameAs(requestedChannel);
        assertThat(statistics.minimumValue()).isEqualByComparingTo(new BigDecimal("1"));
        assertThat(statistics.averageValue()).isEqualByComparingTo(expectedAverage);
        assertThat(statistics.maximumValue()).isEqualByComparingTo(new BigDecimal("2"));
        assertThat(statistics.aggregationPointCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("빈 집계점 목록은 통계값이 없는 정상 결과로 반환한다")
    void returnsEmptyStatisticsForEmptyAggregationPoints() {
        // 준비
        SensorChannelKey requestedChannel = requestedChannel();
        SensorTrendPointListResponse response = trendResponse(requestedChannel, List.of());

        // 실행
        SensorChannelStatistics statistics = calculator.calculate(requestedChannel, response);

        // 검증
        assertThat(statistics.channelKey()).isSameAs(requestedChannel);
        assertThat(statistics.aggregationPointCount()).isZero();
        assertThat(statistics.minimumValue()).isNull();
        assertThat(statistics.averageValue()).isNull();
        assertThat(statistics.maximumValue()).isNull();
    }

    @Test
    @DisplayName("요청 채널이 null이면 IllegalArgumentException이 발생한다")
    void throwsWhenRequestedChannelIsNull() {
        // 준비
        SensorTrendPointListResponse response = trendResponse(requestedChannel(), List.of());

        // 실행 및 검증
        assertThatThrownBy(() -> calculator.calculate(null, response))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requestedChannel");
    }

    @Test
    @DisplayName("센서 추이 응답이 null이면 IllegalStateException이 발생한다")
    void throwsWhenResponseIsNull() {
        // 준비
        SensorChannelKey requestedChannel = requestedChannel();

        // 실행 및 검증
        assertThatThrownBy(
                () -> calculator.calculate(requestedChannel, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("센서 추이 응답")
                .hasMessageContaining("null");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("responseChannelMismatchCases")
    @DisplayName("응답 채널의 네 메타데이터 중 하나라도 다르면 실패한다")
    void throwsWhenResponseChannelMetadataDoesNotMatch(String caseDescription, SensorTrendPointListResponse response) {
        // 준비
        SensorChannelKey requestedChannel = requestedChannel();

        // 실행 및 검증
        assertThatThrownBy(
                () -> calculator.calculate(requestedChannel, response))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requestedChannel=")
                .hasMessageContaining("responseChannel=");
    }

    @Test
    @DisplayName("동일한 측정 시각의 집계점도 제거하지 않고 모두 계산한다")
    void includesAllAggregationPointsWithSameTimestamp() {
        // 준비
        SensorChannelKey requestedChannel = requestedChannel();
        Instant sameMeasuredAt = Instant.parse("2026-08-31T00:00:00Z");

        List<SensorTrendPointResponse> points = List.of(
                new SensorTrendPointResponse(sameMeasuredAt, new BigDecimal("10")),
                new SensorTrendPointResponse(sameMeasuredAt, new BigDecimal("20"))
        );

        SensorTrendPointListResponse response = trendResponse(requestedChannel, points);

        BigDecimal sum = new BigDecimal("10").add(new BigDecimal("20"));

        BigDecimal expectedAverage = sum.divide(BigDecimal.valueOf(points.size()), MathContext.DECIMAL128);

        // 실행
        SensorChannelStatistics statistics = calculator.calculate(requestedChannel, response);

        // 검증
        assertThat(statistics.aggregationPointCount()).isEqualTo(points.size());
        assertThat(statistics.minimumValue()).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(statistics.averageValue()).isEqualByComparingTo(expectedAverage);
        assertThat(statistics.maximumValue()).isEqualByComparingTo(new BigDecimal("20"));
    }

    private static Stream<Arguments> responseChannelMismatchCases() {
        return Stream.of(
                Arguments.of("cultivationId 불일치", new SensorTrendPointListResponse(
                                8L, DEVICE_EUI, SENSOR_TYPE, UNIT, List.of())),
                Arguments.of("deviceEui 불일치", new SensorTrendPointListResponse(
                                CULTIVATION_ID, "eui-001", SENSOR_TYPE, UNIT, List.of())),
                Arguments.of("sensorType 불일치", new SensorTrendPointListResponse(
                        CULTIVATION_ID, DEVICE_EUI, "temperature", UNIT, List.of())),
                Arguments.of("unit 불일치", new SensorTrendPointListResponse(
                                CULTIVATION_ID, DEVICE_EUI, SENSOR_TYPE, "c", List.of()))
        );
    }

    private static SensorChannelKey requestedChannel() {
        return new SensorChannelKey(
                CULTIVATION_ID,
                DEVICE_EUI,
                SENSOR_TYPE,
                UNIT
        );
    }

    private static SensorTrendPointResponse point(String measuredAt, String value) {
        return new SensorTrendPointResponse(Instant.parse(measuredAt), new BigDecimal(value));
    }

    private static SensorTrendPointListResponse trendResponse(SensorChannelKey channel, List<SensorTrendPointResponse> points) {
        return new SensorTrendPointListResponse(
                channel.cultivationId(),
                channel.deviceEui(),
                channel.sensorType(),
                channel.unit(),
                points
        );
    }
}
