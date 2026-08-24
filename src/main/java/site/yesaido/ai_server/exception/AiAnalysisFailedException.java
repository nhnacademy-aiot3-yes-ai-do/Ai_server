package site.yesaido.ai_server.exception;

import site.yesaido.common.exception.server.CustomServerException;
import site.yesaido.common.exception.server.ServerErrorLevel;
// 유저 입력 오류가 아니라 서버 장애라 CustomServerException(500) 지정함
public class AiAnalysisFailedException extends CustomServerException {
    public AiAnalysisFailedException(String message) {
        super(message, ServerErrorLevel.ERROR_LEVEL);
    }

    public AiAnalysisFailedException(Long sensorTypeId) {
        super("센서(ID: %d)에 대한 AI 추천 임계값을 분석하지 못했습니다.".formatted(sensorTypeId), ServerErrorLevel.ERROR_LEVEL);
    }
}
