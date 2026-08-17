package site.yesaido.ai_server.storage.minio.exception;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import site.yesaido.common.exception.client.*;
import site.yesaido.common.exception.server.CustomServerException;
import site.yesaido.common.exception.server.ServerErrorLevel;

import java.util.Objects;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    //400 Bad Request
    @ExceptionHandler({BadRequestException.class})
    public ErrorResponse handleBadRequestException(BadRequestException e) {
        clientErrorPrint(e.getLogContent());
        return createResponseEntity(e, BadRequestException.getCode(), e.getMessage());
    }

    //401 Unauthorized
    @ExceptionHandler({UnauthorizedException.class})
    public ErrorResponse handleUnauthorizedException(UnauthorizedException e) {
        clientErrorPrint(e.getLogContent());
        return createResponseEntity(e, UnauthorizedException.getCode(), e.getMessage());
    }

    //403 Forbidden
    @ExceptionHandler({ForbiddenException.class})
    public ErrorResponse handleForbiddenExceptionException(ForbiddenException e) {
        clientErrorPrint(e.getLogContent());
        return createResponseEntity(e, ForbiddenException.getCode(), e.getMessage());
    }

    //404 Not Found
    @ExceptionHandler({NotFoundException.class})
    public ErrorResponse handleNotFoundExceptionException(NotFoundException e) {
        clientErrorPrint(e.getLogContent());
        return createResponseEntity(e, NotFoundException.getCode(), e.getMessage());
    }

    //409 Conflict
    @ExceptionHandler({ConflictException.class})
    public ErrorResponse handleConflictException(ConflictException e) {
        clientErrorPrint(e.getLogContent());
        return createResponseEntity(e, ConflictException.getCode(), e.getMessage());
    }

    //415 Unsupported Media Type
    @ExceptionHandler({UnsupportedMediaTypeException.class})
    public ErrorResponse handleUnsupportedMediaTypeException(UnsupportedMediaTypeException e) {
        clientErrorPrint(e.getLogContent());
        return createResponseEntity(e, UnsupportedMediaTypeException.getCode(), e.getMessage());
    }

    private void clientErrorPrint(String logContent) {
        log.info("{}", logContent);
    }

    //500 Custom Server Exception
    @ExceptionHandler({CustomServerException.class})
    public ErrorResponse handleServerException(CustomServerException e) {
        if(e.getErrorLevel().equals(ServerErrorLevel.WARN_LEVEL)) {
            log.warn("{}", e.getLogContent());
        } else {
            log.error("{}", e.getLogContent());
        }

        return createResponseEntity(e, CustomServerException.getStatus(), e.getMessage());
    }

    //500 Server Exception
    @ExceptionHandler({Exception.class})
    public ErrorResponse handleException(Exception e) {
        log.warn("{}", e.getMessage());
        return createResponseEntity(e, HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");
    }

    private ErrorResponse createResponseEntity(Exception e, HttpStatus status, String message) {
        return ErrorResponse.create(e, status, message);
    }


    // Spring
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ErrorResponse handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String defaultMessage = (fieldError != null) ? fieldError.getDefaultMessage() : null;
        String message = Objects.requireNonNullElse(defaultMessage, "잘못된 요청입니다.");

        return ErrorResponse.create(e, HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ErrorResponse handleMissingRequestHeaderException(MissingRequestHeaderException e) {
        return ErrorResponse.create(e, HttpStatus.BAD_REQUEST, "필수 헤더가 누락되었습니다: " + e.getHeaderName());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ErrorResponse handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        return ErrorResponse.create(e, HttpStatus.BAD_REQUEST, "사진 파일 크기는 8MB를 초과할 수 없습니다.");
    }

    @ExceptionHandler(FeignException.class)
    public ErrorResponse handleFeignException(FeignException e) {
        log.error("AI 서버 통신 실패 (Status: {}): {}", e.status(), e.getMessage());
        if(e.status() == 404){
            return createResponseEntity(e, HttpStatus.NOT_FOUND, "해당 버섯 가이드 정보를 찾을 수 없습니다.");
        } // 503 (Load balancer / Eureka 연결 실패 등) 또는 500 AI 서버 오류 시
        return ErrorResponse.create(e, HttpStatus.SERVICE_UNAVAILABLE, "AI 서비스 연결이 일시적으로 원활하지 않습니다. 잠시 후 다시 시도해 주세요.");
    }

}