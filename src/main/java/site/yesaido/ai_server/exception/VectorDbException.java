package site.yesaido.ai_server.exception;

public class VectorDbException extends RuntimeException {
    public VectorDbException(Throwable cause) {
        super("Vector DB 처리 중 오류가 발생했습니다.", cause);
    }

    public VectorDbException(String message, Throwable cause) { super(message, cause);
  }
}
