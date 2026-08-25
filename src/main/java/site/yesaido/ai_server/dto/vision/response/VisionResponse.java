package site.yesaido.ai_server.dto.vision.response;

import java.util.List;

/**
 * Vision 서버의 버섯 건강 분석 응답이다.
 *
 * @param analysisType 분석 응답 계약의 종류와 버전 ({@code MUSHROOM_HEALTH_CHECK_V1})
 * @param status 전체 분석 상태 (예: {@code SUCCESS}, {@code NO_MUSHROOM_DETECTED})
 * @param detectorModel 버섯 품종 탐지에 사용한 모델 이름
 * @param healthModel 버섯 건강 분류에 사용한 모델 이름
 * @param thresholds 이번 분석에 실제로 적용된 임계값
 * @param results 같은 품종끼리 묶어 분석한 결과 목록
 * @param warnings 미탐지, 낮은 신뢰도, 주의사항 등을 설명하는 메시지 목록
 */
public record VisionResponse(
        String analysisType,
        String status,
        String detectorModel,
        String healthModel,
        Thresholds thresholds,
        List<Result> results,
        List<String> warnings
) {
}
