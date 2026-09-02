package site.yesaido.ai_server.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.yesaido.ai_server.dto.client.cultivation.DailyCultivationDetailResponse;
import site.yesaido.ai_server.dto.client.mushroom_reference.MushroomReferenceInfoResponse;
import site.yesaido.ai_server.dto.client.mushroom_reference.MushroomReferenceThresholdInfoResponse;
import site.yesaido.ai_server.dto.client.mushroom_reference.SensorTypeInfoResponse;
import site.yesaido.ai_server.dto.client.sensor.snapshot.DataGeneratorThresholdResponse;
import site.yesaido.ai_server.dto.daily_feedback.DailyEnvironmentCompliance;
import site.yesaido.ai_server.dto.daily_feedback.DailyFeedbackContext;
import site.yesaido.ai_server.dto.daily_feedback.DailyNotificationMetrics;
import site.yesaido.ai_server.dto.daily_feedback.DailyVisionAnalysisSnapshot;
import site.yesaido.ai_server.dto.daily_feedback.SensorChannelKey;
import site.yesaido.ai_server.dto.daily_feedback.SensorChannelStatistics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("ConstantConditions")
class DailyFeedbackContextTest {

    private static final ZoneOffset SEOUL_OFFSET = ZoneOffset.ofHours(9);

    private DailyCultivationDetailResponse createDetail(Long cultivationId, Long mushroomId, String role) {
        LocalDateTime now = LocalDateTime.now();
        return new DailyCultivationDetailResponse(
                cultivationId, "느타리 재배지", mushroomId, "GROWING", "GROWTH",
                role, now, null, now, now
        );
    }

    private MushroomReferenceInfoResponse createMushroomRef() {
        SensorTypeInfoResponse sensorType = new SensorTypeInfoResponse(100L, "TEMPERATURE", "°C");
        MushroomReferenceThresholdInfoResponse thresholdInfo = new MushroomReferenceThresholdInfoResponse(
                1L, sensorType, "GROWTH", BigDecimal.valueOf(18.0), BigDecimal.valueOf(25.0)
        );
        return new MushroomReferenceInfoResponse(
                10L, "느타리버섯", "Oyster Mushroom", "Pleurotus ostreatus", List.of(thresholdInfo)
        );
    }

    private DataGeneratorThresholdResponse createCurrentThreshold(Long cultivationId) {
        return new DataGeneratorThresholdResponse(
                cultivationId, "TEMPERATURE", "°C", BigDecimal.valueOf(18.0), BigDecimal.valueOf(24.0)
        );
    }

    private SensorChannelStatistics createSensorStat(Long cultivationId) {
        SensorChannelKey channelKey = new SensorChannelKey(cultivationId, "EUI-01", "TEMPERATURE", "°C");
        return new SensorChannelStatistics(
                channelKey, BigDecimal.valueOf(18.0), BigDecimal.valueOf(20.0), BigDecimal.valueOf(22.0), 96
        );
    }

    private DailyEnvironmentCompliance createCompliance(Long cultivationId, LocalDate date) {
        return new DailyEnvironmentCompliance(
                cultivationId, date, BigDecimal.valueOf(90.0), BigDecimal.valueOf(80.0),
                BigDecimal.valueOf(85.0), BigDecimal.valueOf(95.0)
        );
    }

    private DailyNotificationMetrics createMetrics(Long cultivationId, LocalDate date) {
        return new DailyNotificationMetrics(
                cultivationId, date, 5L, 1L, 3L, 1L
        );
    }

