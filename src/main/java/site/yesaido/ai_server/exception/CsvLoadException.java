package site.yesaido.ai_server.exception;

import site.yesaido.common.exception.server.CustomServerException;
import site.yesaido.common.exception.server.ServerErrorLevel;

public class CsvLoadException extends CustomServerException {
    public CsvLoadException(Throwable cause) {
        super("CSV 데이터를 읽어오는 중 문제가 발생했습니다.", cause, ServerErrorLevel.ERROR_LEVEL);
    }
    public CsvLoadException(String message, Throwable cause) {
        super(message, cause, ServerErrorLevel.ERROR_LEVEL);
    }
}
