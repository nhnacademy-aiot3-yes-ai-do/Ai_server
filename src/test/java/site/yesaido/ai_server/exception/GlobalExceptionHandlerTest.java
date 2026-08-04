package site.yesaido.ai_server.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import site.yesaido.ai_server.dto.ApiResponse;

import static org.assertj.core.api.Assertions.*;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("401 Unauthorized 예외 처리 핸들러 테스트")
    void handleUnauthorizedAccessException() {
        UnauthorizedAccessException e = new UnauthorizedAccessException();
        ResponseEntity<ApiResponse<Void>> response = handler.handleUnauthorizedAccess(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).isEqualTo("접근 권한이 없습니다.");
    }

    @Test
    @DisplayName("404 MushDataNotFound 예외 처리 핸들러 테스트")
    void handleMushDataNotFoundTest() {
        MushDataNotFoundException exception = new MushDataNotFoundException(5L);
        ResponseEntity<ApiResponse<Void>> response = handler.handleMushDataNotFound(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).contains("5");
    }

    @Test
    @DisplayName("500 CsvLoadException 예외 처리 핸들러 테스트")
    void handleCsvLoadExceptionTest() {
        CsvLoadException exception = new CsvLoadException(new RuntimeException("파일 에러"));
        ResponseEntity<ApiResponse<Void>> response = handler.handleCsvLoadException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
    }

    @Test
    @DisplayName("500  VectorDbException 예외 처리 핸들러 테스트")
    void handleVectorDbException() {
        VectorDbException exception = new VectorDbException(new RuntimeException("파일 에러"));
        ResponseEntity<ApiResponse<Void>> response = handler.handleVectorDbException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
    }

    @Test
    @DisplayName("400 Bad Request 파라미터 타입 불일치 핸들러 테스트")
    void handleBadRequestTest() {
        IllegalArgumentException exception = new IllegalArgumentException("파라미터 타입 오류");
        ResponseEntity<ApiResponse<Void>> response = handler.handleBadRequest(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).isEqualTo("유효하지 않은 요청 데이터입니다.");
    }

    @Test
    @DisplayName("500 Unhandled Exception 모든 예외 처리 핸들러 테스트")
    void handleExceptionTest() {
        Exception exception = new Exception("알 수 없는 예외");
        ResponseEntity<ApiResponse<Void>> response = handler.handleException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).isEqualTo("서버 내부에 오류가 발생했습니다.");
    }
}