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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

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
    @DisplayName("예외: 파라미터 null 검증")
    void invalidParams() {
        Map<Long, DailyCultivationPhotoResponse> emptyMap = Map.of();

        assertThatThrownBy(() -> service.fetchPhotosByCultivationId(null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.analyzeIfPresent(null, 1L))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.analyzeIfPresent(emptyMap, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}