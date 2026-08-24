package site.yesaido.ai_server.exception;

import site.yesaido.common.exception.client.ForbiddenException;

public class UnauthorizedAccessException extends ForbiddenException {
    public UnauthorizedAccessException() {
        super("접근 권한이 없습니다.");
    }
}
