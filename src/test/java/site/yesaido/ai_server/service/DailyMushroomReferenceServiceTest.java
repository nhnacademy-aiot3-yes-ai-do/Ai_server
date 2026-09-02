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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

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
    @DisplayName("requireReference 정상 동작 및 예외")
    void requireReference() {
        MushroomReferenceInfoResponse info = new MushroomReferenceInfoResponse(
                1L, "양송이", "Button", "Agaricus bisporus", List.of()
        );
        Map<Long, MushroomReferenceInfoResponse> map = Map.of(1L, info);

        MushroomReferenceInfoResponse result = service.requireReference(map, 1L);
        assertThat(result).isEqualTo(info);

        assertThatThrownBy(() -> service.requireReference(map, 999L))
                .isInstanceOf(IllegalStateException.class);
    }
}