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
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("ConstantConditions")
class DailyFeedbackBatchResultTest {

    private static final ZoneOffset SEOUL_OFFSET = ZoneOffset.ofHours(9);

    @Test
    @DisplayName("정상 생성 및 집계 테스트: from() 팩토리 메서드")
    void createFrom_success() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        OffsetDateTime snapshotAt = OffsetDateTime.of(2026, 9, 1, 12, 0, 0, 0, SEOUL_OFFSET);

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
    @DisplayName("CultivationResult 상태, 예외 타입 및 유효성 검증")
    void cultivationResult_validation() {
        CultivationResult created = CultivationResult.created(10L);
        assertThat(created.status()).isEqualTo(CultivationStatus.CREATED);
        assertThat(created.failureStage()).isNull();
        assertThat(created.exceptionType()).isNull();

        CultivationResult existing = CultivationResult.existing(11L);
        assertThat(existing.status()).isEqualTo(CultivationStatus.EXISTING);

        CultivationResult failed = CultivationResult.failed(
                20L, FailureStage.OWNER_RESOLUTION, new RuntimeException("fail")
        );
        assertThat(failed.status()).isEqualTo(CultivationStatus.FAILED);
        assertThat(failed.exceptionType()).isEqualTo("RuntimeException");

        // FAILED 인데 failureStage 누락
        assertThatThrownBy(() -> new CultivationResult(1L, CultivationStatus.FAILED, null, "RuntimeException"))
                .isInstanceOf(IllegalArgumentException.class);

        // FAILED 인데 exceptionType 공백
        assertThatThrownBy(() -> new CultivationResult(1L, CultivationStatus.FAILED, FailureStage.OWNER_RESOLUTION, "  "))
                .isInstanceOf(IllegalArgumentException.class);

        // CREATED 인데 failureStage 포함
        assertThatThrownBy(() -> new CultivationResult(1L, CultivationStatus.CREATED, FailureStage.OWNER_RESOLUTION, null))
                .isInstanceOf(IllegalArgumentException.class);

        // failed 팩토리에 null exception
        assertThatThrownBy(() -> CultivationResult.failed(1L, FailureStage.OWNER_RESOLUTION, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("DailyFeedbackBatchResult 생성자 유효성 검증 실패 케이스들")
    void batchResult_validationFailures() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        OffsetDateTime seoulSnapshotAt = OffsetDateTime.of(2026, 9, 1, 12, 0, 0, 0, SEOUL_OFFSET);
        OffsetDateTime utcSnapshotAt = OffsetDateTime.of(2026, 9, 1, 12, 0, 0, 0, ZoneOffset.UTC);

        CultivationResult created = CultivationResult.created(1L);
        List<CultivationResult> results = List.of(created);

        // 1. null 파라미터들
        assertThatThrownBy(() -> new DailyFeedbackBatchResult(null, seoulSnapshotAt, 1, 1, 0, 0, results))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DailyFeedbackBatchResult(date, null, 1, 1, 0, 0, results))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DailyFeedbackBatchResult(date, seoulSnapshotAt, 1, 1, 0, 0, null))
                .isInstanceOf(IllegalArgumentException.class);

        // 2. UTC 오프셋
        assertThatThrownBy(() -> new DailyFeedbackBatchResult(date, utcSnapshotAt, 1, 1, 0, 0, results))
                .isInstanceOf(IllegalArgumentException.class);

        // 3. 음수 카운트
        assertThatThrownBy(() -> new DailyFeedbackBatchResult(date, seoulSnapshotAt, -1, 0, 0, 0, results))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DailyFeedbackBatchResult(date, seoulSnapshotAt, 1, -1, 0, 0, results))
                .isInstanceOf(IllegalArgumentException.class);

        // 4. null 요소 포함
        List<CultivationResult> nullList = Collections.singletonList(null);
        assertThatThrownBy(() -> new DailyFeedbackBatchResult(date, seoulSnapshotAt, 1, 1, 0, 0, nullList))
                .isInstanceOf(IllegalArgumentException.class);

        // 5. 중복 cultivationId
        List<CultivationResult> dupList = List.of(CultivationResult.created(1L), CultivationResult.created(1L));
        assertThatThrownBy(() -> new DailyFeedbackBatchResult(date, seoulSnapshotAt, 2, 2, 0, 0, dupList))
                .isInstanceOf(IllegalArgumentException.class);

        // 6. 카운트 불일치
        assertThatThrownBy(() -> new DailyFeedbackBatchResult(date, seoulSnapshotAt, 1, 0, 0, 0, results))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
