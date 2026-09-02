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
import java.util.Collections;
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
        return createSensorStat(cultivationId, "EUI-01", "TEMPERATURE", "°C", BigDecimal.valueOf(18.0), BigDecimal.valueOf(20.0), BigDecimal.valueOf(22.0));
    }

    private SensorChannelStatistics createSensorStat(Long cultivationId, String eui, String sensorType, String unit, BigDecimal min, BigDecimal avg, BigDecimal max) {
        SensorChannelKey channelKey = new SensorChannelKey(cultivationId, eui, sensorType, unit);
        return new SensorChannelStatistics(channelKey, min, avg, max, 96);
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
    @DisplayName("DailyFeedbackContext 정상 생성 및 정렬/방어적 복사 검증")
    void create_success() {
        Long cultivationId = 1L;
        LocalDate feedbackDate = LocalDate.of(2026, 9, 1);
        OffsetDateTime snapshotAt = OffsetDateTime.of(2026, 9, 1, 12, 0, 0, 0, SEOUL_OFFSET);

        DailyCultivationDetailResponse detail = createDetail(cultivationId, 10L, "OWNER");
        MushroomReferenceInfoResponse mushroomRef = createMushroomRef();

        DataGeneratorThresholdResponse threshold1 = new DataGeneratorThresholdResponse(cultivationId, "HUMIDITY", "%", BigDecimal.valueOf(70), BigDecimal.valueOf(90));
        DataGeneratorThresholdResponse threshold2 = new DataGeneratorThresholdResponse(cultivationId, "TEMPERATURE", "°C", BigDecimal.valueOf(18), BigDecimal.valueOf(24));
        List<DataGeneratorThresholdResponse> thresholds = List.of(threshold1, threshold2);

        SensorChannelStatistics stat1 = createSensorStat(cultivationId, "EUI-02", "HUMIDITY", "%", BigDecimal.valueOf(70), BigDecimal.valueOf(80), BigDecimal.valueOf(90));
        SensorChannelStatistics stat2 = createSensorStat(cultivationId, "EUI-01", "TEMPERATURE", "°C", BigDecimal.valueOf(18), BigDecimal.valueOf(20), BigDecimal.valueOf(22));
        List<SensorChannelStatistics> sensorStats = List.of(stat1, stat2);

        DailyEnvironmentCompliance compliance = createCompliance(cultivationId, feedbackDate);
        DailyNotificationMetrics metrics = createMetrics(cultivationId, feedbackDate);
        DailyVisionAnalysisSnapshot vision = DailyVisionAnalysisSnapshot.withoutPhoto(cultivationId);

        DailyFeedbackContext context = new DailyFeedbackContext(
                cultivationId, feedbackDate, snapshotAt, detail, mushroomRef,
                thresholds, sensorStats, compliance, metrics, vision
        );

        assertThat(context.cultivationId()).isEqualTo(cultivationId);
        assertThat(context.feedbackDate()).isEqualTo(feedbackDate);
        assertThat(context.currentThresholds()).hasSize(2);
        assertThat(context.currentThresholds().getFirst().sensorType()).isEqualTo("HUMIDITY");
        assertThat(context.sensorStatistics()).hasSize(2);
        assertThat(context.environmentCompliance()).isEqualTo(compliance);
        assertThat(context.notificationMetrics()).isEqualTo(metrics);
        assertThat(context.visionAnalysis()).isEqualTo(vision);
    }

    @Test
    @DisplayName("생성자 기본 유효성 검증 실패 케이스들")
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
        DailyEnvironmentCompliance mismatchedDateCompliance = createCompliance(cultivationId, LocalDate.of(2026, 9, 2));
        DailyEnvironmentCompliance mismatchedIdCompliance = createCompliance(999L, feedbackDate);
        DailyNotificationMetrics metrics = createMetrics(cultivationId, feedbackDate);
        DailyNotificationMetrics mismatchedDateMetrics = createMetrics(cultivationId, LocalDate.of(2026, 9, 2));
        DailyNotificationMetrics mismatchedIdMetrics = createMetrics(999L, feedbackDate);
        DailyVisionAnalysisSnapshot vision = DailyVisionAnalysisSnapshot.withoutPhoto(cultivationId);
        DailyVisionAnalysisSnapshot mismatchedVision = DailyVisionAnalysisSnapshot.withoutPhoto(999L);

        // 1. cultivationId null or <= 0
        assertThatThrownBy(() -> new DailyFeedbackContext(
                null, feedbackDate, seoulSnapshotAt, ownerDetail, mushroomRef,
                thresholds, sensorStats, compliance, metrics, vision
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DailyFeedbackContext(
                0L, feedbackDate, seoulSnapshotAt, ownerDetail, mushroomRef,
                thresholds, sensorStats, compliance, metrics, vision
        )).isInstanceOf(IllegalArgumentException.class);

        // 2. null fields
        assertThatThrownBy(() -> new DailyFeedbackContext(
                cultivationId, null, seoulSnapshotAt, ownerDetail, mushroomRef,
                thresholds, sensorStats, compliance, metrics, vision
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DailyFeedbackContext(
                cultivationId, feedbackDate, null, ownerDetail, mushroomRef,
                thresholds, sensorStats, compliance, metrics, vision
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DailyFeedbackContext(
                cultivationId, feedbackDate, seoulSnapshotAt, null, mushroomRef,
                thresholds, sensorStats, compliance, metrics, vision
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DailyFeedbackContext(
                cultivationId, feedbackDate, seoulSnapshotAt, ownerDetail, null,
                thresholds, sensorStats, compliance, metrics, vision
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DailyFeedbackContext(
                cultivationId, feedbackDate, seoulSnapshotAt, ownerDetail, mushroomRef,
                null, sensorStats, compliance, metrics, vision
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DailyFeedbackContext(
                cultivationId, feedbackDate, seoulSnapshotAt, ownerDetail, mushroomRef,
                thresholds, null, compliance, metrics, vision
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DailyFeedbackContext(
                cultivationId, feedbackDate, seoulSnapshotAt, ownerDetail, mushroomRef,
                thresholds, sensorStats, null, metrics, vision
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DailyFeedbackContext(
                cultivationId, feedbackDate, seoulSnapshotAt, ownerDetail, mushroomRef,
                thresholds, sensorStats, compliance, null, vision
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DailyFeedbackContext(
                cultivationId, feedbackDate, seoulSnapshotAt, ownerDetail, mushroomRef,
                thresholds, sensorStats, compliance, metrics, null
        )).isInstanceOf(IllegalArgumentException.class);

        // 3. UTC 오프셋
        assertThatThrownBy(() -> new DailyFeedbackContext(
                cultivationId, feedbackDate, utcSnapshotAt, ownerDetail, mushroomRef,
                thresholds, sensorStats, compliance, metrics, vision
        )).isInstanceOf(IllegalArgumentException.class);

        // 4. cultivationId 불일치
        assertThatThrownBy(() -> new DailyFeedbackContext(
                cultivationId, feedbackDate, seoulSnapshotAt, mismatchedIdDetail, mushroomRef,
                thresholds, sensorStats, compliance, metrics, vision
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DailyFeedbackContext(
                cultivationId, feedbackDate, seoulSnapshotAt, ownerDetail, mushroomRef,
                thresholds, sensorStats, mismatchedIdCompliance, metrics, vision
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DailyFeedbackContext(
                cultivationId, feedbackDate, seoulSnapshotAt, ownerDetail, mushroomRef,
                thresholds, sensorStats, compliance, mismatchedIdMetrics, vision
        )).isInstanceOf(IllegalArgumentException.class);

        // 5. OWNER 역할 아님
        assertThatThrownBy(() -> new DailyFeedbackContext(
                cultivationId, feedbackDate, seoulSnapshotAt, managerDetail, mushroomRef,
                thresholds, sensorStats, compliance, metrics, vision
        )).isInstanceOf(IllegalArgumentException.class);

        // 6. mushroomId 불일치
        assertThatThrownBy(() -> new DailyFeedbackContext(
                cultivationId, feedbackDate, seoulSnapshotAt, mismatchedMushroomDetail, mushroomRef,
                thresholds, sensorStats, compliance, metrics, vision
        )).isInstanceOf(IllegalArgumentException.class);

        // 7. compliance date 불일치
        assertThatThrownBy(() -> new DailyFeedbackContext(
                cultivationId, feedbackDate, seoulSnapshotAt, ownerDetail, mushroomRef,
                thresholds, sensorStats, mismatchedDateCompliance, metrics, vision
        )).isInstanceOf(IllegalArgumentException.class);

        // 8. metrics date 불일치
        assertThatThrownBy(() -> new DailyFeedbackContext(
                cultivationId, feedbackDate, seoulSnapshotAt, ownerDetail, mushroomRef,
                thresholds, sensorStats, compliance, mismatchedDateMetrics, vision
        )).isInstanceOf(IllegalArgumentException.class);

        // 9. vision cultivationId 불일치
        assertThatThrownBy(() -> new DailyFeedbackContext(
                cultivationId, feedbackDate, seoulSnapshotAt, ownerDetail, mushroomRef,
                thresholds, sensorStats, compliance, metrics, mismatchedVision
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("currentThresholds 및 sensorStatistics 정규화 예외 검증")
    void normalization_validationFailures() {
        Long cultivationId = 1L;
        LocalDate feedbackDate = LocalDate.of(2026, 9, 1);
        OffsetDateTime seoulSnapshotAt = OffsetDateTime.of(2026, 9, 1, 12, 0, 0, 0, SEOUL_OFFSET);
        DailyCultivationDetailResponse ownerDetail = createDetail(cultivationId, 10L, "OWNER");
        MushroomReferenceInfoResponse mushroomRef = createMushroomRef();
        DailyEnvironmentCompliance compliance = createCompliance(cultivationId, feedbackDate);
        DailyNotificationMetrics metrics = createMetrics(cultivationId, feedbackDate);
        DailyVisionAnalysisSnapshot vision = DailyVisionAnalysisSnapshot.withoutPhoto(cultivationId);

        List<SensorChannelStatistics> validStats = List.of(createSensorStat(cultivationId));
        List<DataGeneratorThresholdResponse> validThresholds = List.of(createCurrentThreshold(cultivationId));

        // thresholds null 요소
        List<DataGeneratorThresholdResponse> nullElemThresholds = Collections.singletonList(null);
        assertThatThrownBy(() -> new DailyFeedbackContext(
                cultivationId, feedbackDate, seoulSnapshotAt, ownerDetail, mushroomRef,
                nullElemThresholds, validStats, compliance, metrics, vision
        )).isInstanceOf(IllegalArgumentException.class);

        // thresholds 다른 cultivationId
        DataGeneratorThresholdResponse diffCultThreshold = new DataGeneratorThresholdResponse(999L, "TEMPERATURE", "°C", BigDecimal.valueOf(18), BigDecimal.valueOf(24));
        List<DataGeneratorThresholdResponse> diffCultThresholds = List.of(diffCultThreshold);
        assertThatThrownBy(() -> new DailyFeedbackContext(
                cultivationId, feedbackDate, seoulSnapshotAt, ownerDetail, mushroomRef,
                diffCultThresholds, validStats, compliance, metrics, vision
        )).isInstanceOf(IllegalArgumentException.class);

        // sensorStats null 요소
        List<SensorChannelStatistics> nullElemStats = Collections.singletonList(null);
        assertThatThrownBy(() -> new DailyFeedbackContext(
                cultivationId, feedbackDate, seoulSnapshotAt, ownerDetail, mushroomRef,
                validThresholds, nullElemStats, compliance, metrics, vision
        )).isInstanceOf(IllegalArgumentException.class);

        // sensorStats 다른 cultivationId
        SensorChannelStatistics diffCultStat = createSensorStat(999L);
        List<SensorChannelStatistics> diffCultStats = List.of(diffCultStat);
        assertThatThrownBy(() -> new DailyFeedbackContext(
                cultivationId, feedbackDate, seoulSnapshotAt, ownerDetail, mushroomRef,
                validThresholds, diffCultStats, compliance, metrics, vision
        )).isInstanceOf(IllegalArgumentException.class);

        // sensorStats 중복 키
        SensorChannelKey dupKey = new SensorChannelKey(cultivationId, "EUI-01", "TEMPERATURE", "°C");
        SensorChannelStatistics dupStat1 = new SensorChannelStatistics(dupKey, BigDecimal.valueOf(18), BigDecimal.valueOf(20), BigDecimal.valueOf(22), 96);
        SensorChannelStatistics dupStat2 = new SensorChannelStatistics(dupKey, BigDecimal.valueOf(19), BigDecimal.valueOf(21), BigDecimal.valueOf(23), 96);
        List<SensorChannelStatistics> dupStats = List.of(dupStat1, dupStat2);
        assertThatThrownBy(() -> new DailyFeedbackContext(
                cultivationId, feedbackDate, seoulSnapshotAt, ownerDetail, mushroomRef,
                validThresholds, dupStats, compliance, metrics, vision
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("mushroomReference 유효성 검증 예외 케이스들")
    void mushroomReference_validationFailures() {
        Long cultivationId = 1L;
        LocalDate feedbackDate = LocalDate.of(2026, 9, 1);
        OffsetDateTime seoulSnapshotAt = OffsetDateTime.of(2026, 9, 1, 12, 0, 0, 0, SEOUL_OFFSET);
        DailyCultivationDetailResponse ownerDetail = createDetail(cultivationId, 10L, "OWNER");
        List<DataGeneratorThresholdResponse> validThresholds = List.of(createCurrentThreshold(cultivationId));
        List<SensorChannelStatistics> validStats = List.of(createSensorStat(cultivationId));
        DailyEnvironmentCompliance compliance = createCompliance(cultivationId, feedbackDate);
        DailyNotificationMetrics metrics = createMetrics(cultivationId, feedbackDate);
        DailyVisionAnalysisSnapshot vision = DailyVisionAnalysisSnapshot.withoutPhoto(cultivationId);
        SensorTypeInfoResponse validSensorType = new SensorTypeInfoResponse(100L, "TEMPERATURE", "°C");

        // 1. mushroomReference id <= 0 or blank names
        MushroomReferenceInfoResponse invalidIdRef = new MushroomReferenceInfoResponse(0L, "느타리", "Oyster", "Pleurotus", List.of());
        assertThatThrownBy(() -> new DailyFeedbackContext(cultivationId, feedbackDate, seoulSnapshotAt, ownerDetail, invalidIdRef, validThresholds, validStats, compliance, metrics, vision)).isInstanceOf(IllegalArgumentException.class);
        MushroomReferenceInfoResponse blankKoRef = new MushroomReferenceInfoResponse(10L, " ", "Oyster", "Pleurotus", List.of());
        assertThatThrownBy(() -> new DailyFeedbackContext(cultivationId, feedbackDate, seoulSnapshotAt, ownerDetail, blankKoRef, validThresholds, validStats, compliance, metrics, vision)).isInstanceOf(IllegalArgumentException.class);
        MushroomReferenceInfoResponse blankEnRef = new MushroomReferenceInfoResponse(10L, "느타리", " ", "Pleurotus", List.of());
        assertThatThrownBy(() -> new DailyFeedbackContext(cultivationId, feedbackDate, seoulSnapshotAt, ownerDetail, blankEnRef, validThresholds, validStats, compliance, metrics, vision)).isInstanceOf(IllegalArgumentException.class);
        MushroomReferenceInfoResponse blankScRef = new MushroomReferenceInfoResponse(10L, "느타리", "Oyster", " ", List.of());
        assertThatThrownBy(() -> new DailyFeedbackContext(cultivationId, feedbackDate, seoulSnapshotAt, ownerDetail, blankScRef, validThresholds, validStats, compliance, metrics, vision)).isInstanceOf(IllegalArgumentException.class);

        // 2. thresholdInfoResponses null / null element
        MushroomReferenceInfoResponse nullThresholdsRef = new MushroomReferenceInfoResponse(10L, "느타리", "Oyster", "Pleurotus", null);
        assertThatThrownBy(() -> new DailyFeedbackContext(cultivationId, feedbackDate, seoulSnapshotAt, ownerDetail, nullThresholdsRef, validThresholds, validStats, compliance, metrics, vision)).isInstanceOf(IllegalArgumentException.class);
        MushroomReferenceInfoResponse nullElemRef = new MushroomReferenceInfoResponse(10L, "느타리", "Oyster", "Pleurotus", Collections.singletonList(null));
        assertThatThrownBy(() -> new DailyFeedbackContext(cultivationId, feedbackDate, seoulSnapshotAt, ownerDetail, nullElemRef, validThresholds, validStats, compliance, metrics, vision)).isInstanceOf(IllegalArgumentException.class);

        // 3. threshold id null or <= 0
        MushroomReferenceThresholdInfoResponse nullThId = new MushroomReferenceThresholdInfoResponse(null, validSensorType, "GROWTH", BigDecimal.TEN, BigDecimal.valueOf(20));
        MushroomReferenceInfoResponse nullThIdRef = new MushroomReferenceInfoResponse(10L, "느타리", "O", "P", List.of(nullThId));
        assertThatThrownBy(() -> new DailyFeedbackContext(cultivationId, feedbackDate, seoulSnapshotAt, ownerDetail, nullThIdRef, validThresholds, validStats, compliance, metrics, vision)).isInstanceOf(IllegalArgumentException.class);
        MushroomReferenceThresholdInfoResponse zeroThId = new MushroomReferenceThresholdInfoResponse(0L, validSensorType, "GROWTH", BigDecimal.TEN, BigDecimal.valueOf(20));
        MushroomReferenceInfoResponse zeroThIdRef = new MushroomReferenceInfoResponse(10L, "느타리", "O", "P", List.of(zeroThId));
        assertThatThrownBy(() -> new DailyFeedbackContext(cultivationId, feedbackDate, seoulSnapshotAt, ownerDetail, zeroThIdRef, validThresholds, validStats, compliance, metrics, vision)).isInstanceOf(IllegalArgumentException.class);

        // 4. sensorType validation
        MushroomReferenceThresholdInfoResponse nullSensorType = new MushroomReferenceThresholdInfoResponse(1L, null, "GROWTH", BigDecimal.TEN, BigDecimal.valueOf(20));
        MushroomReferenceInfoResponse nullSensorTypeRef = new MushroomReferenceInfoResponse(10L, "느타리", "O", "P", List.of(nullSensorType));
        assertThatThrownBy(() -> new DailyFeedbackContext(cultivationId, feedbackDate, seoulSnapshotAt, ownerDetail, nullSensorTypeRef, validThresholds, validStats, compliance, metrics, vision)).isInstanceOf(IllegalArgumentException.class);
        MushroomReferenceThresholdInfoResponse zeroSensorTypeId = new MushroomReferenceThresholdInfoResponse(1L, new SensorTypeInfoResponse(0L, "TEMP", "°C"), "GROWTH", BigDecimal.TEN, BigDecimal.valueOf(20));
        MushroomReferenceInfoResponse zeroSensorTypeIdRef = new MushroomReferenceInfoResponse(10L, "느타리", "O", "P", List.of(zeroSensorTypeId));
        assertThatThrownBy(() -> new DailyFeedbackContext(cultivationId, feedbackDate, seoulSnapshotAt, ownerDetail, zeroSensorTypeIdRef, validThresholds, validStats, compliance, metrics, vision)).isInstanceOf(IllegalArgumentException.class);
        MushroomReferenceThresholdInfoResponse blankSensorType = new MushroomReferenceThresholdInfoResponse(1L, new SensorTypeInfoResponse(100L, " ", "°C"), "GROWTH", BigDecimal.TEN, BigDecimal.valueOf(20));
        MushroomReferenceInfoResponse blankSensorTypeRef = new MushroomReferenceInfoResponse(10L, "느타리", "O", "P", List.of(blankSensorType));
        assertThatThrownBy(() -> new DailyFeedbackContext(cultivationId, feedbackDate, seoulSnapshotAt, ownerDetail, blankSensorTypeRef, validThresholds, validStats, compliance, metrics, vision)).isInstanceOf(IllegalArgumentException.class);
        MushroomReferenceThresholdInfoResponse blankSensorUnit = new MushroomReferenceThresholdInfoResponse(1L, new SensorTypeInfoResponse(100L, "TEMP", " "), "GROWTH", BigDecimal.TEN, BigDecimal.valueOf(20));
        MushroomReferenceInfoResponse blankSensorUnitRef = new MushroomReferenceInfoResponse(10L, "느타리", "O", "P", List.of(blankSensorUnit));
        assertThatThrownBy(() -> new DailyFeedbackContext(cultivationId, feedbackDate, seoulSnapshotAt, ownerDetail, blankSensorUnitRef, validThresholds, validStats, compliance, metrics, vision)).isInstanceOf(IllegalArgumentException.class);

        // 5. thresholdType blank, min/max null, min > max
        MushroomReferenceThresholdInfoResponse blankThType = new MushroomReferenceThresholdInfoResponse(1L, validSensorType, " ", BigDecimal.TEN, BigDecimal.valueOf(20));
        MushroomReferenceInfoResponse blankThTypeRef = new MushroomReferenceInfoResponse(10L, "느타리", "O", "P", List.of(blankThType));
        assertThatThrownBy(() -> new DailyFeedbackContext(cultivationId, feedbackDate, seoulSnapshotAt, ownerDetail, blankThTypeRef, validThresholds, validStats, compliance, metrics, vision)).isInstanceOf(IllegalArgumentException.class);
        MushroomReferenceThresholdInfoResponse nullMinTh = new MushroomReferenceThresholdInfoResponse(1L, validSensorType, "GROWTH", null, BigDecimal.valueOf(20));
        MushroomReferenceInfoResponse nullMinThRef = new MushroomReferenceInfoResponse(10L, "느타리", "O", "P", List.of(nullMinTh));
        assertThatThrownBy(() -> new DailyFeedbackContext(cultivationId, feedbackDate, seoulSnapshotAt, ownerDetail, nullMinThRef, validThresholds, validStats, compliance, metrics, vision)).isInstanceOf(IllegalArgumentException.class);
        MushroomReferenceThresholdInfoResponse nullMaxTh = new MushroomReferenceThresholdInfoResponse(1L, validSensorType, "GROWTH", BigDecimal.TEN, null);
        MushroomReferenceInfoResponse nullMaxThRef = new MushroomReferenceInfoResponse(10L, "느타리", "O", "P", List.of(nullMaxTh));
        assertThatThrownBy(() -> new DailyFeedbackContext(cultivationId, feedbackDate, seoulSnapshotAt, ownerDetail, nullMaxThRef, validThresholds, validStats, compliance, metrics, vision)).isInstanceOf(IllegalArgumentException.class);
        MushroomReferenceThresholdInfoResponse invalidRangeThreshold = new MushroomReferenceThresholdInfoResponse(1L, validSensorType, "GROWTH", BigDecimal.valueOf(30.0), BigDecimal.valueOf(20.0));
        MushroomReferenceInfoResponse invalidRangeRef = new MushroomReferenceInfoResponse(10L, "느타리", "O", "P", List.of(invalidRangeThreshold));
        assertThatThrownBy(() -> new DailyFeedbackContext(cultivationId, feedbackDate, seoulSnapshotAt, ownerDetail, invalidRangeRef, validThresholds, validStats, compliance, metrics, vision)).isInstanceOf(IllegalArgumentException.class);

        // 6. duplicate thresholdId & duplicate sensorTypeId + thresholdType
        MushroomReferenceThresholdInfoResponse th1 = new MushroomReferenceThresholdInfoResponse(1L, validSensorType, "GROWTH", BigDecimal.valueOf(18.0), BigDecimal.valueOf(25.0));
        MushroomReferenceThresholdInfoResponse th2 = new MushroomReferenceThresholdInfoResponse(1L, validSensorType, "HARVEST", BigDecimal.valueOf(18.0), BigDecimal.valueOf(25.0));
        MushroomReferenceInfoResponse dupThIdRef = new MushroomReferenceInfoResponse(10L, "느타리", "O", "P", List.of(th1, th2));
        assertThatThrownBy(() -> new DailyFeedbackContext(cultivationId, feedbackDate, seoulSnapshotAt, ownerDetail, dupThIdRef, validThresholds, validStats, compliance, metrics, vision)).isInstanceOf(IllegalArgumentException.class);

        MushroomReferenceThresholdInfoResponse th4 = new MushroomReferenceThresholdInfoResponse(2L, validSensorType, "GROWTH", BigDecimal.valueOf(18.0), BigDecimal.valueOf(25.0));
        MushroomReferenceInfoResponse dupTypeRef = new MushroomReferenceInfoResponse(10L, "느타리", "O", "P", List.of(th1, th4));
        assertThatThrownBy(() -> new DailyFeedbackContext(cultivationId, feedbackDate, seoulSnapshotAt, ownerDetail, dupTypeRef, validThresholds, validStats, compliance, metrics, vision)).isInstanceOf(IllegalArgumentException.class);
    }
}
