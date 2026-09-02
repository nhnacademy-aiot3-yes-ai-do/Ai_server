package site.yesaido.ai_server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.yesaido.ai_server.dto.client.cultivation.DailyCultivationDetailResponse;
import site.yesaido.ai_server.dto.client.mushroom_reference.MushroomReferenceInfoResponse;
import site.yesaido.ai_server.dto.client.sensor.snapshot.DataGeneratorSnapshotResponse;
import site.yesaido.ai_server.dto.cultivation.DailyCultivationPhotoResponse;
import site.yesaido.ai_server.dto.daily_feedback.DailyEnvironmentCompliance;
import site.yesaido.ai_server.dto.daily_feedback.DailyFeedbackContext;
import site.yesaido.ai_server.dto.daily_feedback.DailyNotificationMetrics;
import site.yesaido.ai_server.entity.GrowthRecord;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@SuppressWarnings("ConstantConditions")
@ExtendWith(MockitoExtension.class)
class DailyFeedbackContextCollectorTest {

    @Mock
    private DailyCultivationDetailService cultivationDetailService;

    @Mock
    private DailyMushroomReferenceService mushroomReferenceService;

    @Mock
    private DailyFeedbackTargetResolver targetResolver;

    @Mock
    private DailySensorStatisticsService sensorStatisticsService;

    @Mock
    private DailyEnvironmentComplianceService environmentComplianceService;

    @Mock
    private DailyVisionAnalysisService visionAnalysisService;

    @InjectMocks
    private DailyFeedbackContextCollector collector;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("collect: 사진이 있는 경우 Vision 분석 정상 조립하여 DailyFeedbackContext 반환")
    void collect_withPhoto_success() {
        LocalDate feedbackDate = LocalDate.of(2026, 9, 1);
        Long cultivationId = 1L;
        Long ownerUserId = 100L;

        DataGeneratorSnapshotResponse snapshot = new DataGeneratorSnapshotResponse(
                OffsetDateTime.now(ZoneOffset.ofHours(9)), List.of(), List.of()
        );
        DailyNotificationMetrics metrics = new DailyNotificationMetrics(
                1L, feedbackDate, 5L, 1L, 4L, 0L
        );
        LocalDateTime now = LocalDateTime.now();
        DailyCultivationDetailResponse detail = new DailyCultivationDetailResponse(
                1L, "양송이 1호", 10L, "GROWING", "GROWTH", "OWNER",
                now.minusDays(5), null, now.minusDays(5), now
        );
        MushroomReferenceInfoResponse mushroomRef = new MushroomReferenceInfoResponse(
                10L, "양송이", "Button", "Agaricus bisporus", List.of()
        );
        DailyEnvironmentCompliance compliance = new DailyEnvironmentCompliance(
                1L, feedbackDate, BigDecimal.valueOf(90), BigDecimal.valueOf(80), BigDecimal.valueOf(95), BigDecimal.valueOf(100)
        );

        GrowthRecord growthRecord = mock(GrowthRecord.class);
        given(growthRecord.getId()).willReturn(50L);
        given(growthRecord.getCultivationPhotoId()).willReturn(500L);
        ObjectNode analysisNode = objectMapper.createObjectNode().put("status", "HEALTHY");
        given(growthRecord.getAnalysisData()).willReturn(analysisNode);
        given(growthRecord.getAnalyzedAt()).willReturn(now);

        given(targetResolver.resolveCultivationIds(snapshot)).willReturn(List.of(1L));
        given(cultivationDetailService.fetch(1L, ownerUserId)).willReturn(detail);
        given(mushroomReferenceService.requireReference(anyMap(), eq(10L))).willReturn(mushroomRef);
        given(targetResolver.resolveCurrentThresholds(snapshot, 1L)).willReturn(List.of());
        given(sensorStatisticsService.collect(snapshot, 1L, ownerUserId)).willReturn(List.of());
        given(environmentComplianceService.fetch(1L, feedbackDate, ownerUserId)).willReturn(compliance);
        given(visionAnalysisService.analyzeIfPresent(anyMap(), eq(1L))).willReturn(Optional.of(growthRecord));

        DailyFeedbackContext context = collector.collect(
                feedbackDate, cultivationId, ownerUserId, snapshot,
                Map.of(10L, mushroomRef),
                Map.of(1L, metrics),
                Map.of()
        );

        assertThat(context).isNotNull();
        assertThat(context.cultivationId()).isEqualTo(1L);
        assertThat(context.visionAnalysis().hasVisionAnalysis()).isTrue();
        assertThat(context.visionAnalysis().growthRecordId()).isEqualTo(50L);
    }

