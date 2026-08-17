package site.yesaido.ai_server.vision.dto.response;

import java.util.List;

/**
 * 같은 품종으로 탐지된 버섯들을 하나로 묶어 분석한 결과이다.
 *
 * @param species 탐지된 버섯 품종 이름
 * @param speciesClassId 탐지 모델에서 사용하는 품종 클래스 ID
 * @param detectedCount 같은 품종으로 묶인 탐지 객체 수
 * @param detectionConfidence 같은 품종 탐지 결과 중 가장 높은 신뢰도
 * @param detectionConfidenceMin 같은 품종 탐지 결과 중 가장 낮은 신뢰도
 * @param healthStatus 건강 상태 ({@code HEALTHY}, {@code DISEASE_SUSPECTED}, {@code UNCERTAIN})
 * @param healthConfidence 건강 분류 결과의 가장 높은 확률. 낮은 탐지 신뢰도로 분류를 생략하면 {@code null}
 * @param healthyProbability 건강함으로 분류한 확률. 건강 분류를 생략하면 {@code null}
 * @param diseaseSuspectedProbability 질병 의심으로 분류한 확률. 건강 분류를 생략하면 {@code null}
 * @param bbox 같은 품종의 모든 탐지 영역을 합친 원본 이미지 좌표 ({@code [xMin, yMin, xMax, yMax]})
 * @param cropBbox 건강 분류용 여백을 포함한 원본 이미지 좌표 ({@code [xMin, yMin, xMax, yMax]})
 */
public record Result(
        String species,
        int speciesClassId,
        int detectedCount,
        double detectionConfidence,
        double detectionConfidenceMin,
        String healthStatus,
        Double healthConfidence,
        Double healthyProbability,
        Double diseaseSuspectedProbability,
        List<Integer> bbox,
        List<Integer> cropBbox
) {
}
