package site.yesaido.ai_server.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.yesaido.ai_server.client.CultivationClient;
import site.yesaido.ai_server.dto.client.cultivation.DailyCultivationDetailResponse;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class DailyCultivationDetailServiceTest {

    @Mock
    private CultivationClient cultivationClient;

    @InjectMocks
    private DailyCultivationDetailService service;

    @Test
    @DisplayName("정상 조회: OWNER 역할과 일치하는 상세 응답 반환")
    void fetch_success() {
        LocalDateTime now = LocalDateTime.now();
        DailyCultivationDetailResponse response = new DailyCultivationDetailResponse(
                1L, "양송이 1호", 10L, "GROWING", "GROWTH", "OWNER",
                now.minusDays(5), null, now.minusDays(5), now
        );

        given(cultivationClient.getDailyCultivationDetail(100L, 1L)).willReturn(response);

        DailyCultivationDetailResponse result = service.fetch(1L, 100L);

        assertThat(result).isNotNull();
        assertThat(result.cultivationId()).isEqualTo(1L);
        assertThat(result.myRole()).isEqualTo("OWNER");
    }

    @Test
    @DisplayName("예외: 파라미터 유효성 검증 실패 시 IllegalArgumentException")
    void fetch_invalidParams() {
        assertThatThrownBy(() -> service.fetch(null, 100L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.fetch(1L, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("예외: 응답 ID 불일치 시 IllegalStateException")
    void fetch_mismatchedId() {
        LocalDateTime now = LocalDateTime.now();
        DailyCultivationDetailResponse response = new DailyCultivationDetailResponse(
                2L, "양송이 2호", 10L, "GROWING", "GROWTH", "OWNER",
                now.minusDays(5), null, now.minusDays(5), now
        );
        given(cultivationClient.getDailyCultivationDetail(100L, 1L)).willReturn(response);

        assertThatThrownBy(() -> service.fetch(1L, 100L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("예외: myRole이 OWNER가 아닐 시 IllegalStateException")
    void fetch_notOwner() {
        LocalDateTime now = LocalDateTime.now();
        DailyCultivationDetailResponse response = new DailyCultivationDetailResponse(
                1L, "양송이 1호", 10L, "GROWING", "GROWTH", "WORKER",
                now.minusDays(5), null, now.minusDays(5), now
        );
        given(cultivationClient.getDailyCultivationDetail(100L, 1L)).willReturn(response);

        assertThatThrownBy(() -> service.fetch(1L, 100L)).isInstanceOf(IllegalStateException.class);
    }
}