package site.yesaido.ai_server.exception;

public class CsvLoadException extends RuntimeException {
    public CsvLoadException(Throwable cause) {
        super("CSV 데이터를 읽어오는 중 문제가 발생했습니다.", cause);
    }
}
