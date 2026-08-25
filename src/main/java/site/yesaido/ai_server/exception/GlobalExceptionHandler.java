package site.yesaido.ai_server.exception;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import site.yesaido.common.exception.client.*;
import site.yesaido.common.exception.server.*;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // [400 BAD_REQUEST] 요청 파라미터 값/범위 오류
    // 버섯 평가 점수에 1~5 범위를 벗어난 값을 넣은 경우
    @ExceptionHandler(BadRequestException.class)
    public ErrorResponse handleBadRequestException(BadRequestException e){
        log.warn("[BAD_REQUEST] {}", e.getMessage());
        return ErrorResponse.create(e, BadRequestException.getCode(), e.getMessage());
    }

    // [400 BAD_REQUEST] URL 경로 변수/쿼리 파라미터의 데이터 타입 불일치
    // 숫자 자리에 문자 전달 등 잘못된 파라미터 인입 시, 비즈니스 로직 진입 전 스프링(Spring MVC)이 자체적으로 던지는 예외이므로 직접 400으로 변환
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ErrorResponse handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("[BAD_REQUEST] 유효하지 않은 파라미터 요청입니다. {}", e.getMessage());
        return ErrorResponse.create(e, HttpStatus.BAD_REQUEST, "유효하지 않은 요청 데이터입니다.");
    }

    // [401 UNAUTHORIZED] 인증 실패
    // 로그인 토큰이 없거나 만료된 상태로 인증이 필요한 API를 호출한 경우
    @ExceptionHandler(UnauthorizedException.class)
    public ErrorResponse handleUnauthorizedException(UnauthorizedException e) {
        log.warn("[Unauthorized] {}", e.getMessage());
        return ErrorResponse.create(e, UnauthorizedException.getCode(), e.getMessage());
    }

    // [403 FORBIDDEN] 권한 없음 / 접근 거부
    // 타인의 재배지 데이터에 접근하거나 관리자 권한이 필요한 API에 일반 유저가 접근한 경우
    @ExceptionHandler(ForbiddenException.class)
    public ErrorResponse handleForbiddenException(ForbiddenException e) {
        log.warn("[Forbidden] {}", e.getMessage());
        return ErrorResponse.create(e, ForbiddenException.getCode(), e.getMessage());
    }

    // [404 NOT_FOUND] 요청한 리소스/데이터를 찾을 수 없음
    // 존재하지 않는 버섯 ID나 재배지 번호를 조회한 경우
    @ExceptionHandler(NotFoundException.class)
    public ErrorResponse handleNotFoundException(NotFoundException e) {
        log.warn("[NotFound] {}", e.getMessage());
        return ErrorResponse.create(e, NotFoundException.getCode(), e.getMessage());
    }

    // [409 CONFLICT] 데이터 충돌 / 중복 요청
    // 이미 등록된 센서나 중복된 고유 데이터를 다시 등록하려 할 때
    @ExceptionHandler(ConflictException.class)
    public ErrorResponse handleConflictException(ConflictException e) {
        log.warn("[Conflict] {}", e.getMessage());
        return ErrorResponse.create(e, ConflictException.getCode(), e.getMessage());
    }

    // 서버 내부 장애

    // [500 INTERNAL_SERVER_ERROR] 비즈니스 로직 상 예측된 서버 측 시스템/인프라 장애
    // ServerErrorLevel에 따라 WARN 또는 ERROR 레벨로 로그 분기
    @ExceptionHandler(CustomServerException.class)
    public ErrorResponse handleCustomServerException(CustomServerException e) {
        if (e.getErrorLevel().equals(ServerErrorLevel.WARN_LEVEL)) {
            log.warn("[CustomServerException] {}", e.getMessage());
        } else {
            log.error("[CustomServerException] {}", e.getMessage(), e);
        }
        return ErrorResponse.create(e, CustomServerException.getStatus(), e.getMessage());
    }


    // [502 BAD_GATEWAY] 외부 마이크로서비스(OpenFeign) 통신 실패
    // Cultivation_server나 Gateway 서버와의 네트워크 연결이 끊긴 경우
    @ExceptionHandler(FeignException.class)
    public ErrorResponse handleFeignException(FeignException e) {
        log.error("[Feign Communication Error] 외부 서버 통신 실패 (Status: {}): {}", e.status(), e.getMessage());
        return ErrorResponse.create(e, HttpStatus.BAD_GATEWAY, "외부 서비스 연결이 일시적으로 원활하지 않습니다. 잠시 후 다시 시도해 주세요.");
    }

    // 필수 요청 헤더가 누락된 경우 처리 (400 BAD_REQUEST)
    // 예: X-User-Id 없이 센서 검증 API를 호출한 경우
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ErrorResponse handleMissingRequestHeaderException(MissingRequestHeaderException e) {
        log.warn("[MissingRequestHeader] 필수 헤더 누락. header={}", e.getHeaderName());
        return ErrorResponse.create(e, HttpStatus.BAD_REQUEST, "필수 헤더가 누락되었습니다: " + e.getHeaderName());
    }

    // 처리되지 않은 모든 예외 방어막 (500 INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public ErrorResponse handleException(Exception e) {
        log.error("[Unhandled Exception] 예외 발생", e);
        return ErrorResponse.create(e, HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부에 오류가 발생했습니다.");
    }

    // 탭 아이콘 없어서 생기는 파비콘(favicion.ico) 에러 별도 처리해서 조용히 넘기
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResourceFound() {
        return ResponseEntity.notFound().build();
    }
}
