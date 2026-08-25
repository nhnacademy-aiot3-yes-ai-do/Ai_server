package site.yesaido.ai_server.exception;

import feign.FeignException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MissingRequestHeaderException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    @DisplayName("500 VectorDbException 예외 처리 핸들러 테스트")
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
    @DisplayName("필수 요청 헤더가 누락되면 400을 반환한다")
    void handleMissingRequestHeaderExceptionTest() {
        MissingRequestHeaderException exception = mock(MissingRequestHeaderException.class);

        when(exception.getHeaderName()).thenReturn("X-User-Id");

        ErrorResponse response = handler.handleMissingRequestHeaderException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).isEqualTo("필수 헤더가 누락되었습니다: X-User-Id");
    }

    @Test
    @DisplayName("외부 서비스 호출 실패 시 503을 반환하고 내부 오류를 노출하지 않는다")
    void handleFeignExceptionTest() {
        FeignException exception = mock(FeignException.class);

        when(exception.status()).thenReturn(500);
        when(exception.getMessage()).thenReturn("https://minio.example/image?X-Amz-Signature=secret");

        ErrorResponse response = handler.handleFeignException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).isEqualTo("외부 서비스 연결이 일시적으로 원활하지 않습니다.").doesNotContain("X-Amz-Signature");
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

    @Test
    @DisplayName("400 InvalidEvaluationRange 예외 처리 핸들러 테스트")
    void handleInvalidEvaluationRangeExceptionTest() {
        InvalidEvaluationRangeException exception = new InvalidEvaluationRangeException("difficultyLevel", 6);
        ErrorResponse response = handler.handleInvalidEvaluationRangeException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).contains("difficultyLevel");
    }
}