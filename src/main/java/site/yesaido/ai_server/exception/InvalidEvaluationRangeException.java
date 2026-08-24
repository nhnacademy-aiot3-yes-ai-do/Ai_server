package site.yesaido.ai_server.exception;

import site.yesaido.common.exception.client.BadRequestException;

public class InvalidEvaluationRangeException extends BadRequestException {
    public InvalidEvaluationRangeException(String name, int value) {
        super("%s 값은 1에서 5 사이여야 합니다. 입력값 : %d".formatted(name, value));
    }
}
