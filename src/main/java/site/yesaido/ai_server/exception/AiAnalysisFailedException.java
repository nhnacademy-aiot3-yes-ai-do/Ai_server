package site.yesaido.ai_server.exception;

public class AiAnalysisFailedException extends RuntimeException {
    public AiAnalysisFailedException(String message) {
        super(message);
    }
    public AiAnalysisFailedException(Long sensorTypeId) {
        super("센서(ID: %d)에 대한 AI 추천 임계값을 분석하지 못했습니다.".formatted(sensorTypeId));
    }
}
