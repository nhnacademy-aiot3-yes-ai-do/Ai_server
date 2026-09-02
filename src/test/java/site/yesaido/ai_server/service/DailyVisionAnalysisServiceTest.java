package site.yesaido.ai_server.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.yesaido.ai_server.client.CultivationClient;
import site.yesaido.ai_server.dto.cultivation.DailyCultivationPhotoListResponse;
import site.yesaido.ai_server.dto.cultivation.DailyCultivationPhotoResponse;
import site.yesaido.ai_server.entity.GrowthRecord;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@SuppressWarnings("ConstantConditions")
@ExtendWith(MockitoExtension.class)
class DailyVisionAnalysisServiceTest {

    @Mock
    private CultivationClient cultivationClient;

    @Mock
    private VisionRelayService visionRelayService;

    @InjectMocks
    private DailyVisionAnalysisService service;

    @Test
    @DisplayName("fetchPhotosByCultivationId: 사진 목록 조회 성공")
    void fetchPhotos_success() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        DailyCultivationPhotoResponse photo = new DailyCultivationPhotoResponse(
                1L, 10L, "https://example.com/image.jpg", OffsetDateTime.now().plusHours(1)
        );
        DailyCultivationPhotoListResponse response = new DailyCultivationPhotoListResponse(date, List.of(photo));

        given(cultivationClient.getDailyCultivationPhotos(date)).willReturn(response);

        Map<Long, DailyCultivationPhotoResponse> result = service.fetchPhotosByCultivationId(date);

