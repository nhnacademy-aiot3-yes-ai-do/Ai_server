package site.yesaido.ai_server.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.yesaido.ai_server.dto.client.cultivation.DailyCultivationDetailResponse;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("ConstantConditions")
class DailyCultivationDetailResponseTest {

    @Test
    @DisplayName("정상 생성 검증 (myRole null 허용, finishedAt null 허용)")
    void create_success() {
        LocalDateTime now = LocalDateTime.now();
        DailyCultivationDetailResponse response = new DailyCultivationDetailResponse(
                1L, "느타리 재배지", 10L, "GROWING", "GROWTH", null, now, null, now, now
        );

        assertThat(response.cultivationId()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("느타리 재배지");
        assertThat(response.myRole()).isNull();
        assertThat(response.finishedAt()).isNull();
    }

    @Test
    @DisplayName("유효성 검증 실패 케이스들 (ID <= 0, 공백 필드, null 날짜 등)")
    void create_validationFailures() {
        LocalDateTime now = LocalDateTime.now();

        // 1. cultivationId <= 0 / null
        assertThatThrownBy(() -> new DailyCultivationDetailResponse(null, "이름", 10L, "STATUS", "MODE", "OWNER", now, null, now, now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DailyCultivationDetailResponse(0L, "이름", 10L, "STATUS", "MODE", "OWNER", now, null, now, now))
                .isInstanceOf(IllegalArgumentException.class);

        // 2. name null / blank
        assertThatThrownBy(() -> new DailyCultivationDetailResponse(1L, "  ", 10L, "STATUS", "MODE", "OWNER", now, null, now, now))
                .isInstanceOf(IllegalArgumentException.class);

        // 3. mushroomId <= 0 / null
        assertThatThrownBy(() -> new DailyCultivationDetailResponse(1L, "이름", 0L, "STATUS", "MODE", "OWNER", now, null, now, now))
                .isInstanceOf(IllegalArgumentException.class);

        // 4. status, mode null / blank
        assertThatThrownBy(() -> new DailyCultivationDetailResponse(1L, "이름", 10L, " ", "MODE", "OWNER", now, null, now, now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DailyCultivationDetailResponse(1L, "이름", 10L, "STATUS", " ", "OWNER", now, null, now, now))
                .isInstanceOf(IllegalArgumentException.class);

        // 5. myRole blank
        assertThatThrownBy(() -> new DailyCultivationDetailResponse(1L, "이름", 10L, "STATUS", "MODE", " ", now, null, now, now))
                .isInstanceOf(IllegalArgumentException.class);

        // 6. startedAt, createdAt, updatedAt null
        assertThatThrownBy(() -> new DailyCultivationDetailResponse(1L, "이름", 10L, "STATUS", "MODE", "OWNER", null, null, now, now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DailyCultivationDetailResponse(1L, "이름", 10L, "STATUS", "MODE", "OWNER", now, null, null, now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DailyCultivationDetailResponse(1L, "이름", 10L, "STATUS", "MODE", "OWNER", now, null, now, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
