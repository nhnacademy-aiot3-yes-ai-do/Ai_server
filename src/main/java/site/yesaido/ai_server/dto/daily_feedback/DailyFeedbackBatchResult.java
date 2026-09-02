package site.yesaido.ai_server.dto.daily_feedback;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 공통 데이터 조회가 성공한 뒤 경작지별 일일 피드백 처리를 수행한
 * 배치 결과를 나타내는 불변 DTO입니다.
 *
 * <p>이 DTO는 배치 대상 수와 DB 저장 결과를 요약하며,
 * {@link CultivationStatus#CREATED}와
 * {@link CultivationStatus#EXISTING}은 현재 DB에서 확인된
 * 저장 결과만 의미합니다. RabbitMQ 완료 이벤트가 성공적으로
 * 발행됐다는 의미는 포함하지 않습니다.</p>
 *
 * <p>공통 Data Generator Snapshot, 버섯 참조정보, Notification 통계,
 * 날짜별 사진 목록 조회 실패를 임의의 경작지 실패로 변환하지 않습니다.
 * 이러한 공통 조회 실패는 배치 서비스에서 예외로 전파하여 전체 실행을
 * 실패시켜야 합니다.</p>
 *
 * <p>{@code DailyFeedback} 엔티티, 피드백 content와 contextSnapshot,
 * 내부 저장 ID, OWNER 사용자 ID, 센서 EUI, Presigned URL 및
 * 예외 메시지나 stack trace는 포함하지 않습니다.</p>
 *
 * @param feedbackDate 피드백 대상 Asia/Seoul 달력 날짜
 * @param snapshotAt 배치에서 사용한 Data Generator Snapshot 생성 시각
 * @param targetCount 전체 처리 대상 경작지 수
 * @param createdCount 이번 처리에서 새로 저장된 경작지 수
 * @param existingCount 기존 저장 결과를 사용한 경작지 수
 * @param failedCount 경작지별 처리에 실패한 경작지 수
 * @param results 경작지 ID 오름차순으로 정렬된 대상별 처리 결과
 */
public record DailyFeedbackBatchResult(
        LocalDate feedbackDate,
        OffsetDateTime snapshotAt,
        int targetCount,
        int createdCount,
        int existingCount,
        int failedCount,
        List<CultivationResult> results
) {

    private static final ZoneOffset SEOUL_OFFSET =
            ZoneOffset.ofHours(9);

    public DailyFeedbackBatchResult {
        validateDateAndSnapshot(feedbackDate, snapshotAt);
        validateNonNegativeCounts(
                targetCount,
                createdCount,
                existingCount,
                failedCount
        );

        if (results == null) {
            throw new IllegalArgumentException("results는 null일 수 없습니다.");
        }

        ResultSummary resultSummary = normalizeResults(results);

        validateCounts(
                targetCount,
                createdCount,
                existingCount,
                failedCount,
                resultSummary
        );

        results = List.copyOf(resultSummary.results());
    }

    private static void validateDateAndSnapshot(
            LocalDate feedbackDate,
            OffsetDateTime snapshotAt
    ) {
        if (feedbackDate == null) {
            throw new IllegalArgumentException("feedbackDate는 null일 수 없습니다.");
        }

        if (snapshotAt == null) {
            throw new IllegalArgumentException("snapshotAt은 null일 수 없습니다.");
        }

        if (!SEOUL_OFFSET.equals(snapshotAt.getOffset())) {
            throw new IllegalArgumentException("snapshotAt의 offset은 +09:00이어야 합니다.");
        }
    }

    private static void validateNonNegativeCounts(
            int targetCount,
            int createdCount,
            int existingCount,
            int failedCount
    ) {
        if (targetCount < 0) {
            throw new IllegalArgumentException("targetCount는 음수일 수 없습니다.");
        }

        if (createdCount < 0) {
            throw new IllegalArgumentException("createdCount는 음수일 수 없습니다.");
        }

        if (existingCount < 0) {
            throw new IllegalArgumentException("existingCount는 음수일 수 없습니다.");
        }

        if (failedCount < 0) {
            throw new IllegalArgumentException("failedCount는 음수일 수 없습니다.");
        }
    }

    private static ResultSummary normalizeResults(
            List<CultivationResult> results
    ) {
        List<CultivationResult> normalizedResults = new ArrayList<>(results.size());
        Set<Long> cultivationIds = new HashSet<>();

        int actualCreatedCount = 0;
        int actualExistingCount = 0;
        int actualFailedCount = 0;

        for (CultivationResult result : results) {
            if (result == null) {
                throw new IllegalArgumentException("results에는 null 요소가 포함될 수 없습니다.");
            }

            Long resultCultivationId = result.cultivationId();

            if (resultCultivationId == null || resultCultivationId <= 0) {
                throw new IllegalArgumentException("결과의 cultivationId는 null이 아니며 0보다 커야 합니다.");
            }

            if (!cultivationIds.add(resultCultivationId)) {
                throw new IllegalArgumentException("results에는 동일한 cultivationId가 중복될 수 없습니다.");
            }

            if (result.status() == null) {
                throw new IllegalArgumentException("결과의 status는 null일 수 없습니다.");
            }

            switch (result.status()) {
                case CREATED -> actualCreatedCount++;
                case EXISTING -> actualExistingCount++;
                case FAILED -> actualFailedCount++;
            }

            normalizedResults.add(result);
        }

        normalizedResults.sort(Comparator.comparing(CultivationResult::cultivationId));

        return new ResultSummary(
                normalizedResults,
                actualCreatedCount,
                actualExistingCount,
                actualFailedCount
        );
    }

    private static void validateCounts(
            int targetCount,
            int createdCount,
            int existingCount,
            int failedCount,
            ResultSummary resultSummary
    ) {
        if (targetCount != resultSummary.results().size()) {
            throw new IllegalArgumentException("targetCount는 results의 크기와 일치해야 합니다.");
        }

        long suppliedStatusCount = (long) createdCount + existingCount + failedCount;

        if (suppliedStatusCount != targetCount) {
            throw new IllegalArgumentException("createdCount, existingCount, failedCount의 합계는 targetCount와 일치해야 합니다.");
        }

        if (createdCount != resultSummary.createdCount()) {
            throw new IllegalArgumentException("createdCount는 CREATED 결과의 실제 개수와 일치해야 합니다.");
        }

        if (existingCount != resultSummary.existingCount()) {
            throw new IllegalArgumentException("existingCount는 EXISTING 결과의 실제 개수와 일치해야 합니다.");
        }

        if (failedCount != resultSummary.failedCount()) {
            throw new IllegalArgumentException("failedCount는 FAILED 결과의 실제 개수와 일치해야 합니다.");
        }
    }

    /**
     * 경작지별 처리 결과를 기준으로 배치 집계값을 계산합니다.
     *
     * <p>호출자가 전달한 count를 신뢰하지 않고 결과 목록에 존재하는
     * 각 상태를 직접 세어 최상위 count를 생성합니다. 최종 생성은
     * canonical constructor를 거치므로 중복 ID, 상태별 계약과
     * count 불변식도 다시 검증됩니다.</p>
     *
     * @param feedbackDate 피드백 대상 날짜
     * @param snapshotAt 배치에서 사용한 Snapshot 생성 시각
     * @param results 경작지별 처리 결과
     * @return 계산된 count와 정렬된 불변 결과 목록을 가진 배치 결과
     */
    public static DailyFeedbackBatchResult from(
            LocalDate feedbackDate,
            OffsetDateTime snapshotAt,
            List<CultivationResult> results
    ) {
        if (results == null) {
            throw new IllegalArgumentException("results는 null일 수 없습니다.");
        }

        List<CultivationResult> copiedResults = new ArrayList<>(results.size());

        int createdCount = 0;
        int existingCount = 0;
        int failedCount = 0;

        for (CultivationResult result : results) {
            if (result == null) {
                throw new IllegalArgumentException("results에는 null 요소가 포함될 수 없습니다.");
            }

            switch (result.status()) {
                case CREATED -> createdCount++;
                case EXISTING -> existingCount++;
                case FAILED -> failedCount++;
            }

            copiedResults.add(result);
        }

        return new DailyFeedbackBatchResult(
                feedbackDate,
                snapshotAt,
                copiedResults.size(),
                createdCount,
                existingCount,
                failedCount,
                copiedResults
        );
    }

    /**
     * 한 경작지의 일일 피드백 처리 결과입니다.
     *
     * <p>성공 결과에는 실패 단계와 예외 타입을 포함하지 않습니다.
     * 실패 결과에는 안전하게 분류된 실패 단계와 예외 클래스의
     * 단순 이름만 포함합니다.</p>
     *
     * <p>예외 메시지, cause, stack trace와 처리 과정의 원본 데이터는
     * 저장하지 않습니다.</p>
     *
     * @param cultivationId 처리한 경작지 ID
     * @param status DB 저장 또는 처리 실패 상태
     * @param failureStage 실패한 처리 단계 또는 성공이면 null
     * @param exceptionType 실패 예외의 안전한 클래스 단순 이름 또는 성공이면 null
     */
    public record CultivationResult(
            Long cultivationId,
            CultivationStatus status,
            FailureStage failureStage,
            String exceptionType
    ) {

        private static final Pattern EXCEPTION_TYPE_PATTERN = Pattern.compile("^[A-Za-z0-9_$]+$");

        public CultivationResult {
            validateRequiredFields(cultivationId, status);

            if (status == CultivationStatus.FAILED) {
                validateFailedFields(failureStage, exceptionType);
            } else {
                validateSuccessfulFields(failureStage, exceptionType);
            }
        }

        private static void validateRequiredFields(
                Long cultivationId,
                CultivationStatus status
        ) {
            if (cultivationId == null || cultivationId <= 0) {
                throw new IllegalArgumentException("cultivationId는 null이 아니며 0보다 커야 합니다.");
            }

            if (status == null) {
                throw new IllegalArgumentException("status는 null일 수 없습니다.");
            }
        }

        private static void validateFailedFields(
                FailureStage failureStage,
                String exceptionType
        ) {
            if (failureStage == null) {
                throw new IllegalArgumentException("FAILED 결과에는 failureStage가 필수입니다.");
            }

            if (exceptionType == null || exceptionType.isBlank()) {
                throw new IllegalArgumentException("FAILED 결과에는 exceptionType이 필수입니다.");
            }

            if (!EXCEPTION_TYPE_PATTERN.matcher(exceptionType).matches()) {
                throw new IllegalArgumentException("exceptionType은 영문자, 숫자, _ 또는 $만 포함할 수 있습니다.");
            }
        }

        private static void validateSuccessfulFields(
                FailureStage failureStage,
                String exceptionType
        ) {
            if (failureStage != null) {
                throw new IllegalArgumentException("CREATED 또는 EXISTING 결과에는 failureStage를 포함할 수 없습니다.");
            }

            if (exceptionType != null) {
                throw new IllegalArgumentException("CREATED 또는 EXISTING 결과에는 exceptionType을 포함할 수 없습니다.");
            }
        }

        /**
         * 이번 처리에서 새로 저장된 경작지 결과를 생성합니다.
         *
         * @param cultivationId 처리한 경작지 ID
         * @return CREATED 상태의 결과
         */
        public static CultivationResult created(Long cultivationId) {
            return new CultivationResult(
                    cultivationId,
                    CultivationStatus.CREATED,
                    null,
                    null
            );
        }

        /**
         * 기존 DB 피드백을 사용한 경작지 결과를 생성합니다.
         *
         * @param cultivationId 처리한 경작지 ID
         * @return EXISTING 상태의 결과
         */
        public static CultivationResult existing(Long cultivationId) {
            return new CultivationResult(
                    cultivationId,
                    CultivationStatus.EXISTING,
                    null,
                    null
            );
        }

        /**
         * 경작지별 처리에 실패한 결과를 생성합니다.
         *
         * <p>예외 객체 자체나 메시지를 보관하지 않고 클래스의 단순
         * 이름만 추출합니다. 익명 예외처럼 단순 이름이 비어 있으면
         * {@code RuntimeException}을 사용합니다.</p>
         *
         * @param cultivationId 처리에 실패한 경작지 ID
         * @param failureStage 실패한 처리 단계
         * @param exception 발생한 RuntimeException
         * @return FAILED 상태의 안전한 결과
         */
        public static CultivationResult failed(Long cultivationId, FailureStage failureStage, RuntimeException exception) {
            if (exception == null) {
                throw new IllegalArgumentException("exception은 null일 수 없습니다.");
            }

            String simpleName = exception.getClass().getSimpleName();

            if (simpleName == null || simpleName.isBlank()) {
                simpleName = "RuntimeException";
            }

            return new CultivationResult(
                    cultivationId,
                    CultivationStatus.FAILED,
                    failureStage,
                    simpleName
            );
        }
    }

    private record ResultSummary(
            List<CultivationResult> results,
            int createdCount,
            int existingCount,
            int failedCount
    ) {
    }

    /**
     * 경작지별 처리 결과 상태입니다.
     *
     * <p>CREATED와 EXISTING은 DB에서 확정된 저장 상태만 의미하며,
     * RabbitMQ 완료 이벤트 발행 결과를 나타내지 않습니다.</p>
     */
    public enum CultivationStatus {
        CREATED,
        EXISTING,
        FAILED
    }

    /**
     * 대상 경작지 처리 중 실패한 단계입니다.
     *
     * <p>배치 공통 데이터 조회 실패는 이 값으로 표현하지 않고
     * 전체 배치 실패 예외로 전파합니다.</p>
     */
    public enum FailureStage {
        OWNER_RESOLUTION,
        CULTIVATION_PROCESSING
    }
}
