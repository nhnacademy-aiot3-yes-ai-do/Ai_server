package site.yesaido.ai_server.exception;

import site.yesaido.common.exception.server.CustomServerException;
import site.yesaido.common.exception.server.ServerErrorLevel;

import java.time.Instant;

public class GeminiAllKeysExhaustedException extends CustomServerException {
    // 키 개수와 복구 시각만 넘겨받아 내부에서 메시지를 조립하는 생성자
    public GeminiAllKeysExhaustedException(int totalKeys, Instant earliestRecoveryTime) {
        super(String.format("모든 Gemini API Key(%d개)가 일일 할당량 소진으로 쿨다운 중입니다. (예상 복구 시각: %s)",
                totalKeys, earliestRecoveryTime), ServerErrorLevel.ERROR_LEVEL);
    }

    // 직접 문자열을 넘길 수도 있는 오버로딩 생성자
    public GeminiAllKeysExhaustedException(String message) {
        super(message, ServerErrorLevel.ERROR_LEVEL);
    }
}
