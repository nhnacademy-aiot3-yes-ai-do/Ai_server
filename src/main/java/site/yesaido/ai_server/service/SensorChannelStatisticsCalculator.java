package site.yesaido.ai_server.service;

import org.springframework.stereotype.Component;
import site.yesaido.ai_server.dto.client.sensor.trend.SensorTrendPointListResponse;
import site.yesaido.ai_server.dto.client.sensor.trend.SensorTrendPointResponse;
import site.yesaido.ai_server.dto.daily_feedback.SensorChannelKey;
import site.yesaido.ai_server.dto.daily_feedback.SensorChannelStatistics;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

/**
 * Cultivation Server가 반환한 센서 추이 응답을 검증하고
 * 채널별 통계를 계산합니다.
 *
 * <p>계산 전에 요청 채널과 응답의 {@code cultivationId},
 * {@code deviceEui}, {@code sensorType}, {@code unit} 네 필드가
 * 모두 정확히 일치하는지 검증합니다. EUI가 같더라도 경작지,
 * 센서 타입 또는 단위가 다르면 절대 같은 채널로 합치지 않습니다.</p>
 *
 * <p>평균값은 원시 센서 측정값이 아니라 서버가 반환한 15분 평균
 * 집계점들을 동일한 가중치로 다시 산술평균한 값입니다.</p>
 *
 * <p>데이터가 없는 정상 응답만 집계점 수가 0인 통계로 변환합니다.
 * null 응답이나 채널 계약 불일치는 무데이터로 처리하지 않고
 * 장애로 상위 계층에 전파합니다.</p>
 *
 * <p>표시 목적의 반올림이나 scale 조정은 이 계산기에서 수행하지 않으며,
 * 필요한 경우 최종 출력 계층에서 처리해야 합니다.</p>
 */
@Component
public class SensorChannelStatisticsCalculator {

    /**
     * 요청 채널과 일치하는 추이 응답의 15분 평균 집계점 통계를 계산합니다.
     *
     * @param requestedChannel 통계를 요청한 원본 센서 채널
     * @param response Cultivation Server가 반환한 센서 추이 응답
     * @return 요청 채널의 최솟값, 평균값, 최댓값과 집계점 수
     * @throws IllegalArgumentException 요청 채널이 null인 경우
     * @throws IllegalStateException 응답이 null이거나 채널 계약이 일치하지 않는 경우
     */
    public SensorChannelStatistics calculate(
            SensorChannelKey requestedChannel,
            SensorTrendPointListResponse response
    ) {
        if (requestedChannel == null) {
            throw new IllegalArgumentException("requestedChannel은 null일 수 없습니다.");
        }

        if (response == null) {
            throw new IllegalStateException("센서 추이 응답이 null입니다.");
        }

        SensorChannelKey responseChannel = new SensorChannelKey(
                response.cultivationId(),
                response.deviceEui(),
                response.sensorType(),
                response.unit()
        );

        if (!requestedChannel.equals(responseChannel)) {
            throw new IllegalStateException("센서 추이 응답 채널이 요청 채널과 일치하지 않습니다: requestedChannel=%s, responseChannel=%s "
                    .formatted(requestedChannel, responseChannel).strip());
        }

        List<SensorTrendPointResponse> points = response.responses();

        if (points.isEmpty()) {
            return new SensorChannelStatistics(
                    requestedChannel,
                    null,
                    null,
                    null,
                    0
            );
        }

        BigDecimal minimumValue = points.getFirst().value();
        BigDecimal maximumValue = points.getFirst().value();
        BigDecimal sum = BigDecimal.ZERO;

        for (SensorTrendPointResponse point : points) {
            BigDecimal value = point.value();

            if (value.compareTo(minimumValue) < 0) {
                minimumValue = value;
            }

            if (value.compareTo(maximumValue) > 0) {
                maximumValue = value;
            }

            sum = sum.add(value);
        }

        int aggregationPointCount = points.size();
        BigDecimal averageValue = sum.divide(BigDecimal.valueOf(aggregationPointCount), MathContext.DECIMAL128);

        return new SensorChannelStatistics(
                requestedChannel,
                minimumValue,
                averageValue,
                maximumValue,
                aggregationPointCount
        );
    }
}
