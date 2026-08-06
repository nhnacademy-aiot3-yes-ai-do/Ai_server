package site.yesaido.ai_server.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponse;
import static org.assertj.core.api.Assertions.*;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("401 Unauthorized 예외 처리 핸들러 테스트")
    void handleUnauthorizedAccessException() {
        UnauthorizedAccessException e = new UnauthorizedAccessException();
        ErrorResponse response = handler.handleUnauthorizedAccess(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).isEqualTo(e.getMessage());
    }

    @Test
    @DisplayName("404 MushDataNotFound 예외 처리 핸들러 테스트")
    void handleMushDataNotFoundTest() {
        MushDataNotFoundException exception = new MushDataNotFoundException(5L);
        ErrorResponse response = handler.handleMushDataNotFound(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).contains("5");
    }

    @Test
    @DisplayName("500 CsvLoadException 예외 처리 핸들러 테스트")
    void handleCsvLoadExceptionTest() {
        CsvLoadException exception = new CsvLoadException(new RuntimeException("파일 에러"));
        ErrorResponse response = handler.handleCsvLoadException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).contains("CSV 데이터를 읽어오는 중 문제가 발생했습니다.");
    }

    @Test
    @DisplayName("500  VectorDbException 예외 처리 핸들러 테스트")
    void handleVectorDbException() {
        VectorDbException exception = new VectorDbException(new RuntimeException("파일 에러"));
        ErrorResponse response = handler.handleVectorDbException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).contains("Vector DB 처리 중 오류가 발생했습니다.");
    }

    @Test
    @DisplayName("400 Bad Request 파라미터 타입 불일치 핸들러 테스트")
    void handleBadRequestTest() {
        IllegalArgumentException exception = new IllegalArgumentException("파라미터 타입 오류");
        ErrorResponse response = handler.handleBadRequest(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).isEqualTo("유효하지 않은 요청 데이터입니다.");
    }

    @Test
    @DisplayName("500 Unhandled Exception 모든 예외 처리 핸들러 테스트")
    void handleExceptionTest() {
        Exception exception = new Exception("알 수 없는 예외");
        ErrorResponse response = handler.handleException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).isEqualTo("서버 내부에 오류가 발생했습니다.");
    }
}