        assertThat(result).containsKey(1L);
        assertThat(result.get(1L).photoId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("fetchPhotosByCultivationId: 응답이 null이거나 날짜 불일치 시 IllegalStateException")
    void fetchPhotos_nullOrMismatchedResponse() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        LocalDate otherDate = LocalDate.of(2026, 9, 2);

        given(cultivationClient.getDailyCultivationPhotos(date)).willReturn(null);
        assertThatThrownBy(() -> service.fetchPhotosByCultivationId(date))
                .isInstanceOf(IllegalStateException.class);

        DailyCultivationPhotoListResponse mismatchedDateResponse = new DailyCultivationPhotoListResponse(otherDate, List.of());
        given(cultivationClient.getDailyCultivationPhotos(date)).willReturn(mismatchedDateResponse);
        assertThatThrownBy(() -> service.fetchPhotosByCultivationId(date))
                .isInstanceOf(IllegalStateException.class);

        DailyCultivationPhotoListResponse nullPhotosResponse = new DailyCultivationPhotoListResponse(date, null);
        given(cultivationClient.getDailyCultivationPhotos(date)).willReturn(nullPhotosResponse);
        assertThatThrownBy(() -> service.fetchPhotosByCultivationId(date))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("fetchPhotosByCultivationId: 목록 내 유효하지 않은 사진 정보 시 IllegalStateException")
    void fetchPhotos_invalidPhotoElements() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(1);

        // 1. null 요소
        List<DailyCultivationPhotoResponse> nullElementList = Collections.singletonList(null);
        given(cultivationClient.getDailyCultivationPhotos(date))
                .willReturn(new DailyCultivationPhotoListResponse(date, nullElementList));
        assertThatThrownBy(() -> service.fetchPhotosByCultivationId(date))
                .isInstanceOf(IllegalStateException.class);

        // 2. cultivationId <= 0
        DailyCultivationPhotoResponse invalidCultivationId = new DailyCultivationPhotoResponse(0L, 10L, "https://example.com/1.jpg", expiresAt);
        given(cultivationClient.getDailyCultivationPhotos(date))
                .willReturn(new DailyCultivationPhotoListResponse(date, List.of(invalidCultivationId)));
        assertThatThrownBy(() -> service.fetchPhotosByCultivationId(date))
                .isInstanceOf(IllegalStateException.class);

        // 3. photoId <= 0
        DailyCultivationPhotoResponse invalidPhotoId = new DailyCultivationPhotoResponse(1L, 0L, "https://example.com/1.jpg", expiresAt);
        given(cultivationClient.getDailyCultivationPhotos(date))
                .willReturn(new DailyCultivationPhotoListResponse(date, List.of(invalidPhotoId)));
        assertThatThrownBy(() -> service.fetchPhotosByCultivationId(date))
                .isInstanceOf(IllegalStateException.class);

        // 4. presignedUrl null/blank
        DailyCultivationPhotoResponse blankUrl = new DailyCultivationPhotoResponse(1L, 10L, "  ", expiresAt);
        given(cultivationClient.getDailyCultivationPhotos(date))
                .willReturn(new DailyCultivationPhotoListResponse(date, List.of(blankUrl)));
        assertThatThrownBy(() -> service.fetchPhotosByCultivationId(date))
                .isInstanceOf(IllegalStateException.class);

        // 5. expiresAt null
        DailyCultivationPhotoResponse nullExpires = new DailyCultivationPhotoResponse(1L, 10L, "https://example.com/1.jpg", null);
        given(cultivationClient.getDailyCultivationPhotos(date))
                .willReturn(new DailyCultivationPhotoListResponse(date, List.of(nullExpires)));
        assertThatThrownBy(() -> service.fetchPhotosByCultivationId(date))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("fetchPhotosByCultivationId: 중복 cultivationId 또는 중복 photoId 수신 시 IllegalStateException")
    void fetchPhotos_duplicateElements() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(1);

        DailyCultivationPhotoResponse photo1 = new DailyCultivationPhotoResponse(1L, 10L, "https://example.com/1.jpg", expiresAt);
        DailyCultivationPhotoResponse photo2 = new DailyCultivationPhotoResponse(1L, 11L, "https://example.com/2.jpg", expiresAt);

        List<DailyCultivationPhotoResponse> duplicatePhotos = List.of(photo1, photo2);
        DailyCultivationPhotoListResponse dupCultivationResponse = new DailyCultivationPhotoListResponse(date, duplicatePhotos);
        given(cultivationClient.getDailyCultivationPhotos(date)).willReturn(dupCultivationResponse);

        assertThatThrownBy(() -> service.fetchPhotosByCultivationId(date))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("analyzeIfPresent: 사진이 있는 경우 분석 실행 및 GrowthRecord 반환")
    void analyzeIfPresent_withPhoto() {
        DailyCultivationPhotoResponse photo = new DailyCultivationPhotoResponse(
                1L, 10L, "https://example.com/image.jpg", OffsetDateTime.now().plusHours(1)
        );
        Map<Long, DailyCultivationPhotoResponse> photosMap = Map.of(1L, photo);

        GrowthRecord mockGrowthRecord = mock(GrowthRecord.class);
        given(mockGrowthRecord.getCultivationId()).willReturn(1L);
        given(mockGrowthRecord.getCultivationPhotoId()).willReturn(10L);

        given(visionRelayService.analyzeAndSave(photo)).willReturn(mockGrowthRecord);

        Optional<GrowthRecord> result = service.analyzeIfPresent(photosMap, 1L);

        assertThat(result).isPresent();
        assertThat(result.get().getCultivationId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("analyzeIfPresent: 사진이 없는 경우 Optional.empty() 반환")
    void analyzeIfPresent_noPhoto() {
        Map<Long, DailyCultivationPhotoResponse> photosMap = Map.of();

        Optional<GrowthRecord> result = service.analyzeIfPresent(photosMap, 1L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("analyzeIfPresent: 분석 결과의 cultivationId 또는 photoId가 불일치하거나 null일 때 IllegalStateException")
    void analyzeIfPresent_mismatchedResult() {
        DailyCultivationPhotoResponse photo = new DailyCultivationPhotoResponse(
                1L, 10L, "https://example.com/image.jpg", OffsetDateTime.now().plusHours(1)
        );
        Map<Long, DailyCultivationPhotoResponse> photosMap = Map.of(1L, photo);
        Long cultivationId = 1L;

        // 1. null 결과
        given(visionRelayService.analyzeAndSave(photo)).willReturn(null);
        assertThatThrownBy(() -> service.analyzeIfPresent(photosMap, cultivationId))
                .isInstanceOf(IllegalStateException.class);

        // 2. cultivationId 불일치
        GrowthRecord mismatchedCultivationRecord = mock(GrowthRecord.class);
        given(mismatchedCultivationRecord.getCultivationId()).willReturn(999L);
        given(visionRelayService.analyzeAndSave(photo)).willReturn(mismatchedCultivationRecord);
        assertThatThrownBy(() -> service.analyzeIfPresent(photosMap, cultivationId))
                .isInstanceOf(IllegalStateException.class);

        // 3. photoId 불일치
        GrowthRecord mismatchedPhotoRecord = mock(GrowthRecord.class);
        given(mismatchedPhotoRecord.getCultivationId()).willReturn(1L);
        given(mismatchedPhotoRecord.getCultivationPhotoId()).willReturn(999L);
        given(visionRelayService.analyzeAndSave(photo)).willReturn(mismatchedPhotoRecord);
        assertThatThrownBy(() -> service.analyzeIfPresent(photosMap, cultivationId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("예외: 파라미터 null 검증")
    void invalidParams() {
        Map<Long, DailyCultivationPhotoResponse> emptyMap = Map.of();
        Long nullCultivationId = null;

        assertThatThrownBy(() -> service.fetchPhotosByCultivationId(null))
                .isInstanceOf(IllegalArgumentException.class);

        Long cultivationId = 1L;
        assertThatThrownBy(() -> service.analyzeIfPresent(null, cultivationId))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.analyzeIfPresent(emptyMap, nullCultivationId))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
