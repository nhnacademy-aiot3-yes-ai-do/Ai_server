package site.yesaido.ai_server.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import site.yesaido.ai_server.dto.ApiResponse;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 잘못된 요청 파라미터/타입 불일치 처리(400 BAD_REQUEST)
    // 예: /api/mushrooms/abc/guide 처럼 숫자에 문자를 입력한 경우
    @ExceptionHandler({MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception e){
        log.warn("[BAD_REQUEST] 유효하지 않은 파라미터 요청입니다. {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("유효하지 않은 요청 데이터입니다."));
    }

    // 권한 없음(401 UNAUTHORIZED)
    @ExceptionHandler(UnauthorizedAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorizedAccess(UnauthorizedAccessException e){
        log.warn("[UnauthorizedAccess] {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(e.getMessage()));
    }

    // 버섯 데이터 없음 예외(404 NOT_FOUND)
    @ExceptionHandler(MushDataNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleMushDataNotFound(MushDataNotFoundException e){
        log.warn("[MushDataNotFound] {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(e.getMessage()));

    }

    // CSV 로딩 실패 예외 (500 INTERNAL_SERVER_ERROR)
    @ExceptionHandler(CsvLoadException.class)
    public ResponseEntity<ApiResponse<Void>> handleCsvLoadException(CsvLoadException e){
        log.error("[CsvLoadException] CSV 데이터 로딩 실패", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(VectorDbException.class)
    public ResponseEntity<ApiResponse<Void>> handleVectorDbException(VectorDbException e){
        log.error("[VectorDbException] Vector DB 연동 중 오류 발생", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(e.getMessage()));
    }

    // 처리되지 않은 모든 예외 방어막 (500 INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e){
        log.error("[Unhandled Exception] 예외 발생", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("서버 내부에 오류가 발생했습니다."));
    }
}
