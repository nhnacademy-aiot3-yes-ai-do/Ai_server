package site.yesaido.ai_server.service;

import org.springframework.stereotype.Component;
import site.yesaido.ai_server.dto.cultivation.ChangePhase;

@Component
public class PhaseTransition {
    // 환경 유지율 통과 점수
    private static final double PASS_SCORE_THRESHOLD = 80.0;
    /**
     * 현재 재배 중인 버섯이 수확기로 넘어갈 수 있는지 판단
     * mushroomId 버섯 종류 ID (1:느타리, 2:새송이, 3:팽이, 4:양송이, 5:표고)
     * cultivationDays 재배 시작일로부터 경과한 일수
     * avgEnvironmentScore 현재까지 누적된 평균 환경 유지율 점수
     * hasDisease 병충해 발생 여부 (AI 이미지 분석 결과 등)
     * return PhaseTransitionResult (전환 가능 여부 + 메시지)
     */
    public ChangePhase evaluate(Long mushroomId, long cultivationDays, double environmentScoreAverage, boolean hasDisease){
        if(hasDisease){
            return new ChangePhase(false, "병충해가 발생하여 폐기가 필요합니다.");
        }
        int requiredDays = getMinRequiredDays(mushroomId);
        // 버섯 종류별 최소 재배 일수 확인
        if(cultivationDays < requiredDays){
            return new ChangePhase(false,
                    String.format("아직 재배 단계입니다. 수확기로 전환하려면 최소 %d일이 필요합니다.", requiredDays));
        }
        // 누적 평균 환경 유지 점수 확인(1일차~오늘)
        if(environmentScoreAverage < PASS_SCORE_THRESHOLD){
            return new ChangePhase(false, String.format("누적 평균 환경 유지 점수(%.1f)가 낮아 수확기 전환 조건이 부족합니다. (기준 : %.1f점)",
                    environmentScoreAverage, PASS_SCORE_THRESHOLD));
        }

        return new ChangePhase(true, "수확기로 전환 가능합니다.");
    }

    private int getMinRequiredDays(Long mushroomId){
        if (mushroomId == null) {
            return 7; // 기본값
        }

        return switch (mushroomId.intValue()) {
            case 1 -> 5; // 느타리버섯
            case 4 -> 10; // 양송이버섯
            default -> 7; // 새송이, 팽이, 표고버섯은 기본 7일로 설정
        };
    }

}
