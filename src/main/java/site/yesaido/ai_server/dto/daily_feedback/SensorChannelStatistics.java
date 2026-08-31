package site.yesaido.ai_server.dto.daily_feedback;

import java.math.BigDecimal;

/**
 * 하나의 센서 채널에 대한 최근 24시간 추이 집계 결과입니다.
 *
 * <p>통계 기준은 원시 센서 측정값이 아니라 Cultivation Server가 반환한
 * 15분 평균 집계점입니다. {@code minimumValue}와
 * {@code maximumValue} 역시 원시 데이터의 실제 극값이 아니라
 * 반환된 15분 평균 집계점 중의 최솟값과 최댓값입니다.</p>
 *
 * <p>{@code averageValue}는 반환된 15분 평균 집계점들을 동일한 가중치로
 * 다시 평균한 값입니다. {@code aggregationPointCount}는 원시 센서의
 * 측정 횟수가 아니라 반환된 15분 평균 집계점의 개수입니다.</p>
 *
 * <p>현재 조회 시간 범위는 특정 달력 날짜가 아니라 요청 시점을 기준으로
 * 계속 이동하는 최근 24시간입니다.</p>
 *
 * <p>{@code aggregationPointCount}가 0이고 세 통계값이 모두 null인
 * 조합은 등록된 센서 채널에 사용할 수 있는 추이 데이터가 없음을
 * 의미합니다.</p>
 *
 * <p>통계는 {@link SensorChannelKey}의
 * {@code cultivationId + deviceEui + sensorType + unit} 네 필드 전체를
 * 기준으로 다른 EUI, 센서 타입 및 단위와 독립적으로 계산합니다.</p>
 *
 * @param channelKey 통계 대상 원본 센서 채널 식별키
 * @param minimumValue 반환된 15분 평균 집계점 중 최솟값
 * @param averageValue 반환된 15분 평균 집계점의 동일 가중 평균값
 * @param maximumValue 반환된 15분 평균 집계점 중 최댓값
 * @param aggregationPointCount 반환된 15분 평균 집계점 개수
 */
public record SensorChannelStatistics(
        SensorChannelKey channelKey,
        BigDecimal minimumValue,
        BigDecimal averageValue,
        BigDecimal maximumValue,
        int aggregationPointCount
) {

    public SensorChannelStatistics {
        if (channelKey == null) {
            throw new IllegalArgumentException("channelKey는 null일 수 없습니다.");
        }

        if (aggregationPointCount < 0) {
            throw new IllegalArgumentException("aggregationPointCount는 음수일 수 없습니다.");
        }

        if (aggregationPointCount == 0) {
            if (minimumValue != null || averageValue != null || maximumValue != null) {
                throw new IllegalArgumentException("집계점이 없으면 모든 통계값은 null이어야 합니다.");
            }
        } else {
            if (minimumValue == null || averageValue == null || maximumValue == null) {
                throw new IllegalArgumentException("집계점이 있으면 모든 통계값이 존재해야 합니다.");
            }

            if (minimumValue.compareTo(averageValue) > 0 || averageValue.compareTo(maximumValue) > 0) {
                throw new IllegalArgumentException("통계값은 minimumValue, averageValue, maximumValue 순서여야 합니다.");
            }
        }
    }
}
