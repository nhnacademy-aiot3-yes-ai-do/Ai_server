package site.yesaido.ai_server.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.yesaido.ai_server.dto.cultivation.ChangePhase;
import static org.assertj.core.api.Assertions.assertThat;

class PhaseTransitionTest {
    private final PhaseTransition phaseTransition = new PhaseTransition();

    @Test
    @DisplayName("병충해가 있으면 수확기로 전환 불가")
    void evaluate_disease_false() {
        ChangePhase result = phaseTransition.evaluate(1L, 10, 90.0, true);
        assertThat(result.changeP()).isFalse();
        assertThat(result.message()).contains("병충해");
    }

    @Test
    @DisplayName("재배 일수가 부족하면 수확기로 전환 불가 (느타리 기준 5일)")
    void evaluate_notEnoughDays_false() {
        ChangePhase result = phaseTransition.evaluate(1L, 3, 90.0, false);
        assertThat(result.changeP()).isFalse();
        assertThat(result.message()).contains("최소 5일");
    }

    @Test
    @DisplayName("환경 유지 점수가 낮으면 수확기로 전환 불가")
    void evaluate_lowScore_false() {
        ChangePhase result = phaseTransition.evaluate(1L, 6, 70.0, false);
        assertThat(result.changeP()).isFalse();
        assertThat(result.message()).contains("부족합니다");
    }

    @Test
    @DisplayName("모든 조건을 만족하면 수확기로 전환 가능")
    void evaluate_success() {
        ChangePhase result = phaseTransition.evaluate(1L, 6, 85.0, false);
        assertThat(result.changeP()).isTrue();
        assertThat(result.message()).contains("가능합니다");
    }

    @Test
    @DisplayName("버섯 종류별 최소 재배 일수 조건 분기문 테스트")
    void getMinRequiredDays_test() {
        // 양송이(4)는 10일 필요
        assertThat(phaseTransition.evaluate(4L, 9, 90.0, false).changeP()).isFalse();
        // 새송이(2) 및 null은 7일 필요
        assertThat(phaseTransition.evaluate(2L, 6, 90.0, false).changeP()).isFalse();
        assertThat(phaseTransition.evaluate(null, 6, 90.0, false).changeP()).isFalse();
    }
}
