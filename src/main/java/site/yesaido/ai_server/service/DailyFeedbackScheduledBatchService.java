package site.yesaido.ai_server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.yesaido.ai_server.dto.daily_feedback.DailyFeedbackBatchResult;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Kubernetes CronJob 요청을 받아 전날의 일일 피드백 배치를 실행하는
 * 단일 진입점 서비스입니다.
 *
 * <p>AI Server가 여러 Pod로 실행되는 환경에서 일반 {@code @Scheduled}
 * 작업을 사용하면 각 Pod가 같은 날짜의 Vision 및 LLM 외부 호출을
 * 동시에 수행할 수 있습니다. 이를 방지하기 위해 하루 한 번의 실행
 * 시점은 Kubernetes CronJob이 관리하고, 이 서비스는 요청을 받은 뒤
 * 전날 날짜 계산과 배치 실행만 담당합니다.</p>
 *
 * <p>일부 경작지가 실패하면 예외를 발생시켜 Kubernetes Job의 실패와
 * 재시도를 유도합니다. 앞선 실행에서 이미 저장된 피드백은
 * {@code daily_feedback}의 멱등 저장 계약에 따라 재시도 시 기존 결과로
 * 처리되므로 Vision 및 LLM 호출을 다시 수행하지 않습니다.</p>
 *
 * <p>Feign, Vision 및 LLM 호출 동안 DB 트랜잭션을 유지하지 않도록
 * 공개 실행 메서드는 기존 트랜잭션도 중단한 상태에서 동작합니다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyFeedbackScheduledBatchService {

    private static final String INVALID_BATCH_RESULT_MESSAGE =
            "일일 피드백 배치 결과 계약이 올바르지 않습니다.";

    private static final String PARTIAL_FAILURE_MESSAGE =
            "일일 피드백 배치에 실패한 대상이 포함되어 있습니다.";

    private final DailyFeedbackBatchService dailyFeedbackBatchService;
    private final Clock clock;

    /**
     * Asia/Seoul 기준 오늘의 전날을 계산하여 일일 피드백 배치를
     * 실행합니다.
     *
     * <p>반환 결과의 날짜와 집계 계약을 다시 검증하며, 실패 대상이
     * 하나라도 포함되면 Kubernetes CronJob이 실행 실패를 인식하고
     * 재시도할 수 있도록 안전한 예외를 발생시킵니다.</p>
     *
     * @return 실패 대상 없이 완료된 전날 일일 피드백 배치 결과
     * @throws IllegalStateException 반환 계약이 잘못되었거나 실패 대상이
     *                               포함된 경우
     * @throws RuntimeException 일일 피드백 배치의 공통 조회 또는 실행이
     *                          실패한 경우
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DailyFeedbackBatchResult executePreviousDay() {
        LocalDate feedbackDate = LocalDate.now(clock).minusDays(1);

        DailyFeedbackBatchResult result =
                dailyFeedbackBatchService.execute(feedbackDate);

        validateResult(result, feedbackDate);

        if (result.failedCount() > 0) {
            log.warn(
                    "전날 일일 피드백 배치 부분 실패: "
                            + "feedbackDate={}, targetCount={}, "
                            + "createdCount={}, existingCount={}, "
                            + "failedCount={}",
                    feedbackDate,
                    result.targetCount(),
                    result.createdCount(),
                    result.existingCount(),
                    result.failedCount()
            );

            throw new IllegalStateException(PARTIAL_FAILURE_MESSAGE);
        }

        log.info(
                "전날 일일 피드백 배치 완료: "
                        + "feedbackDate={}, targetCount={}, "
                        + "createdCount={}, existingCount={}, "
                        + "failedCount={}",
                feedbackDate,
                result.targetCount(),
                result.createdCount(),
                result.existingCount(),
                result.failedCount()
        );

        return result;
    }

    private void validateResult(
            DailyFeedbackBatchResult result,
            LocalDate feedbackDate
    ) {
        if (result == null) {
            throw new IllegalStateException(
                    INVALID_BATCH_RESULT_MESSAGE
            );
        }

        if (!feedbackDate.equals(result.feedbackDate())) {
            throw new IllegalStateException(
                    INVALID_BATCH_RESULT_MESSAGE
            );
        }

        if (result.targetCount() < 0
                || result.createdCount() < 0
                || result.existingCount() < 0
                || result.failedCount() < 0) {
            throw new IllegalStateException(
                    INVALID_BATCH_RESULT_MESSAGE
            );
        }

        long statusCount = (long) result.createdCount()
                + result.existingCount()
                + result.failedCount();

        if (statusCount != result.targetCount()) {
            throw new IllegalStateException(
                    INVALID_BATCH_RESULT_MESSAGE
            );
        }
    }
}
