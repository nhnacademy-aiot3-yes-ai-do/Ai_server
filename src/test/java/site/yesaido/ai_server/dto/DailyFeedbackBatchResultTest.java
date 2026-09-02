package site.yesaido.ai_server.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.yesaido.ai_server.dto.daily_feedback.DailyFeedbackBatchResult;
import site.yesaido.ai_server.dto.daily_feedback.DailyFeedbackBatchResult.CultivationResult;
import site.yesaido.ai_server.dto.daily_feedback.DailyFeedbackBatchResult.CultivationStatus;
import site.yesaido.ai_server.dto.daily_feedback.DailyFeedbackBatchResult.FailureStage;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DailyFeedbackBatchResultTest {

    @Test
    @DisplayName("정상 생성 및 집계 테스트: from() 팩토리 메서드")
    void createFrom_success() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        OffsetDateTime snapshotAt = OffsetDateTime.now(ZoneOffset.ofHours(9));

        CultivationResult createdResult = CultivationResult.created(1L);
        CultivationResult existingResult = CultivationResult.existing(2L);
        CultivationResult failedResult = CultivationResult.failed(
                3L, FailureStage.CULTIVATION_PROCESSING, new IllegalStateException("오류")
        );

        List<CultivationResult> results = List.of(createdResult, existingResult, failedResult);
        DailyFeedbackBatchResult batchResult = DailyFeedbackBatchResult.from(date, snapshotAt, results);

        assertThat(batchResult.targetCount()).isEqualTo(3);
        assertThat(batchResult.createdCount()).isEqualTo(1);
        assertThat(batchResult.existingCount()).isEqualTo(1);
        assertThat(batchResult.failedCount()).isEqualTo(1);
        assertThat(batchResult.results()).hasSize(3);
    }

    @Test
    @DisplayName("CultivationResult 상태 및 예외 타입 검증")
    void cultivationResult_validation() {
        CultivationResult created = CultivationResult.created(10L);
        assertThat(created.status()).isEqualTo(CultivationStatus.CREATED);
        assertThat(created.failureStage()).isNull();

        CultivationResult failed = CultivationResult.failed(
                20L, FailureStage.OWNER_RESOLUTION, new RuntimeException("fail")
        );
        assertThat(failed.status()).isEqualTo(CultivationStatus.FAILED);
        assertThat(failed.exceptionType()).isEqualTo("RuntimeException");
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    @DisplayName("예외: null 파라미터 검증")
    void invalidParams() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        OffsetDateTime snapshotAt = OffsetDateTime.now(ZoneOffset.ofHours(9));
        List<CultivationResult> emptyList = List.of();

        assertThatThrownBy(() -> DailyFeedbackBatchResult.from(null, snapshotAt, emptyList))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> DailyFeedbackBatchResult.from(date, null, emptyList))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> DailyFeedbackBatchResult.from(date, snapshotAt, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
