package site.yesaido.ai_server.exception;

import site.yesaido.common.exception.server.CustomServerException;
import site.yesaido.common.exception.server.ServerErrorLevel;

public class VectorDbException extends CustomServerException {
    public VectorDbException(Throwable cause) {
        super("Vector DB 처리 중 오류가 발생했습니다.", cause, ServerErrorLevel.ERROR_LEVEL);
    }

    public VectorDbException(String message, Throwable cause) {
        super(message, cause, ServerErrorLevel.ERROR_LEVEL);
  }
}
