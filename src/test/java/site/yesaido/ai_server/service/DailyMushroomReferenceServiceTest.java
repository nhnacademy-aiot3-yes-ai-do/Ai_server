package site.yesaido.ai_server.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.yesaido.ai_server.client.CultivationClient;
import site.yesaido.ai_server.dto.client.mushroom_reference.MushroomReferenceInfoListResponse;
import site.yesaido.ai_server.dto.client.mushroom_reference.MushroomReferenceInfoResponse;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@SuppressWarnings("ConstantConditions")
@ExtendWith(MockitoExtension.class)
class DailyMushroomReferenceServiceTest {

    @Mock
    private CultivationClient cultivationClient;

    @InjectMocks
    private DailyMushroomReferenceService service;

    @Test
    @DisplayName("정상 조회: 전체 버섯 참조정보 Map 반환")
    void fetchAllById_success() {
        MushroomReferenceInfoResponse info = new MushroomReferenceInfoResponse(
                1L, "양송이", "Button", "Agaricus bisporus", List.of()
        );
        given(cultivationClient.getMushroomReference())
                .willReturn(new MushroomReferenceInfoListResponse(List.of(info)));

        Map<Long, MushroomReferenceInfoResponse> result = service.fetchAllById();

        assertThat(result).containsKey(1L);
        assertThat(result.get(1L).mushroomNameKo()).isEqualTo("양송이");
    }

    @Test
    @DisplayName("fetchAllById: 응답이 null이거나 목록이 null일 때 IllegalStateException")
    void fetchAllById_nullResponse() {
        given(cultivationClient.getMushroomReference()).willReturn(null);
        assertThatThrownBy(() -> service.fetchAllById())
                .isInstanceOf(IllegalStateException.class);

        MushroomReferenceInfoListResponse nullListResponse = new MushroomReferenceInfoListResponse(null);
        given(cultivationClient.getMushroomReference()).willReturn(nullListResponse);
        assertThatThrownBy(() -> service.fetchAllById())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("fetchAllById: 목록 내 요소가 유효하지 않을 때 IllegalStateException")
    void fetchAllById_invalidElements() {
        // 1. null 요소 포함
        List<MushroomReferenceInfoResponse> nullElementList = Collections.singletonList(null);
        given(cultivationClient.getMushroomReference())
                .willReturn(new MushroomReferenceInfoListResponse(nullElementList));
        assertThatThrownBy(() -> service.fetchAllById())
                .isInstanceOf(IllegalStateException.class);

        // 2. ID <= 0
        MushroomReferenceInfoResponse invalidId = new MushroomReferenceInfoResponse(0L, "양송이", "Button", "Agaricus bisporus", List.of());
        given(cultivationClient.getMushroomReference())
                .willReturn(new MushroomReferenceInfoListResponse(List.of(invalidId)));
        assertThatThrownBy(() -> service.fetchAllById())
                .isInstanceOf(IllegalStateException.class);

        // 3. 한국어 이름 null / blank
        MushroomReferenceInfoResponse blankName = new MushroomReferenceInfoResponse(1L, "  ", "Button", "Agaricus bisporus", List.of());
        given(cultivationClient.getMushroomReference())
                .willReturn(new MushroomReferenceInfoListResponse(List.of(blankName)));
        assertThatThrownBy(() -> service.fetchAllById())
                .isInstanceOf(IllegalStateException.class);

        // 4. thresholdInfoResponses null
        MushroomReferenceInfoResponse nullThresholds = new MushroomReferenceInfoResponse(1L, "양송이", "Button", "Agaricus bisporus", null);
        given(cultivationClient.getMushroomReference())
                .willReturn(new MushroomReferenceInfoListResponse(List.of(nullThresholds)));
        assertThatThrownBy(() -> service.fetchAllById())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("fetchAllById: 중복된 mushroomId 수신 시 IllegalStateException")
    void fetchAllById_duplicateId() {
        MushroomReferenceInfoResponse info1 = new MushroomReferenceInfoResponse(
                1L, "양송이", "Button", "Agaricus bisporus", List.of()
        );
        MushroomReferenceInfoResponse info2 = new MushroomReferenceInfoResponse(
                1L, "양송이2", "Button2", "Agaricus bisporus", List.of()
        );

        List<MushroomReferenceInfoResponse> duplicateList = List.of(info1, info2);
        given(cultivationClient.getMushroomReference())
                .willReturn(new MushroomReferenceInfoListResponse(duplicateList));

        assertThatThrownBy(() -> service.fetchAllById())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("requireReference 정상 동작 및 예외")
    void requireReference() {
        MushroomReferenceInfoResponse info = new MushroomReferenceInfoResponse(
                1L, "양송이", "Button", "Agaricus bisporus", List.of()
        );
        Map<Long, MushroomReferenceInfoResponse> map = Map.of(1L, info);

        MushroomReferenceInfoResponse result = service.requireReference(map, 1L);
        assertThat(result).isEqualTo(info);

        Long notFoundId = 999L;
        assertThatThrownBy(() -> service.requireReference(map, notFoundId))
                .isInstanceOf(IllegalStateException.class);

        Long nullId = null;
        assertThatThrownBy(() -> service.requireReference(null, 1L))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.requireReference(map, nullId))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
