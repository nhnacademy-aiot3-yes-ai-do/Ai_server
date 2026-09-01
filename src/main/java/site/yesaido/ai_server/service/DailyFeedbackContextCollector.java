package site.yesaido.ai_server.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.yesaido.ai_server.dto.client.cultivation.DailyCultivationDetailResponse;
import site.yesaido.ai_server.dto.client.mushroom_reference.MushroomReferenceInfoResponse;
import site.yesaido.ai_server.dto.client.sensor.snapshot.DataGeneratorSnapshotResponse;
import site.yesaido.ai_server.dto.client.sensor.snapshot.DataGeneratorThresholdResponse;
import site.yesaido.ai_server.dto.cultivation.DailyCultivationPhotoResponse;
import site.yesaido.ai_server.dto.daily_feedback.DailyEnvironmentCompliance;
import site.yesaido.ai_server.dto.daily_feedback.DailyFeedbackContext;
import site.yesaido.ai_server.dto.daily_feedback.DailyNotificationMetrics;
import site.yesaido.ai_server.dto.daily_feedback.DailyVisionAnalysisSnapshot;
import site.yesaido.ai_server.dto.daily_feedback.SensorChannelStatistics;
import site.yesaido.ai_server.entity.GrowthRecord;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 한 경작지와 한 날짜의 일일 피드백 Context를 수집하고 조립합니다.
 *
 * <p>Data Generator Snapshot, 버섯 참조정보 Map, Notification 통계 Map,
 * 날짜별 사진 Map은 상위 배치 오케스트레이터가 각각 한 번 조회하여
 * 전달한 공통 데이터입니다. 이 서비스는 공통 데이터를 다시 조회하지 않고
 * 경작지 한 곳에 필요한 정보만 해석하고 수집합니다.</p>
 *
 * <p>OWNER 사용자 ID도 상위 계층에서 한 번 조회한 값을 전달받아 재배 상세,
 * 센서 추이와 환경 유지율 조회에 재사용합니다. 이 클래스에서는 OWNER를
 * 다시 조회하거나 ADMIN 우회를 사용하지 않습니다.</p>
 *
 * <p>불필요한 이미지 다운로드와 Vision 호출을 줄이기 위해 재배 상세,
 * 버섯 참조정보, 현재 임계값, 센서 통계, 환경 유지율 등 다른 필수 데이터가
 * 모두 정상적으로 수집된 다음 마지막으로 Vision 분석을 실행합니다.</p>
 *
 * <p>외부 호출 실패나 계약 위반을 빈 값 또는 기본값으로 바꾸지 않습니다.
 * 경작지 한 곳의 실패를 다른 경작지와 격리하는 책임은 이 서비스가 아니라
 * 이후 최상위 배치 오케스트레이터가 담당합니다.</p>
 */
@Service
@RequiredArgsConstructor
public class DailyFeedbackContextCollector {

    private final DailyCultivationDetailService cultivationDetailService;
    private final DailyMushroomReferenceService mushroomReferenceService;
    private final DailyFeedbackTargetResolver targetResolver;
    private final DailySensorStatisticsService sensorStatisticsService;
    private final DailyEnvironmentComplianceService environmentComplianceService;
    private final DailyVisionAnalysisService visionAnalysisService;

    /**
     * 공통 배치 데이터와 경작지별 조회 결과를 이용해
     * 하나의 일일 피드백 Context를 생성합니다.
     *
     * <p>사진이 없는 경우에만 Vision 분석이 없는 Snapshot을 생성합니다.
     * 사진 다운로드나 Vision 호출이 실패하면 예외를 그대로 전파하며,
     * 이를 사진 없음 상태로 변환하지 않습니다.</p>
     *
     * @param feedbackDate 피드백 대상 Asia/Seoul 달력 날짜
     * @param cultivationId Context를 수집할 경작지 ID
     * @param ownerUserId Cultivation 권한 검사에 사용할 OWNER 사용자 ID
     * @param snapshot 배치 시작 시 한 번 조회한 Data Generator Snapshot
     * @param referencesById 배치 시작 시 한 번 조회한 버섯 참조정보 Map
     * @param notificationMetricsByCultivationId 배치에서 한 번 조회한 경작지별 Notification 통계 Map
     * @param photosByCultivationId 배치에서 한 번 조회한 경작지별 사진 Map
     * @return 모든 필수 수집과 계약 검증을 통과한 일일 피드백 Context
     * @throws IllegalArgumentException 입력값이 유효하지 않은 경우
     * @throws IllegalStateException 공통 데이터가 대상 경작지와 일치하지 않거나 필수 데이터가 누락된 경우
     */
    public DailyFeedbackContext collect(
            LocalDate feedbackDate,
            Long cultivationId,
            Long ownerUserId,
            DataGeneratorSnapshotResponse snapshot,
            Map<Long, MushroomReferenceInfoResponse> referencesById,
            Map<Long, DailyNotificationMetrics>
                    notificationMetricsByCultivationId,
            Map<Long, DailyCultivationPhotoResponse> photosByCultivationId
    ) {
        if (feedbackDate == null) {
            throw new IllegalArgumentException("feedbackDate는 null일 수 없습니다.");
        }

        if (cultivationId == null || cultivationId <= 0) {
            throw new IllegalArgumentException("cultivationId는 null이 아니며 0보다 커야 합니다.");
        }

        if (ownerUserId == null || ownerUserId <= 0) {
            throw new IllegalArgumentException("ownerUserId는 null이 아니며 0보다 커야 합니다.");
        }

        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot은 null일 수 없습니다.");
        }

