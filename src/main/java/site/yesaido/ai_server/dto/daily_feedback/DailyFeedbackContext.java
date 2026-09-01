package site.yesaido.ai_server.dto.daily_feedback;

import site.yesaido.ai_server.dto.client.cultivation.DailyCultivationDetailResponse;
import site.yesaido.ai_server.dto.client.mushroom_reference.MushroomReferenceInfoResponse;
import site.yesaido.ai_server.dto.client.mushroom_reference.MushroomReferenceThresholdInfoResponse;
import site.yesaido.ai_server.dto.client.mushroom_reference.SensorTypeInfoResponse;
import site.yesaido.ai_server.dto.client.sensor.snapshot.DataGeneratorThresholdResponse;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 한 경작지와 한 날짜의 일일 피드백 생성 근거를 묶은 불변 Context입니다.
 *
 * <p>외부 서비스에서 정상적으로 수집하고 계약 검증을 통과한 재배 상세정보,
 * 버섯 참조정보, 센서 통계, 환경 유지율, Notification 통계와 Vision 분석
 * Snapshot을 하나의 결정적인 구조로 보존합니다.</p>
 *
 * <p>각 데이터의 시간 기준은 서로 다릅니다.</p>
 *
 * <ul>
 *     <li>{@code currentThresholds}: {@code dataGeneratorSnapshotAt} 시점의 현재 설정</li>
 *     <li>{@code sensorStatistics}: 호출 시점 기준 rolling 24시간의 15분 평균 집계점 통계</li>
 *     <li>{@code environmentCompliance}: Asia/Seoul 달력 날짜 하루의 원시 측정값 유지율</li>
 *     <li>{@code notificationMetrics}: 같은 달력 날짜의 Notification 원본 이벤트 통계</li>
 *     <li>{@code visionAnalysis}: 사진이 존재할 때 수행한 Vision 분석 Snapshot</li>
 * </ul>
 *
 * <p>{@code dataGeneratorSnapshotAt}의 날짜와 Vision 분석 시각은 배치 실행이나
 * 재시도로 다음 날이 될 수 있으므로 {@code feedbackDate}와 같다고 강제하지
 * 않습니다. 현재 임계값도 피드백 날짜 하루 내내 적용된 과거 이력으로
 * 해석하면 안 됩니다.</p>
 *
 * <p>Vision 분석 여부는 {@link DailyVisionAnalysisSnapshot#hasVisionAnalysis()}
 * 값만을 단일 진실로 사용합니다. false는 사진이 없었다는 의미이며 분석
 * 실패를 의미하지 않습니다. {@code NO_MUSHROOM_DETECTED}도 정상적으로
 * 수행된 Vision 분석입니다.</p>
 *
 * <p>이 Context는 외부 API 호출, fallback 생성, LLM 호출과 프롬프트,
 * JSON 변환, DailyFeedback 저장, RabbitMQ 이벤트 발행, 수확 전환 판단,
 * 임계값 비교와 환경 점수 계산을 수행하지 않습니다. OWNER 사용자 ID,
 * GrowthRecord Entity와 Presigned URL도 포함하지 않습니다.</p>
 *
 * @param cultivationId Context가 속한 경작지 ID
 * @param feedbackDate Asia/Seoul 기준 피드백 대상 달력 날짜
 * @param dataGeneratorSnapshotAt 센서와 현재 임계값 Snapshot 생성 시각
 * @param cultivationDetail 재배 이름·버섯 ID·상태·모드·시작 시각
 * @param mushroomReference 재배 버섯의 이름과 참조 임계값
 * @param currentThresholds Snapshot 생성 시점의 현재 경작지 임계값
 * @param sensorStatistics 채널별 rolling 24시간 센서 통계
 * @param environmentCompliance 피드백 날짜의 공식 환경 유지율
 * @param notificationMetrics 피드백 날짜의 Notification 원본 이벤트 통계
 * @param visionAnalysis 사진 유무와 Vision 분석 결과 Snapshot
 */
public record DailyFeedbackContext(
        Long cultivationId,
        LocalDate feedbackDate,
        OffsetDateTime dataGeneratorSnapshotAt,
        DailyCultivationDetailResponse cultivationDetail,
        MushroomReferenceInfoResponse mushroomReference,
        List<DataGeneratorThresholdResponse> currentThresholds,
        List<SensorChannelStatistics> sensorStatistics,
        DailyEnvironmentCompliance environmentCompliance,
        DailyNotificationMetrics notificationMetrics,
        DailyVisionAnalysisSnapshot visionAnalysis
) {

    private static final ZoneOffset SEOUL_OFFSET = ZoneOffset.ofHours(9);
    private static final String OWNER_ROLE = "OWNER";

    public DailyFeedbackContext {
        if (cultivationId == null || cultivationId <= 0) {
            throw new IllegalArgumentException("cultivationId는 null이 아니며 0보다 커야 합니다.");
        }

        if (feedbackDate == null) {
            throw new IllegalArgumentException("feedbackDate는 null일 수 없습니다.");
        }

        if (dataGeneratorSnapshotAt == null) {
            throw new IllegalArgumentException("dataGeneratorSnapshotAt은 null일 수 없습니다.");
        }

        if (!SEOUL_OFFSET.equals(dataGeneratorSnapshotAt.getOffset())) {
            throw new IllegalArgumentException(
                    "dataGeneratorSnapshotAt의 offset은 +09:00이어야 합니다."
            );
        }

        if (cultivationDetail == null) {
            throw new IllegalArgumentException("cultivationDetail은 null일 수 없습니다.");
        }

        if (mushroomReference == null) {
            throw new IllegalArgumentException("mushroomReference는 null일 수 없습니다.");
        }

        if (currentThresholds == null) {
            throw new IllegalArgumentException("currentThresholds는 null일 수 없습니다.");
        }

        if (sensorStatistics == null) {
            throw new IllegalArgumentException("sensorStatistics는 null일 수 없습니다.");
        }

        if (environmentCompliance == null) {
            throw new IllegalArgumentException("environmentCompliance는 null일 수 없습니다.");
        }

        if (notificationMetrics == null) {
            throw new IllegalArgumentException("notificationMetrics는 null일 수 없습니다.");
        }

        if (visionAnalysis == null) {
            throw new IllegalArgumentException("visionAnalysis는 null일 수 없습니다. 사진이 없으면 DailyVisionAnalysisSnapshot.withoutPhoto를 사용해야 합니다.");
        }

        mushroomReference = validateAndCopyMushroomReference(mushroomReference);
        currentThresholds = normalizeCurrentThresholds(cultivationId, currentThresholds);
        sensorStatistics = normalizeSensorStatistics(cultivationId, sensorStatistics);

        if (!cultivationId.equals(cultivationDetail.cultivationId())) {
            throw new IllegalArgumentException("cultivationDetail의 cultivationId가 Context와 일치하지 않습니다: contextCultivationId=%s, detailCultivationId=%s"
                    .formatted(cultivationId, cultivationDetail.cultivationId()));
        }

        if (!cultivationId.equals(environmentCompliance.cultivationId())) {
            throw new IllegalArgumentException("environmentCompliance의 cultivationId가 Context와 일치하지 않습니다: contextCultivationId=%s, complianceCultivationId=%s"
                    .formatted(cultivationId, environmentCompliance.cultivationId()));
        }

        if (!cultivationId.equals(notificationMetrics.cultivationId())) {
            throw new IllegalArgumentException("notificationMetrics의 cultivationId가 Context와 일치하지 않습니다: contextCultivationId=%s, metricsCultivationId=%s"
                    .formatted(cultivationId, notificationMetrics.cultivationId()));
        }

        if (!cultivationId.equals(visionAnalysis.cultivationId())) {
            throw new IllegalArgumentException("visionAnalysis의 cultivationId가 Context와 일치하지 않습니다: contextCultivationId=%s, visionCultivationId=%s"
                    .formatted(cultivationId, visionAnalysis.cultivationId()));
        }

        if (!feedbackDate.equals(environmentCompliance.date())) {
            throw new IllegalArgumentException("environmentCompliance의 날짜가 feedbackDate와 일치하지 않습니다: feedbackDate=%s, complianceDate=%s"
                    .formatted(feedbackDate, environmentCompliance.date()));
        }

        if (!feedbackDate.equals(notificationMetrics.date())) {
            throw new IllegalArgumentException("notificationMetrics의 날짜가 feedbackDate와 일치하지 않습니다: feedbackDate=%s, metricsDate=%s"
                    .formatted(feedbackDate, notificationMetrics.date()));
        }

        if (!Long.valueOf(mushroomReference.id())
                .equals(cultivationDetail.mushroomId())) {
            throw new IllegalArgumentException("버섯 참조정보 ID가 재배 상세정보의 mushroomId와 일치하지 않습니다: detailMushroomId=%s, referenceMushroomId=%s"
                    .formatted(cultivationDetail.mushroomId(), mushroomReference.id()));
        }

        if (!OWNER_ROLE.equals(cultivationDetail.myRole())) {
            throw new IllegalArgumentException("cultivationDetail의 myRole은 OWNER여야 합니다: cultivationId=%s, myRole=%s"
                    .formatted(cultivationId, cultivationDetail.myRole()));
        }
    }

    private static List<DataGeneratorThresholdResponse> normalizeCurrentThresholds(Long cultivationId, List<DataGeneratorThresholdResponse> source) {
        List<DataGeneratorThresholdResponse> normalized = new ArrayList<>(source.size());
        Set<CurrentThresholdKey> thresholdKeys = new HashSet<>();

        for (DataGeneratorThresholdResponse threshold : source) {
            if (threshold == null) {
                throw new IllegalArgumentException("currentThresholds에는 null 요소가 포함될 수 없습니다.");
            }

            if (!cultivationId.equals(threshold.cultivationId())) {
                throw new IllegalArgumentException("현재 임계값의 cultivationId가 Context와 일치하지 않습니다: contextCultivationId=%s, thresholdCultivationId=%s"
                        .formatted(cultivationId, threshold.cultivationId()));
            }

            if (isNullOrBlank(threshold.sensorType())) {
                throw new IllegalArgumentException("현재 임계값의 sensorType은 null 또는 공백일 수 없습니다.");
            }

            if (isNullOrBlank(threshold.unit())) {
                throw new IllegalArgumentException("현재 임계값의 unit은 null 또는 공백일 수 없습니다.");
            }

            CurrentThresholdKey thresholdKey = new CurrentThresholdKey(threshold.sensorType(), threshold.unit());

            if (!thresholdKeys.add(thresholdKey)) {
                throw new IllegalArgumentException("currentThresholds에 동일한 sensorType과 unit 조합이 중복되었습니다: sensorType=%s, unit=%s"
                        .formatted(threshold.sensorType(), threshold.unit()));
            }

            normalized.add(threshold);
        }

        normalized.sort(
                Comparator
                        .comparing(DataGeneratorThresholdResponse::sensorType)
                        .thenComparing(DataGeneratorThresholdResponse::unit)
        );

        return List.copyOf(normalized);
    }

    private static List<SensorChannelStatistics> normalizeSensorStatistics(
            Long cultivationId,
            List<SensorChannelStatistics> source
    ) {
        List<SensorChannelStatistics> normalized = new ArrayList<>(source.size());
        Set<SensorChannelKey> channelKeys = new HashSet<>();

        for (SensorChannelStatistics statistics : source) {
            if (statistics == null) {
                throw new IllegalArgumentException("sensorStatistics에는 null 요소가 포함될 수 없습니다.");
            }

            SensorChannelKey channelKey = statistics.channelKey();

            if (channelKey == null) {
                throw new IllegalArgumentException("sensorStatistics의 channelKey는 null일 수 없습니다.");
            }

            if (!cultivationId.equals(channelKey.cultivationId())) {
                throw new IllegalArgumentException("센서 통계 채널의 cultivationId가 Context와 일치하지 않습니다: contextCultivationId=%s, channelCultivationId=%s"
                        .formatted(cultivationId, channelKey.cultivationId()));
            }

            if (isNullOrBlank(channelKey.deviceEui())) {
                throw new IllegalArgumentException("센서 통계 채널의 deviceEui는 null 또는 공백일 수 없습니다.");
            }

            if (isNullOrBlank(channelKey.sensorType())) {
                throw new IllegalArgumentException("센서 통계 채널의 sensorType은 null 또는 공백일 수 없습니다.");
            }

            if (isNullOrBlank(channelKey.unit())) {
                throw new IllegalArgumentException("센서 통계 채널의 unit은 null 또는 공백일 수 없습니다.");
            }

            if (!channelKeys.add(channelKey)) {
                throw new IllegalArgumentException("sensorStatistics에 동일한 SensorChannelKey가 중복되었습니다: channelKey=%s"
                        .formatted(channelKey));
            }

            normalized.add(statistics);
        }

        normalized.sort(
                Comparator
                        .comparing((SensorChannelStatistics statistics) -> statistics.channelKey().deviceEui())
                        .thenComparing(statistics -> statistics.channelKey().sensorType())
                        .thenComparing(statistics -> statistics.channelKey().unit())
        );

        return List.copyOf(normalized);
    }

    private static MushroomReferenceInfoResponse validateAndCopyMushroomReference(MushroomReferenceInfoResponse source) {
        if (source.id() <= 0) {
            throw new IllegalArgumentException("mushroomReference.id는 0보다 커야 합니다.");
        }

        if (isNullOrBlank(source.mushroomNameKo())) {
            throw new IllegalArgumentException("mushroomNameKo는 null 또는 공백일 수 없습니다.");
        }

        if (isNullOrBlank(source.mushroomNameEn())) {
            throw new IllegalArgumentException("mushroomNameEn은 null 또는 공백일 수 없습니다.");
        }

        if (isNullOrBlank(source.mushroomScientificName())) {
            throw new IllegalArgumentException("mushroomScientificName은 null 또는 공백일 수 없습니다.");
        }

        if (source.thresholdInfoResponses() == null) {
            throw new IllegalArgumentException("thresholdInfoResponses는 null일 수 없습니다.");
        }

        List<MushroomReferenceThresholdInfoResponse> normalizedThresholds = new ArrayList<>(source.thresholdInfoResponses().size());
        Set<Long> thresholdIds = new HashSet<>();
        Set<MushroomThresholdKey> thresholdKeys = new HashSet<>();

        for (MushroomReferenceThresholdInfoResponse threshold : source.thresholdInfoResponses()) {
            if (threshold == null) {
                throw new IllegalArgumentException("thresholdInfoResponses에는 null 요소가 포함될 수 없습니다.");
            }

            if (threshold.id() == null || threshold.id() <= 0) {
                throw new IllegalArgumentException("버섯 참조 임계값 ID는 null이 아니며 0보다 커야 합니다.");
            }

            SensorTypeInfoResponse sensorType = threshold.sensorType();

            if (sensorType == null) {
                throw new IllegalArgumentException("버섯 참조 임계값의 sensorType은 null일 수 없습니다: thresholdId=%s".formatted(threshold.id()));
            }

            if (sensorType.id() <= 0) {
                throw new IllegalArgumentException("버섯 참조 임계값의 sensorType.id는 0보다 커야 합니다: thresholdId=%s".formatted(threshold.id()));
            }

            if (isNullOrBlank(sensorType.type())) {
                throw new IllegalArgumentException("버섯 참조 임계값의 sensorType.type은 null 또는 공백일 수 없습니다: thresholdId=%s".formatted(threshold.id()));
            }

            if (isNullOrBlank(sensorType.valueUnit())) {
                throw new IllegalArgumentException("버섯 참조 임계값의 sensorType.valueUnit은 null 또는 공백일 수 없습니다: thresholdId=%s".formatted(threshold.id()));
            }

            if (isNullOrBlank(threshold.thresholdType())) {
                throw new IllegalArgumentException("버섯 참조 임계값의 thresholdType은 null 또는 공백일 수 없습니다: thresholdId=%s".formatted(threshold.id()));
            }

            if (threshold.thresholdMin() == null) {
                throw new IllegalArgumentException("버섯 참조 임계값의 thresholdMin은 null일 수 없습니다: thresholdId=%s".formatted(threshold.id()));
            }

            if (threshold.thresholdMax() == null) {
                throw new IllegalArgumentException("버섯 참조 임계값의 thresholdMax는 null일 수 없습니다: thresholdId=%s".formatted(threshold.id()));
            }

            if (threshold.thresholdMin().compareTo(threshold.thresholdMax()) > 0) {
                throw new IllegalArgumentException("버섯 참조 임계값의 thresholdMin은 thresholdMax보다 클 수 없습니다: thresholdId=%s".formatted(threshold.id()));
            }

            if (!thresholdIds.add(threshold.id())) {
                throw new IllegalArgumentException("버섯 참조 임계값 ID가 중복되었습니다: thresholdId=%s".formatted(threshold.id()));
            }

            MushroomThresholdKey thresholdKey = new MushroomThresholdKey(sensorType.id(), threshold.thresholdType());

            if (!thresholdKeys.add(thresholdKey)) {
                throw new IllegalArgumentException("동일한 sensorType.id와 thresholdType 조합이 중복되었습니다: sensorTypeId=%s, thresholdType=%s"
                        .formatted(sensorType.id(), threshold.thresholdType())
                );
            }

            normalizedThresholds.add(threshold);
        }

        normalizedThresholds.sort(
                Comparator
                        .comparing((MushroomReferenceThresholdInfoResponse threshold) -> threshold.sensorType().type())
                        .thenComparing(threshold -> threshold.sensorType().valueUnit())
                        .thenComparing(MushroomReferenceThresholdInfoResponse::thresholdType)
                        .thenComparingLong(threshold -> threshold.sensorType().id())
                        .thenComparing(MushroomReferenceThresholdInfoResponse::id)
        );

        return new MushroomReferenceInfoResponse(
                source.id(),
                source.mushroomNameKo(),
                source.mushroomNameEn(),
                source.mushroomScientificName(),
                List.copyOf(normalizedThresholds)
        );
    }

    private static boolean isNullOrBlank(String value) {
        return value == null || value.isBlank();
    }

    private record CurrentThresholdKey(
            String sensorType,
            String unit
    ) {
    }

    private record MushroomThresholdKey(
            long sensorTypeId,
            String thresholdType
    ) {
    }
}
