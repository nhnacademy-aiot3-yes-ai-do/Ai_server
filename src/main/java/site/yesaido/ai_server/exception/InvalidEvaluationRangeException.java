package site.yesaido.ai_server.exception;

public class InvalidEvaluationRangeException extends RuntimeException {
    public InvalidEvaluationRangeException(String name, int value) {
        super("%s 값은 1에서 5 사이여야 합니다. 입력값 : %d".formatted(name, value));
    }
}