        if (referencesById == null) {
            throw new IllegalArgumentException("referencesById는 null일 수 없습니다.");
        }

        if (notificationMetricsByCultivationId == null) {
            throw new IllegalArgumentException("notificationMetricsByCultivationId는 null일 수 없습니다.");
        }

        if (photosByCultivationId == null) {
            throw new IllegalArgumentException("photosByCultivationId는 null일 수 없습니다.");
        }

        List<Long> targetCultivationIds = targetResolver.resolveCultivationIds(snapshot);

        if (!targetCultivationIds.contains(cultivationId)) {
            throw new IllegalStateException("배치 Snapshot에 대상 경작지가 존재하지 않습니다: cultivationId=%s"
                    .formatted(cultivationId));
        }

        DailyNotificationMetrics notificationMetrics = requireNotificationMetrics(notificationMetricsByCultivationId, cultivationId, feedbackDate);

        DailyCultivationDetailResponse detail = cultivationDetailService.fetch(cultivationId, ownerUserId);
        MushroomReferenceInfoResponse mushroomReference = mushroomReferenceService.requireReference(referencesById, detail.mushroomId());

        List<DataGeneratorThresholdResponse> currentThresholds = targetResolver.resolveCurrentThresholds(snapshot, cultivationId);
        List<SensorChannelStatistics> sensorStatistics = sensorStatisticsService.collect(snapshot, cultivationId, ownerUserId);

        DailyEnvironmentCompliance environmentCompliance = environmentComplianceService.fetch(cultivationId, feedbackDate, ownerUserId);
        Optional<GrowthRecord> growthRecord = visionAnalysisService.analyzeIfPresent(photosByCultivationId, cultivationId);

        DailyVisionAnalysisSnapshot visionAnalysis;

        if (growthRecord.isPresent()) {
            GrowthRecord analyzedRecord = growthRecord.get();

            visionAnalysis = DailyVisionAnalysisSnapshot.analyzed(
                    cultivationId,
                    analyzedRecord.getId(),
                    analyzedRecord.getCultivationPhotoId(),
                    analyzedRecord.getAnalysisData(),
                    analyzedRecord.getAnalyzedAt()
            );
        } else {
            visionAnalysis = DailyVisionAnalysisSnapshot.withoutPhoto(cultivationId);
        }

        return new DailyFeedbackContext(
                cultivationId,
                feedbackDate,
                snapshot.snapshotAt(),
                detail,
                mushroomReference,
                currentThresholds,
                sensorStatistics,
                environmentCompliance,
                notificationMetrics,
                visionAnalysis
        );
    }

    /**
     * 대상 경작지와 날짜에 정확히 대응하는 Notification 통계를 반환합니다.
     *
     * <p>통계가 누락되거나 null인 경우 모든 횟수가 0인 기본 통계를
     * 생성하지 않고 공통 배치 응답 계약 위반으로 처리합니다.</p>
     *
     * @param metricsByCultivationId 경작지별 Notification 통계 Map
     * @param cultivationId 통계를 찾을 경작지 ID
     * @param feedbackDate 통계가 일치해야 하는 피드백 날짜
     * @return 대상 ID와 날짜가 검증된 Notification 통계
     * @throws IllegalStateException 통계가 없거나 식별정보가 일치하지 않는 경우
     */
    private DailyNotificationMetrics requireNotificationMetrics(Map<Long, DailyNotificationMetrics> metricsByCultivationId, Long cultivationId, LocalDate feedbackDate) {
        if (!metricsByCultivationId.containsKey(cultivationId)) {
            throw new IllegalStateException("대상 경작지의 Notification 통계가 누락되었습니다: cultivationId=%s, feedbackDate=%s"
                    .formatted(cultivationId, feedbackDate));
        }

        DailyNotificationMetrics metrics = metricsByCultivationId.get(cultivationId);

        if (metrics == null) {
            throw new IllegalStateException("대상 경작지의 Notification 통계가 null입니다: cultivationId=%s, feedbackDate=%s"
                    .formatted(cultivationId, feedbackDate));
        }

        if (!cultivationId.equals(metrics.cultivationId())) {
            throw new IllegalStateException("Notification 통계의 cultivationId가 요청과 일치하지 않습니다: requestedCultivationId=%s, metricsCultivationId=%s"
                    .formatted(cultivationId, metrics.cultivationId()));
        }

        if (!feedbackDate.equals(metrics.date())) {
            throw new IllegalStateException("Notification 통계 날짜가 feedbackDate와 일치하지 않습니다: feedbackDate=%s, metricsDate=%s"
                    .formatted(feedbackDate, metrics.date()));
        }

        return metrics;
    }
}
