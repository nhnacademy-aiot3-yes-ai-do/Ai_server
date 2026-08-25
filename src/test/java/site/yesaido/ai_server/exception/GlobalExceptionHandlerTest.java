package site.yesaido.ai_server.exception;

import feign.FeignException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import site.yesaido.common.exception.client.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("400 Bad Request - 유효하지 않은 평가 범위(InvalidEvaluationRangeException) 처리 테스트")
    void handleInvalidEvaluationRangeExceptionTest() {
        InvalidEvaluationRangeException exception = new InvalidEvaluationRangeException("difficultyLevel", 6);
        ErrorResponse response = handler.handleBadRequestException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).contains("difficultyLevel");
    }

    @Test
    @DisplayName("400 Bad Request - 파라미터 타입 불일치(MethodArgumentTypeMismatchException) 처리 테스트")
    void handleBadRequestTypeMismatchTest() {
        MethodArgumentTypeMismatchException exception = mock(MethodArgumentTypeMismatchException.class);
        when(exception.getMessage()).thenReturn("타입 변환 실패");

        ErrorResponse response = handler.handleMethodArgumentTypeMismatch(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).isEqualTo("유효하지 않은 요청 데이터입니다.");
    }

    @Test
    @DisplayName("401 Unauthorized - 인증 실패 예외 처리 테스트")
    void handleUnauthorizedExceptionTest() {
        UnauthorizedException e = new UnauthorizedException("인증이 필요합니다.");
        ErrorResponse response = handler.handleUnauthorizedException(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).isEqualTo("인증이 필요합니다.");
    }

    @Test
    @DisplayName("403 Forbidden - 접근 권한 없음(UnauthorizedAccessException) 처리 테스트")
    void handleForbiddenAccessExceptionTest() {
        UnauthorizedAccessException e = new UnauthorizedAccessException();
        ErrorResponse response = handler.handleForbiddenException(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).isEqualTo(e.getMessage());
    }

    @Test
    @DisplayName("404 Not Found - 버섯 데이터 없음(MushDataNotFoundException) 처리 테스트")
    void handleMushDataNotFoundTest() {
        MushDataNotFoundException exception = new MushDataNotFoundException(5L);
        ErrorResponse response = handler.handleNotFoundException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).contains("5");
    }

    @Test
    @DisplayName("409 Conflict - 중복/충돌 예외 처리 테스트")
    void handleConflictExceptionTest() {
        ConflictException exception = new ConflictException("이미 존재하는 데이터입니다.");
        ErrorResponse response = handler.handleConflictException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).isEqualTo("이미 존재하는 데이터입니다.");
    }

    @Test
    @DisplayName("500 Internal Server Error - AI 분석 실패(AiAnalysisFailedException) 처리 테스트")
    void handleAiAnalysisFailedExceptionTest() {
        AiAnalysisFailedException exception = new AiAnalysisFailedException(10L);
        ErrorResponse response = handler.handleCustomServerException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).contains("10");
    }

    @Test
    @DisplayName("500 Internal Server Error - CSV 로딩 실패(CsvLoadException) 처리 테스트")
    void handleCsvLoadExceptionTest() {
        CsvLoadException exception = new CsvLoadException(new RuntimeException("파일 읽기 에러"));
        ErrorResponse response = handler.handleCustomServerException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).contains("CSV 데이터를 읽어오는 중 문제가 발생했습니다.");
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
    @DisplayName("500 Internal Server Error - Vector DB 오류(VectorDbException) 처리 테스트")
    void handleVectorDbExceptionTest() {
        VectorDbException exception = new VectorDbException(new RuntimeException("DB 연결 끊김"));
        ErrorResponse response = handler.handleCustomServerException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).contains("Vector DB 처리 중 오류가 발생했습니다.");
    }

    @Test
    @DisplayName("502 Bad Gateway - Feign 외부 통신 실패(FeignException) 처리 테스트")
    void handleFeignExceptionTest() {
        FeignException exception = mock(FeignException.class);
        when(exception.status()).thenReturn(500);
        when(exception.getMessage()).thenReturn("Connection refused");

        ErrorResponse response = handler.handleFeignException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).contains("외부 서비스 연결이 일시적으로 원활하지 않습니다.");
    }

    @Test
    @DisplayName("500 Internal Server Error - 처리되지 않은 모든 예외(Exception) 처리 테스트")
    void handleUnhandledExceptionTest() {
        Exception exception = new Exception("예상치 못한 버그");
        ErrorResponse response = handler.handleException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).isEqualTo("서버 내부에 오류가 발생했습니다.");
    }

    @Test
    @DisplayName("404 Not Found - 파비콘 및 정적 리소스 없음(NoResourceFoundException) 처리 테스트")
    void handleNoResourceFoundTest() {
        var response = handler.handleNoResourceFound();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

}