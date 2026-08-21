package site.yesaido.ai_server.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.yesaido.ai_server.dto.ai.mush_summary.ApiResponse;

import static org.assertj.core.api.Assertions.*;

class ApiResponseTest {
    @Test
    @DisplayName("성공 응답 생성 테스트")
    void successResponseTest(){
        ApiResponse<String> response = ApiResponse.success("테스트");
        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("요청이 성공적으로 처리되었습니다.");
        assertThat(response.data()).isEqualTo("테스트");

    }
}
