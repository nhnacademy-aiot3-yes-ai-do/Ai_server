package site.yesaido.ai_server.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.yesaido.ai_server.dto.client.cultivation.CultivationMemberResponse;
import site.yesaido.ai_server.dto.client.cultivation.DailyCultivationDetailResponse;
import site.yesaido.ai_server.dto.cultivation.DailyCultivationPhotoListResponse;
import site.yesaido.ai_server.dto.cultivation.DailyCultivationPhotoResponse;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("ConstantConditions")
class ClientCultivationDtoTest {

    @Test
    @DisplayName("CultivationMemberResponse 생성 및 필드 검증")
    void memberResponse() {
        LocalDateTime now = LocalDateTime.now();
        CultivationMemberResponse member = new CultivationMemberResponse(10L, 100L, "홍길동", "OWNER", now);

        assertThat(member.memberId()).isEqualTo(10L);
        assertThat(member.userId()).isEqualTo(100L);
        assertThat(member.nickname()).isEqualTo("홍길동");
        assertThat(member.role()).isEqualTo("OWNER");
        assertThat(member.joinedAt()).isEqualTo(now);

        assertThatThrownBy(() -> new CultivationMemberResponse(null, 100L, "홍길동", "OWNER", now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CultivationMemberResponse(10L, null, "홍길동", "OWNER", now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("DailyCultivationDetailResponse 생성 및 필드 검증")
    void detailResponse() {
        LocalDateTime now = LocalDateTime.now();
        DailyCultivationDetailResponse detail = new DailyCultivationDetailResponse(
                1L, "양송이 1호", 100L, "GROWING", "GROWTH", "OWNER",
                now.minusDays(5), null, now.minusDays(5), now
        );

        assertThat(detail.cultivationId()).isEqualTo(1L);
        assertThat(detail.name()).isEqualTo("양송이 1호");
        assertThat(detail.mushroomId()).isEqualTo(100L);
        assertThat(detail.status()).isEqualTo("GROWING");
        assertThat(detail.mode()).isEqualTo("GROWTH");
        assertThat(detail.myRole()).isEqualTo("OWNER");
    }

    @Test
    @DisplayName("DailyCultivationPhotoResponse 및 ListResponse 검증")
    void photoResponse() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(1);
        DailyCultivationPhotoResponse photo = new DailyCultivationPhotoResponse(
                1L, 100L, "https://example.com/photo.jpg", expiresAt
        );

        assertThat(photo.cultivationId()).isEqualTo(1L);
        assertThat(photo.photoId()).isEqualTo(100L);
        assertThat(photo.presignedUrl()).isEqualTo("https://example.com/photo.jpg");
        assertThat(photo.expiresAt()).isEqualTo(expiresAt);

        DailyCultivationPhotoListResponse listResponse = new DailyCultivationPhotoListResponse(
                date, List.of(photo)
        );
        // 💡 targetDate()로 수정
        assertThat(listResponse.targetDate()).isEqualTo(date);
        assertThat(listResponse.photos()).hasSize(1);
    }
}
