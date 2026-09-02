package site.yesaido.ai_server.service;

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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

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

    @Test
    @DisplayName("collect: 모든 필수 데이터 정상 조립하여 DailyFeedbackContext 반환")
    void collect_success() {
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
        assertThat(context.cultivationId()).isEqualTo(1L);
        assertThat(context.feedbackDate()).isEqualTo(feedbackDate);
        assertThat(context.cultivationDetail().name()).isEqualTo("양송이 1호");
    }

    @Test
    @DisplayName("예외: 대상 경작지가 스냅샷 목록에 없는 경우 IllegalStateException")
    void collect_notInSnapshot() {
        LocalDate feedbackDate = LocalDate.now();
        DataGeneratorSnapshotResponse snapshot = new DataGeneratorSnapshotResponse(
                OffsetDateTime.now(ZoneOffset.ofHours(9)), List.of(), List.of()
        );

        given(targetResolver.resolveCultivationIds(snapshot)).willReturn(List.of(2L));

        Map<Long, MushroomReferenceInfoResponse> emptyRefMap = Map.of();
        Map<Long, DailyNotificationMetrics> emptyMetricsMap = Map.of();
        Map<Long, DailyCultivationPhotoResponse> emptyPhotoMap = Map.of();

        assertThatThrownBy(() -> collector.collect(
                feedbackDate, 1L, 100L, snapshot, emptyRefMap, emptyMetricsMap, emptyPhotoMap
        )).isInstanceOf(IllegalStateException.class);
    }
}
