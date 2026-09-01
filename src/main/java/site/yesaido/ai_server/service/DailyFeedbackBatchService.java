package site.yesaido.ai_server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.yesaido.ai_server.client.CultivationClient;
import site.yesaido.ai_server.dto.client.mushroom_reference.MushroomReferenceInfoResponse;
import site.yesaido.ai_server.dto.client.sensor.snapshot.DataGeneratorSnapshotResponse;
import site.yesaido.ai_server.dto.cultivation.DailyCultivationPhotoResponse;
import site.yesaido.ai_server.dto.daily_feedback.DailyFeedbackBatchResult;
import site.yesaido.ai_server.dto.daily_feedback.DailyFeedbackBatchResult.CultivationResult;
import site.yesaido.ai_server.dto.daily_feedback.DailyFeedbackBatchResult.FailureStage;
import site.yesaido.ai_server.dto.daily_feedback.DailyNotificationMetrics;
import site.yesaido.ai_server.entity.DailyFeedback;
import site.yesaido.ai_server.service.DailyFeedbackPersistenceService.PersistenceResult;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 한 날짜의 모든 대상 경작지에 대해 일일 피드백 처리를 실행하는
 * 최상위 배치 서비스입니다.
 *
 * <p>Data Generator Snapshot, 버섯 참조정보, Notification 통계와
 * 날짜별 사진 목록은 배치 실행마다 각각 한 번만 조회합니다.
 * Presigned URL의 유효 시간을 최대한 확보하기 위해 사진 목록은
 * 공통 데이터 중 마지막에 조회합니다.</p>
 *
 * <p>Snapshot은 피드백 대상 날짜의 과거 상태가 아니라 배치 실행
 * 시점의 활성 센서와 현재 임계값입니다. 따라서 Snapshot 생성 날짜와
 * {@code feedbackDate}가 같다고 강제하지 않습니다.</p>
 *
 * <p>경작지별 OWNER 조회와 피드백 처리는 오름차순으로 순차 실행합니다.
 * 하나의 경작지에서 {@link RuntimeException}이 발생하더라도 안전한
 * 실패 결과만 기록하고 다음 경작지를 계속 처리합니다.</p>
 *
 * <p>공통 데이터 조회 실패는 개별 경작지 실패로 변환하지 않습니다.
 * Snapshot, 대상 해석, 버섯 참조정보, Notification 통계 또는 사진 목록
 * 조회가 실패하면 예외를 그대로 상위 계층으로 전파합니다.</p>
 *
 * <p>이 서비스는 RabbitMQ 완료 이벤트 발행, 수확 전환 판단,
 * 스케줄링, Repository 직접 접근과 API 응답 구성을 담당하지 않습니다.
 * Feign, Vision과 LLM 처리 중 DB 연결을 점유하지 않도록 기존
 * 트랜잭션도 중단한 상태에서 실행합니다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyFeedbackBatchService {

    private static final String NULL_SNAPSHOT_RESPONSE_MESSAGE = "Data Generator Snapshot 응답이 null입니다.";

    private static final String INVALID_PROCESSING_RESULT_MESSAGE = "일일 피드백 처리 결과 계약이 올바르지 않습니다.";

    private final CultivationClient cultivationClient;
    private final DailyFeedbackTargetResolver targetResolver;
    private final DailyMushroomReferenceService mushroomReferenceService;
    private final DailyNotificationMetricsService notificationMetricsService;
    private final DailyVisionAnalysisService dailyVisionAnalysisService;
    private final CultivationOwnerService cultivationOwnerService;
    private final DailyFeedbackProcessor processor;

    /**
     * 지정한 날짜의 모든 일일 피드백 대상 경작지를 순차 처리합니다.
     *
     * <p>대상이 없으면 다른 공통 API와 경작지별 서비스를 호출하지 않고
     * 정상적인 빈 배치 결과를 반환합니다.</p>
     *
     * <p>사진이 없는 대상은 정상 처리 대상입니다. 사진 응답에 Snapshot
     * 대상이 아닌 경작지가 포함될 수도 있으므로 두 집합의 일치를
     * 강제하지 않고 대상 ID와 사진 Map의 교집합만 Processor에
     * 전달합니다.</p>
     *
     * @param feedbackDate 피드백을 생성할 Asia/Seoul 달력 날짜
     * @return 대상별 DB 저장 상태와 실패 단계를 담은 안전한 배치 결과
     * @throws IllegalArgumentException feedbackDate가 null인 경우
     * @throws RuntimeException 공통 데이터 조회 또는 해석에 실패한 경우
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DailyFeedbackBatchResult execute(LocalDate feedbackDate) {
        if (feedbackDate == null) {
            throw new IllegalArgumentException("feedbackDate는 null일 수 없습니다.");
        }

        DataGeneratorSnapshotResponse snapshot = cultivationClient.getDataGeneratorSnapshot();

        if (snapshot == null) {
            throw new IllegalStateException(NULL_SNAPSHOT_RESPONSE_MESSAGE);
        }

        List<Long> targetIds = targetResolver.resolveCultivationIds(snapshot);

        if (targetIds.isEmpty()) {
            DailyFeedbackBatchResult emptyResult =
                    DailyFeedbackBatchResult.from(feedbackDate, snapshot.snapshotAt(), List.of());

            logCompletedBatch(emptyResult);

            return emptyResult;
        }

        Map<Long, MushroomReferenceInfoResponse> referencesById =
                mushroomReferenceService.fetchAllById();

        Map<Long, DailyNotificationMetrics> notificationMetricsByCultivationId =
                notificationMetricsService.fetchDailyMetrics(feedbackDate, targetIds);

        Map<Long, DailyCultivationPhotoResponse> allPhotosByCultivationId =
                dailyVisionAnalysisService.fetchPhotosByCultivationId(feedbackDate);

        Map<Long, DailyCultivationPhotoResponse> targetPhotosByCultivationId =
                selectTargetPhotos(targetIds, allPhotosByCultivationId);

        List<CultivationResult> cultivationResults = new ArrayList<>(targetIds.size());

        for (Long cultivationId : targetIds) {
            Long ownerUserId;

            try {
                ownerUserId = cultivationOwnerService.findOwnerUserId(cultivationId);
            } catch (RuntimeException exception) {
                addFailure(
                        cultivationResults,
                        feedbackDate,
                        cultivationId,
                        FailureStage.OWNER_RESOLUTION,
                        exception
                );

                continue;
            }

            try {
                PersistenceResult persistenceResult =
                        processor.process(
                                feedbackDate,
                                cultivationId,
                                ownerUserId,
                                snapshot,
                                referencesById,
                                notificationMetricsByCultivationId,
                                targetPhotosByCultivationId
                        );

                cultivationResults.add(validateAndMapResult(persistenceResult, cultivationId, feedbackDate));
            } catch (RuntimeException exception) {
                addFailure(
                        cultivationResults,
                        feedbackDate,
                        cultivationId,
                        FailureStage.CULTIVATION_PROCESSING,
                        exception
                );
            }
        }

        DailyFeedbackBatchResult batchResult =
                DailyFeedbackBatchResult.from(feedbackDate, snapshot.snapshotAt(), cultivationResults);

        logCompletedBatch(batchResult);

        return batchResult;
    }

    /**
     * 전체 사진 Map에서 Snapshot 대상 경작지의 사진만 선택합니다.
     *
     * <p>대상 ID의 오름차순을 그대로 사용해 결정적인 반복 순서를
     * 유지합니다. 사진이 없는 대상은 Map에 추가하지 않으며,
     * 사진에 존재하는 ID를 이용해 배치 대상을 확장하지 않습니다.</p>
     */
    private Map<Long, DailyCultivationPhotoResponse>
    selectTargetPhotos(List<Long> targetIds,
            Map<Long, DailyCultivationPhotoResponse> allPhotosByCultivationId) {
        LinkedHashMap<Long, DailyCultivationPhotoResponse> targetPhotosByCultivationId = new LinkedHashMap<>();

        for (Long cultivationId : targetIds) {
            if (!allPhotosByCultivationId.containsKey(cultivationId)) {
                continue;
            }

            targetPhotosByCultivationId.put(cultivationId, allPhotosByCultivationId.get(cultivationId));
        }

        return Collections.unmodifiableMap(targetPhotosByCultivationId);
    }

    /**
     * Processor가 반환한 DB 결과의 식별정보와 상태를 검증하고
     * 외부 노출용 대상 결과로 변환합니다.
     *
     * <p>엔티티 ID, content와 contextSnapshot은 반환 결과에
     * 포함하지 않습니다.</p>
     */
    private CultivationResult validateAndMapResult(
            PersistenceResult persistenceResult,
            Long cultivationId,
            LocalDate feedbackDate
    ) {
        if (persistenceResult == null) {
            throw invalidProcessingResult();
        }

        DailyFeedback feedback = persistenceResult.feedback();

        if (feedback == null) {
            throw invalidProcessingResult();
        }

        if (persistenceResult.status() == null) {
            throw invalidProcessingResult();
        }

        if (feedback.getId() == null || feedback.getId() <= 0) {
            throw invalidProcessingResult();
        }

        if (!Objects.equals(cultivationId, feedback.getCultivationId())) {
            throw invalidProcessingResult();
        }

        if (!Objects.equals(feedbackDate, feedback.getFeedbackDate())) {
            throw invalidProcessingResult();
        }

        return switch (persistenceResult.status()) {
            case CREATED -> CultivationResult.created(cultivationId);
            case EXISTING -> CultivationResult.existing(cultivationId);
        };
    }

    private IllegalStateException invalidProcessingResult() {
        return new IllegalStateException(INVALID_PROCESSING_RESULT_MESSAGE);
    }

    /**
     * 개별 경작지 실패를 안전한 결과로 변환하고 다음 대상을
     * 계속 처리할 수 있도록 기록합니다.
     */
    private void addFailure(
            List<CultivationResult> cultivationResults,
            LocalDate feedbackDate,
            Long cultivationId,
            FailureStage failureStage,
            RuntimeException exception
    ) {
        CultivationResult failure = CultivationResult.failed(cultivationId, failureStage, exception);

        log.warn("일일 피드백 대상 처리 실패: "
                        + "feedbackDate={}, "
                        + "cultivationId={}, "
                        + "failureStage={}, "
                        + "exceptionType={}",
                feedbackDate,
                cultivationId,
                failureStage,
                safeExceptionType(exception)
        );

        cultivationResults.add(failure);
    }

    /**
     * 로그에 예외 메시지나 stack trace를 전달하지 않고
     * 안전한 클래스 단순 이름만 반환합니다.
     */
    private String safeExceptionType(RuntimeException exception) {
        String simpleName = exception.getClass().getSimpleName();

        if (simpleName == null || simpleName.isBlank()) {
            return "RuntimeException";
        }

        return simpleName;
    }

    /**
     * 정상 완료된 배치의 날짜와 집계 숫자만 기록합니다.
     */
    private void logCompletedBatch(DailyFeedbackBatchResult batchResult) {
        log.info("일일 피드백 배치 완료: feedbackDate={}, targetCount={}, "
                        + "createdCount={}, existingCount={}, failedCount={}",
                batchResult.feedbackDate(),
                batchResult.targetCount(),
                batchResult.createdCount(),
                batchResult.existingCount(),
                batchResult.failedCount()
        );
    }
}