    @Test
    @DisplayName("collect: 사진이 없는 경우 withoutPhoto 조립 성공")
    void collect_noPhoto_success() {
        LocalDate feedbackDate = LocalDate.of(2026, 9, 1);
        Long cultivationId = 1L;
        Long ownerUserId = 100L;

        DataGeneratorSnapshotResponse snapshot = new DataGeneratorSnapshotResponse(
                OffsetDateTime.now(ZoneOffset.ofHours(9)), List.of(), List.of()
        );
        DailyNotificationMetrics metrics = new DailyNotificationMetrics(
                1L, feedbackDate, 5L, 1L, 4L, 0L
        );
        LocalDateTime now = LocalDateTime.now();
        DailyCultivationDetailResponse detail = new DailyCultivationDetailResponse(
                1L, "양송이 1호", 10L, "GROWING", "GROWTH", "OWNER",
                now.minusDays(5), null, now.minusDays(5), now
        );
        MushroomReferenceInfoResponse mushroomRef = new MushroomReferenceInfoResponse(
                10L, "양송이", "Button", "Agaricus bisporus", List.of()
        );
        DailyEnvironmentCompliance compliance = new DailyEnvironmentCompliance(
                1L, feedbackDate, BigDecimal.valueOf(90), BigDecimal.valueOf(80), BigDecimal.valueOf(95), BigDecimal.valueOf(100)
        );

        given(targetResolver.resolveCultivationIds(snapshot)).willReturn(List.of(1L));
        given(cultivationDetailService.fetch(1L, ownerUserId)).willReturn(detail);
        given(mushroomReferenceService.requireReference(anyMap(), eq(10L))).willReturn(mushroomRef);
        given(targetResolver.resolveCurrentThresholds(snapshot, 1L)).willReturn(List.of());
        given(sensorStatisticsService.collect(snapshot, 1L, ownerUserId)).willReturn(List.of());
        given(environmentComplianceService.fetch(1L, feedbackDate, ownerUserId)).willReturn(compliance);
        given(visionAnalysisService.analyzeIfPresent(anyMap(), eq(1L))).willReturn(Optional.empty());

        DailyFeedbackContext context = collector.collect(
                feedbackDate, cultivationId, ownerUserId, snapshot,
                Map.of(10L, mushroomRef),
                Map.of(1L, metrics),
                Map.of()
        );

        assertThat(context).isNotNull();
        assertThat(context.visionAnalysis().hasVisionAnalysis()).isFalse();
    }

    @Test
    @DisplayName("예외: 파라미터 null 검증")
    void invalidParams() {
        LocalDate feedbackDate = LocalDate.of(2026, 9, 1);
        DataGeneratorSnapshotResponse snapshot = new DataGeneratorSnapshotResponse(
                OffsetDateTime.now(ZoneOffset.ofHours(9)), List.of(), List.of()
        );
        Map<Long, MushroomReferenceInfoResponse> refMap = Map.of();
        Map<Long, DailyNotificationMetrics> metricsMap = Map.of();
        Map<Long, DailyCultivationPhotoResponse> photoMap = Map.of();

        assertThatThrownBy(() -> collector.collect(null, 1L, 100L, snapshot, refMap, metricsMap, photoMap))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> collector.collect(feedbackDate, null, 100L, snapshot, refMap, metricsMap, photoMap))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> collector.collect(feedbackDate, 1L, null, snapshot, refMap, metricsMap, photoMap))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> collector.collect(feedbackDate, 1L, 100L, null, refMap, metricsMap, photoMap))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> collector.collect(feedbackDate, 1L, 100L, snapshot, null, metricsMap, photoMap))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> collector.collect(feedbackDate, 1L, 100L, snapshot, refMap, null, photoMap))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> collector.collect(feedbackDate, 1L, 100L, snapshot, refMap, metricsMap, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("예외: Notification 통계 누락 또는 불일치 시 IllegalStateException")
    void notificationMetrics_validationFailures() {
        LocalDate feedbackDate = LocalDate.of(2026, 9, 1);
        DataGeneratorSnapshotResponse snapshot = new DataGeneratorSnapshotResponse(
                OffsetDateTime.now(ZoneOffset.ofHours(9)), List.of(), List.of()
        );
        given(targetResolver.resolveCultivationIds(snapshot)).willReturn(List.of(1L));

        Map<Long, MushroomReferenceInfoResponse> refMap = Map.of();
        Map<Long, DailyCultivationPhotoResponse> photoMap = Map.of();

        // 1. 통계 키 누락
        Map<Long, DailyNotificationMetrics> missingKeyMap = Map.of();
        assertThatThrownBy(() -> collector.collect(feedbackDate, 1L, 100L, snapshot, refMap, missingKeyMap, photoMap))
                .isInstanceOf(IllegalStateException.class);

        // 2. 통계 null 값
        Map<Long, DailyNotificationMetrics> nullValueMap = Collections.singletonMap(1L, null);
        assertThatThrownBy(() -> collector.collect(feedbackDate, 1L, 100L, snapshot, refMap, nullValueMap, photoMap))
                .isInstanceOf(IllegalStateException.class);

        // 3. cultivationId 불일치
        DailyNotificationMetrics mismatchedIdMetric = new DailyNotificationMetrics(999L, feedbackDate, 5L, 1L, 4L, 0L);
        Map<Long, DailyNotificationMetrics> mismatchedIdMap = Map.of(1L, mismatchedIdMetric);
        assertThatThrownBy(() -> collector.collect(feedbackDate, 1L, 100L, snapshot, refMap, mismatchedIdMap, photoMap))
                .isInstanceOf(IllegalStateException.class);

        // 4. date 불일치
        DailyNotificationMetrics mismatchedDateMetric = new DailyNotificationMetrics(1L, LocalDate.of(2026, 9, 2), 5L, 1L, 4L, 0L);
        Map<Long, DailyNotificationMetrics> mismatchedDateMap = Map.of(1L, mismatchedDateMetric);
        assertThatThrownBy(() -> collector.collect(feedbackDate, 1L, 100L, snapshot, refMap, mismatchedDateMap, photoMap))
                .isInstanceOf(IllegalStateException.class);
    }
}
