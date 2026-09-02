package site.yesaido.ai_server.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.yesaido.ai_server.client.CultivationClient;
import site.yesaido.ai_server.dto.client.cultivation.CultivationMemberListResponse;
import site.yesaido.ai_server.dto.client.cultivation.CultivationMemberResponse;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CultivationOwnerServiceTest {

    @Mock
    private CultivationClient cultivationClient;

    @InjectMocks
    private CultivationOwnerService cultivationOwnerService;

    @Test
    @DisplayName("정상 조회: 단일 OWNER인 경우 해당 userId 반환")
    void findOwnerUserId_success() {
        LocalDateTime now = LocalDateTime.now();
        CultivationMemberResponse owner = new CultivationMemberResponse(1L, 100L, "농장주", "OWNER", now);
        CultivationMemberResponse worker = new CultivationMemberResponse(2L, 101L, "직원", "WORKER", now);
        CultivationMemberListResponse response = new CultivationMemberListResponse(List.of(owner, worker));

        given(cultivationClient.getCultivationMembers(1L, 0L, "ADMIN")).willReturn(response);

        Long ownerUserId = cultivationOwnerService.findOwnerUserId(1L);

        assertThat(ownerUserId).isEqualTo(100L);
    }

    @Test
    @DisplayName("예외: cultivationId가 null이거나 0 이하인 경우 IllegalArgumentException")
    void findOwnerUserId_invalidCultivationId() {
        assertThatThrownBy(() -> cultivationOwnerService.findOwnerUserId(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cultivationOwnerService.findOwnerUserId(0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("예외: CultivationClient 응답이 null인 경우 IllegalStateException")
    void findOwnerUserId_nullResponse() {
        given(cultivationClient.getCultivationMembers(1L, 0L, "ADMIN")).willReturn(null);

        assertThatThrownBy(() -> cultivationOwnerService.findOwnerUserId(1L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("예외: OWNER가 2명 이상인 경우 IllegalStateException")
    void findOwnerUserId_multipleOwners() {
        LocalDateTime now = LocalDateTime.now();
        CultivationMemberResponse owner1 = new CultivationMemberResponse(1L, 100L, "농장주1", "OWNER", now);
        CultivationMemberResponse owner2 = new CultivationMemberResponse(2L, 101L, "농장주2", "OWNER", now);
        CultivationMemberListResponse response = new CultivationMemberListResponse(List.of(owner1, owner2));

        given(cultivationClient.getCultivationMembers(1L, 0L, "ADMIN")).willReturn(response);

        assertThatThrownBy(() -> cultivationOwnerService.findOwnerUserId(1L))
                .isInstanceOf(IllegalStateException.class);
    }
}