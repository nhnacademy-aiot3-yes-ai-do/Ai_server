package site.yesaido.ai_server.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.yesaido.ai_server.client.CultivationClient;
import site.yesaido.ai_server.dto.client.sensor.snapshot.DataGeneratorSnapshotResponse;
import site.yesaido.ai_server.dto.client.sensor.trend.SensorTrendPointListResponse;
import site.yesaido.ai_server.dto.daily_feedback.SensorChannelKey;
import site.yesaido.ai_server.dto.daily_feedback.SensorChannelStatistics;

import java.util.ArrayList;
import java.util.List;

/**
 * 특정 경작지의 센서 채널별 최근 24시간 통계를 수집합니다.
 *
 * <p>이미 조회된 Snapshot을 {@link DailyFeedbackTargetResolver}로 해석하여
 * 센서 채널을 결정하고, 각 채널의 Trend를 Cultivation Server에서 조회한
 * 다음 {@link SensorChannelStatisticsCalculator}로 통계를 계산합니다.</p>
 *
 * <p>Trend API의 시간 범위는 특정 달력 날짜가 아니라 요청 시점을
 * 기준으로 계속 이동하는 최근 24시간입니다. 결과의 최솟값, 평균값,
 * 최댓값은 원시 센서 측정값 통계가 아니라 서버가 반환한 15분 평균
 * 집계점의 통계입니다.</p>
 *
 * <p>OWNER 사용자 ID는 Cultivation Server의 현재 사용자 권한 계약을
 * 만족시키기 위해 상위 일일 피드백 조립 계층에서 한 번 조회한 뒤
 * 이 서비스에 전달합니다. 채널마다 OWNER를 다시 조회하지 않습니다.</p>
 *
 * <p>채널을 순차적으로 호출하여 Resolver가 정한 결정적인 결과 순서를
 * 유지하고 Cultivation Server에 동시에 과도한 요청을 보내지 않습니다.</p>
 *
 * <p>빈 채널 목록은 오류가 아니라 해당 경작지에 조회할 센서 채널이
 * 없다는 의미입니다. 반면 외부 호출 실패, null 응답 또는 채널
 * 메타데이터 불일치는 데이터 없음으로 숨기지 않고 상위 계층에
 * 그대로 전파합니다.</p>
 */
@Service
@RequiredArgsConstructor
public class DailySensorStatisticsService {

    private final CultivationClient cultivationClient;
    private final DailyFeedbackTargetResolver targetResolver;
    private final SensorChannelStatisticsCalculator statisticsCalculator;

    /**
     * 특정 경작지에 등록된 센서 채널의 최근 24시간 통계를 순차 수집합니다.
     *
     * <p>Trend 요청에는 단위가 포함되지 않지만, 계산기가 응답의 단위와
     * 요청 채널의 단위가 정확히 일치하는지 검증합니다.</p>
     *
     * @param snapshot 실행 시점에 이미 조회한 Data Generator Snapshot
     * @param cultivationId 통계를 수집할 경작지 ID
     * @param ownerUserId Cultivation 사용자 권한 검사에 사용할 OWNER 사용자 ID
     * @return 채널 정렬 순서를 유지하는 수정 불가능한 통계 목록
     * @throws IllegalArgumentException 입력값이 유효하지 않은 경우
     */
    public List<SensorChannelStatistics> collect(DataGeneratorSnapshotResponse snapshot, Long cultivationId, Long ownerUserId) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot은 null일 수 없습니다.");
        }

        if (cultivationId == null || cultivationId <= 0) {
            throw new IllegalArgumentException("cultivationId는 null이 아니며 0보다 커야 합니다.");
        }

        if (ownerUserId == null || ownerUserId <= 0) {
            throw new IllegalArgumentException("ownerUserId는 null이 아니며 0보다 커야 합니다.");
        }

        List<SensorChannelKey> channels = targetResolver.resolveChannels(snapshot, cultivationId);

        if (channels.isEmpty()) {
            return List.of();
        }

        List<SensorChannelStatistics> statistics = new ArrayList<>(channels.size());

        for (SensorChannelKey channel : channels) {
            SensorTrendPointListResponse response = cultivationClient.getSensorTrend(
                    channel.cultivationId(), channel.deviceEui(), channel.sensorType(), ownerUserId);

            SensorChannelStatistics channelStatistics = statisticsCalculator.calculate(channel, response);

            statistics.add(channelStatistics);
        }

        return List.copyOf(statistics);
    }
}
