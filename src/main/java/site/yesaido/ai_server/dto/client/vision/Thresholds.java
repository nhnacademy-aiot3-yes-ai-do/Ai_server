package site.yesaido.ai_server.dto.client.vision;

/**
 * Vision 서버가 이번 요청의 탐지와 건강 분류에 적용한 임계값이다.
 * @param detection 탐지 모델의 bbox를 결과 후보로 채택하는 최소 신뢰도
 * @param minDetectionConfidence 품종 그룹의 모든 탐지 결과가 건강 분류를 진행하기 위해 충족해야 하는 최소 신뢰도
 * @param healthUncertain 건강 상태를 확정하기 위해 분류 확률이 충족해야 하는 최소 신뢰도
 */
public record Thresholds( // 비전 모델 응답
        double detection,
        double minDetectionConfidence,
        double healthUncertain
) {
}
