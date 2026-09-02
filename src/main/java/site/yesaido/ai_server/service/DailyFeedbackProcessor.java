package site.yesaido.ai_server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.yesaido.ai_server.dto.client.mushroom_reference.MushroomReferenceInfoResponse;
import site.yesaido.ai_server.dto.client.sensor.snapshot.DataGeneratorSnapshotResponse;
import site.yesaido.ai_server.dto.cultivation.DailyCultivationPhotoResponse;
import site.yesaido.ai_server.dto.daily_feedback.DailyFeedbackContext;
import site.yesaido.ai_server.dto.daily_feedback.DailyNotificationMetrics;
import site.yesaido.ai_server.entity.DailyFeedback;
import site.yesaido.ai_server.exception.DailyFeedbackProcessingException;
import site.yesaido.ai_server.exception.DailyFeedbackProcessingException.Reason;
import site.yesaido.ai_server.service.DailyFeedbackPersistenceService.PersistenceResult;
import site.yesaido.ai_server.service.DailyFeedbackPersistenceService.PersistenceStatus;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/**
 * 한 경작지와 한 날짜의 일일 피드백 생성 과정을 연결합니다.
 *
 * <p>기존 피드백이 있으면 외부 데이터 수집과 LLM 호출을 생략하고
 * DB에 저장된 기존 피드백을 반환합니다.</p>
 *
 * <p>신규 대상은 Context 수집, 원본 Context Snapshot 변환,
 * LLM 피드백 생성, 엔티티 생성과 멱등 저장 순서로 처리합니다.</p>
 *
 * <p>여러 경작지 반복, 공통 외부 데이터 조회, 실패 격리,
 * RabbitMQ 이벤트 발행과 스케줄링은 담당하지 않습니다.</p>
 */
@Service
@RequiredArgsConstructor
public class DailyFeedbackProcessor {

    private final DailyFeedbackContextCollector contextCollector;
    private final DailyFeedbackGenerationService generationService;
    private final DailyFeedbackPersistenceService persistenceService;
    private final ObjectMapper objectMapper;

    /**
     * 한 경작지의 일일 피드백을 생성하거나 기존 결과를 반환합니다.
     *
     * <p>Context 수집과 LLM 호출은 외부 통신을 포함하므로 호출자가
     * 트랜잭션 안에서 이 메서드를 호출하더라도 기존 트랜잭션을
     * 일시 중단합니다.</p>
     *
     * @param feedbackDate 피드백 대상 Asia/Seoul 달력 날짜
     * @param cultivationId 처리할 경작지 ID
     * @param ownerUserId Cultivation 권한 검사에 사용할 OWNER 사용자 ID
     * @param snapshot 배치 시작 시 한 번 조회한 Data Generator Snapshot
     * @param referencesById 배치에서 한 번 조회한 버섯 참조정보 Map
     * @param notificationMetricsByCultivationId 경작지별 Notification 통계 Map
     * @param photosByCultivationId 대상 날짜의 경작지별 사진 Map
     * @return DB에서 확정된 피드백과 CREATED 또는 EXISTING 상태
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PersistenceResult process(
            LocalDate feedbackDate,
            Long cultivationId,
            Long ownerUserId,
            DataGeneratorSnapshotResponse snapshot,
            Map<Long, MushroomReferenceInfoResponse> referencesById,
            Map<Long, DailyNotificationMetrics> notificationMetricsByCultivationId,
            Map<Long, DailyCultivationPhotoResponse> photosByCultivationId
    ) {
        validateInputs(
                feedbackDate,
                cultivationId,
                ownerUserId,
                snapshot,
                referencesById,
                notificationMetricsByCultivationId,
                photosByCultivationId
        );

        Optional<DailyFeedback> existing = persistenceService.findExisting(cultivationId, feedbackDate);

        if (existing.isPresent()) {
            return new PersistenceResult(existing.get(), PersistenceStatus.EXISTING);
        }

        DailyFeedbackContext context =
                contextCollector.collect(
                        feedbackDate,
                        cultivationId,
                        ownerUserId,
                        snapshot,
                        referencesById,
                        notificationMetricsByCultivationId,
                        photosByCultivationId
                );

        JsonNode contextSnapshot = createContextSnapshot(context);

        String content = generationService.generate(context);

        DailyFeedback candidate =
                DailyFeedback.builder()
                        .cultivationId(context.cultivationId())
                        .feedbackDate(context.feedbackDate())
                        .hasVisionAnalysis(context.visionAnalysis().hasVisionAnalysis())
                        .content(content)
                        .contextSnapshot(contextSnapshot)
                        .build();

        return persistenceService.saveOrGet(candidate);
    }

    private JsonNode createContextSnapshot(DailyFeedbackContext context) {
        JsonNode contextSnapshot;

        try {
            contextSnapshot = objectMapper.valueToTree(context);
        } catch (RuntimeException exception) {
            throw new DailyFeedbackProcessingException(
                    context.cultivationId(),
                    context.feedbackDate(),
                    Reason.CONTEXT_SNAPSHOT_SERIALIZATION_FAILED,
                    exception
            );
        }

        if (contextSnapshot == null || contextSnapshot.isNull() || !contextSnapshot.isObject()) {
            throw new DailyFeedbackProcessingException(
                    context.cultivationId(),
                    context.feedbackDate(),
                    Reason.INVALID_CONTEXT_SNAPSHOT
            );
        }

        return contextSnapshot;
    }

    private void validateInputs(
            LocalDate feedbackDate,
            Long cultivationId,
            Long ownerUserId,
            DataGeneratorSnapshotResponse snapshot,
            Map<Long, MushroomReferenceInfoResponse> referencesById,
            Map<Long, DailyNotificationMetrics> notificationMetricsByCultivationId,
            Map<Long, DailyCultivationPhotoResponse> photosByCultivationId
    ) {
        if (feedbackDate == null) {
            throw new IllegalArgumentException("feedbackDate는 null일 수 없습니다.");
        }

        if (cultivationId == null || cultivationId <= 0) {
            throw new IllegalArgumentException("cultivationId는 null이 아니며 0보다 커야 합니다.");
        }

        if (ownerUserId == null || ownerUserId <= 0) {
            throw new IllegalArgumentException("ownerUserId는 null이 아니며 0보다 커야 합니다.");
        }

        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot은 null일 수 없습니다.");
        }

        if (referencesById == null) {
            throw new IllegalArgumentException("referencesById는 null일 수 없습니다.");
        }

        if (notificationMetricsByCultivationId == null) {
            throw new IllegalArgumentException("notificationMetricsByCultivationId는 null일 수 없습니다.");
        }

        if (photosByCultivationId == null) {
            throw new IllegalArgumentException("photosByCultivationId는 null일 수 없습니다.");
        }
    }
}
