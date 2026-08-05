package site.yesaido.ai_server.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 잘못된 요청 파라미터/타입 불일치 처리(400 BAD_REQUEST)
    // 예: /api/mushrooms/abc/guide 처럼 숫자에 문자를 입력한 경우
    @ExceptionHandler({MethodArgumentTypeMismatchException.class})
    public ErrorResponse handleBadRequest(Exception e){
        log.warn("[BAD_REQUEST] 유효하지 않은 파라미터 요청입니다. {}", e.getMessage());
        return ErrorResponse.create(e, HttpStatus.BAD_REQUEST, "유효하지 않은 요청 데이터입니다.");
    }

    // 권한 없음(401 UNAUTHORIZED)
    @ExceptionHandler(UnauthorizedAccessException.class)
    public ErrorResponse handleUnauthorizedAccess(UnauthorizedAccessException e){
        log.warn("[UnauthorizedAccess] {}", e.getMessage());
        return ErrorResponse.create(e, HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    // 버섯 데이터 없음 예외(404 NOT_FOUND)
    @ExceptionHandler(MushDataNotFoundException.class)
    public ErrorResponse handleMushDataNotFound(MushDataNotFoundException e){
        log.warn("[MushDataNotFound] {}", e.getMessage());
        return ErrorResponse.create(e, HttpStatus.NOT_FOUND, e.getMessage());
    }

    // CSV 로딩 실패 예외 (500 INTERNAL_SERVER_ERROR)
    @ExceptionHandler(CsvLoadException.class)
    public ErrorResponse handleCsvLoadException(CsvLoadException e){
        log.error("[CsvLoadException] CSV 데이터 로딩 실패", e);
        return ErrorResponse.create(e, HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }

    @ExceptionHandler(VectorDbException.class)
    public ErrorResponse handleVectorDbException(VectorDbException e){
        log.error("[VectorDbException] Vector DB 연동 중 오류 발생", e);
        return ErrorResponse.create(e, HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }

    // 처리되지 않은 모든 예외 방어막 (500 INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public ErrorResponse handleException(Exception e){
        log.error("[Unhandled Exception] 예외 발생", e);
        return ErrorResponse.create(e, HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부에 오류가 발생했습니다.");
    }

    // 탭 아이콘 없어서 생기는 파비콘(favicion.ico) 에러 별도 처리해서 조용히 넘기
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResourceFound() {
        return ResponseEntity.notFound().build();
    }
}
