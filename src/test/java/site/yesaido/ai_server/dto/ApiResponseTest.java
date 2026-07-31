package site.yesaido.ai_server.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

    @Test
    @DisplayName("에러 응답 생성 테스트")
    void errorResponseTest(){
        ApiResponse<String> response = ApiResponse.error("에러 메시지");
        assertThat(response.success()).isFalse();
        assertThat(response.message()).isEqualTo("에러 메시지");
        assertThat(response.data()).isNull();
    }
}