    @Test
    @DisplayName("DailyFeedbackContext 정상 생성 및 필드 일치 검증")
    void create_success() {
        Long cultivationId = 1L;
        LocalDate feedbackDate = LocalDate.of(2026, 9, 1);
        OffsetDateTime snapshotAt = OffsetDateTime.of(2026, 9, 1, 12, 0, 0, 0, SEOUL_OFFSET);

        DailyCultivationDetailResponse detail = createDetail(cultivationId, 10L, "OWNER");
        MushroomReferenceInfoResponse mushroomRef = createMushroomRef();
        List<DataGeneratorThresholdResponse> thresholds = List.of(createCurrentThreshold(cultivationId));
        List<SensorChannelStatistics> sensorStats = List.of(createSensorStat(cultivationId));
        DailyEnvironmentCompliance compliance = createCompliance(cultivationId, feedbackDate);
        DailyNotificationMetrics metrics = createMetrics(cultivationId, feedbackDate);
        DailyVisionAnalysisSnapshot vision = DailyVisionAnalysisSnapshot.withoutPhoto(cultivationId);

        DailyFeedbackContext context = new DailyFeedbackContext(
                cultivationId, feedbackDate, snapshotAt, detail, mushroomRef,
                thresholds, sensorStats, compliance, metrics, vision
        );

        assertThat(context.cultivationId()).isEqualTo(cultivationId);
        assertThat(context.feedbackDate()).isEqualTo(feedbackDate);
        assertThat(context.currentThresholds()).hasSize(1);
        assertThat(context.sensorStatistics()).hasSize(1);
        assertThat(context.environmentCompliance()).isEqualTo(compliance);
        assertThat(context.notificationMetrics()).isEqualTo(metrics);
        assertThat(context.visionAnalysis()).isEqualTo(vision);
    }

    @Test
    @DisplayName("생성자 유효성 검증 실패 케이스들 (null, 타임존 불일치, 권한 불일치)")
    void create_validationFailures() {
        Long cultivationId = 1L;
        LocalDate feedbackDate = LocalDate.of(2026, 9, 1);
        OffsetDateTime utcSnapshotAt = OffsetDateTime.of(2026, 9, 1, 12, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime seoulSnapshotAt = OffsetDateTime.of(2026, 9, 1, 12, 0, 0, 0, SEOUL_OFFSET);

        DailyCultivationDetailResponse ownerDetail = createDetail(cultivationId, 10L, "OWNER");
        DailyCultivationDetailResponse managerDetail = createDetail(cultivationId, 10L, "MANAGER");
        DailyCultivationDetailResponse mismatchedIdDetail = createDetail(999L, 10L, "OWNER");
        DailyCultivationDetailResponse mismatchedMushroomDetail = createDetail(cultivationId, 999L, "OWNER");

        MushroomReferenceInfoResponse mushroomRef = createMushroomRef();
        List<DataGeneratorThresholdResponse> thresholds = List.of(createCurrentThreshold(cultivationId));
        List<SensorChannelStatistics> sensorStats = List.of(createSensorStat(cultivationId));
        DailyEnvironmentCompliance compliance = createCompliance(cultivationId, feedbackDate);
        DailyNotificationMetrics metrics = createMetrics(cultivationId, feedbackDate);
        DailyVisionAnalysisSnapshot vision = DailyVisionAnalysisSnapshot.withoutPhoto(cultivationId);

        // 1. cultivationId null
        assertThatThrownBy(() -> new DailyFeedbackContext(
                null, feedbackDate, seoulSnapshotAt, ownerDetail, mushroomRef,
                thresholds, sensorStats, compliance, metrics, vision
        )).isInstanceOf(IllegalArgumentException.class);

        // 2. UTC 오프셋 (Seoul +09:00 필수)
        assertThatThrownBy(() -> new DailyFeedbackContext(
                cultivationId, feedbackDate, utcSnapshotAt, ownerDetail, mushroomRef,
                thresholds, sensorStats, compliance, metrics, vision
        )).isInstanceOf(IllegalArgumentException.class);

        // 3. cultivationId 불일치
        assertThatThrownBy(() -> new DailyFeedbackContext(
                cultivationId, feedbackDate, seoulSnapshotAt, mismatchedIdDetail, mushroomRef,
                thresholds, sensorStats, compliance, metrics, vision
        )).isInstanceOf(IllegalArgumentException.class);

        // 4. OWNER 역할이 아닐 때
        assertThatThrownBy(() -> new DailyFeedbackContext(
                cultivationId, feedbackDate, seoulSnapshotAt, managerDetail, mushroomRef,
                thresholds, sensorStats, compliance, metrics, vision
        )).isInstanceOf(IllegalArgumentException.class);

        // 5. mushroomId 불일치
        assertThatThrownBy(() -> new DailyFeedbackContext(
                cultivationId, feedbackDate, seoulSnapshotAt, mismatchedMushroomDetail, mushroomRef,
                thresholds, sensorStats, compliance, metrics, vision
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
