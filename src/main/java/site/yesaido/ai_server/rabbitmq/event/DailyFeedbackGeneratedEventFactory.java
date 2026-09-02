package site.yesaido.ai_server.rabbitmq.event;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import site.yesaido.ai_server.entity.DailyFeedback;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.UUID;

/**
 * DB 저장이 완료된 {@link DailyFeedback}을 일일 피드백 생성 이벤트로
 * 결정적으로 변환하는 Factory입니다.
 *
 * <p>경작지 ID와 피드백 날짜를 이벤트 멱등성 키로 사용하며,
 * Context Snapshot에서는 검증된 재배지 이름만 추출합니다.
 * 현재 시각이나 무작위 UUID를 사용하지 않으므로 같은 경작지와 날짜의
 * 이벤트를 다시 생성해도 동일한 이벤트 ID가 만들어집니다.</p>
 *
 * <p>이 클래스는 이벤트 생성만 담당합니다. 생성된 이벤트를 RabbitMQ로
 * 발행하는 책임은 {@code AiNotificationProducer}에 있습니다.</p>
 */
@Component
public class DailyFeedbackGeneratedEventFactory {

    private static final String EVENT_ID_NAMESPACE_PREFIX =
            "yesaido:ai:daily-feedback-generated:v1:";

    private static final ZoneId SEOUL_ZONE =
            ZoneId.of("Asia/Seoul");

    private static final String INVALID_STORED_FEEDBACK_CONTRACT_MESSAGE =
            "저장된 일일 피드백 계약이 올바르지 않습니다.";

    /**
     * 저장이 완료된 일일 피드백을 Notification Server 전송용 이벤트로 변환합니다.
     *
     * <p>이벤트 ID는 경작지 ID와 피드백 날짜로 결정하며, 발생 시각은
     * 엔티티의 최초 DB 생성 시각을 Asia/Seoul 기준 OffsetDateTime으로
     * 변환하여 사용합니다. 재배지 이름은 저장된 Context Snapshot의
     * 식별정보를 검증한 뒤 추출합니다.</p>
     *
     * <p>현재 시각과 무작위 UUID를 사용하지 않으며, 이 메서드는
     * RabbitMQ 발행을 수행하지 않습니다.</p>
     *
     * @param feedback DB 저장이 완료된 일일 피드백
     * @param ownerUserId 알림을 받을 재배지 OWNER 사용자 ID
     * @return 결정적으로 생성된 일일 피드백 완료 이벤트
     * @throws IllegalArgumentException 호출자 입력값이 올바르지 않은 경우
     * @throws IllegalStateException 저장된 피드백 또는 Context Snapshot 계약이
     *                               올바르지 않은 경우
     */
    public AiEvent.DailyFeedbackGeneratedEvent create(DailyFeedback feedback, Long ownerUserId) {
        if (feedback == null) {
            throw new IllegalArgumentException("feedback은 null일 수 없습니다.");
        }

        if (ownerUserId == null || ownerUserId <= 0) {
            throw new IllegalArgumentException("ownerUserId는 0보다 커야 합니다.");
        }

        Long feedbackId = feedback.getId();
        Long cultivationId = feedback.getCultivationId();
        var feedbackDate = feedback.getFeedbackDate();
        String feedbackContent = feedback.getContent();
        var createdAt = feedback.getCreatedAt();

        if (feedbackId == null || feedbackId <= 0 || cultivationId == null
                || cultivationId <= 0 || feedbackDate == null || feedbackContent == null
                || feedbackContent.isBlank() || createdAt == null) {
            throw invalidStoredFeedbackContract();
        }

        JsonNode contextSnapshot = feedback.getContextSnapshot();

        if (contextSnapshot == null || !contextSnapshot.isObject()) {
            throw invalidStoredFeedbackContract();
        }

        JsonNode snapshotCultivationId = contextSnapshot.at("/cultivationId");
        JsonNode snapshotFeedbackDate = contextSnapshot.at("/feedbackDate");
        JsonNode cultivationDetail = contextSnapshot.at("/cultivationDetail");
        JsonNode detailCultivationId = contextSnapshot.at("/cultivationDetail/cultivationId");
        JsonNode cultivationNameNode = contextSnapshot.at("/cultivationDetail/name");

        if (!matchesCultivationId(snapshotCultivationId, cultivationId)) {
            throw invalidStoredFeedbackContract();
        }

        if (!snapshotFeedbackDate.isTextual() || !feedbackDate.toString().equals(snapshotFeedbackDate.textValue())) {
            throw invalidStoredFeedbackContract();
        }

        if (!cultivationDetail.isObject()) {
            throw invalidStoredFeedbackContract();
        }

        if (!matchesCultivationId(detailCultivationId, cultivationId)) {
            throw invalidStoredFeedbackContract();
        }

        if (!cultivationNameNode.isTextual() || cultivationNameNode.textValue().isBlank()) {
            throw invalidStoredFeedbackContract();
        }

        String cultivationName = cultivationNameNode.textValue().strip();
        String eventIdSource = EVENT_ID_NAMESPACE_PREFIX + cultivationId + ":" + feedbackDate;

        UUID eventId = UUID.nameUUIDFromBytes(eventIdSource.getBytes(StandardCharsets.UTF_8));

        String feedbackUrl = "/cultivations/" + cultivationId + "/daily-feedbacks/" + feedbackDate;

        return new AiEvent.DailyFeedbackGeneratedEvent(
                eventId,
                ownerUserId,
                cultivationId,
                cultivationName,
                feedbackUrl,
                feedbackContent,
                createdAt.atZone(SEOUL_ZONE).toOffsetDateTime()
        );
    }

    private boolean matchesCultivationId(JsonNode cultivationIdNode, Long expectedCultivationId) {
        return cultivationIdNode.isIntegralNumber()
                && cultivationIdNode.canConvertToLong()
                && cultivationIdNode.longValue()
                == expectedCultivationId;
    }

    private IllegalStateException invalidStoredFeedbackContract() {
        return new IllegalStateException(INVALID_STORED_FEEDBACK_CONTRACT_MESSAGE);
    }
}
