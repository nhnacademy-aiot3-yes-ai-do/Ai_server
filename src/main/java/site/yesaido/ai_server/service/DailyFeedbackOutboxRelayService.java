package site.yesaido.ai_server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.yesaido.ai_server.config.DailyFeedbackOutboxProperties;
import site.yesaido.ai_server.rabbitmq.AiNotificationProducer;
import site.yesaido.ai_server.rabbitmq.event.AiEvent;
import site.yesaido.ai_server.service.DailyFeedbackOutboxClaimService.ClaimedOutbox;
import site.yesaido.ai_server.service.DailyFeedbackOutboxStateService.FailureDisposition;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 선점된 일일 피드백 Outbox를 RabbitMQ로 전달하고 발행 결과를 기록합니다.
 *
 * <p>Outbox 재시도와 Notification Server의 {@code eventId} 중복 방지를
 * 결합하여 {@code at-least-once} 방식으로 이벤트를 전달합니다.
 * RabbitMQ 발행 성공과 Outbox 상태 저장 사이에는 분산 트랜잭션이 없으므로
 * 동일한 이벤트가 다시 발행될 수 있습니다.</p>
 *
 * <p>RabbitMQ 네트워크 호출은 DB 트랜잭션 밖에서 수행합니다.
 * Broker Confirm을 기다리는 동안 DB 연결이나 행 잠금을 점유하지 않으며,
 * 선점과 각 상태 기록만 별도의 짧은 트랜잭션으로 처리합니다.</p>
 *
 * <p>RabbitMQ 발행은 성공했지만 PUBLISHED 상태 저장에 실패한 경우에는
 * 추가 실패 처리를 수행하지 않고 Outbox를 SENDING 상태로 남깁니다.
 * Broker가 이미 이벤트를 수신했을 수 있으므로 발행 실패로 되돌리지 않으며,
 * 이후 stale recovery가 해당 Outbox를 복구합니다.</p>
 *
 * <p>스케줄 실행과 오래된 SENDING 복구는 이 클래스의 책임이 아닙니다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyFeedbackOutboxRelayService {

    private static final String INVALID_PAYLOAD_MESSAGE =
            "일일 피드백 Outbox Payload 계약이 올바르지 않습니다.";

    private final DailyFeedbackOutboxClaimService
            dailyFeedbackOutboxClaimService;

    private final DailyFeedbackOutboxStateService
            dailyFeedbackOutboxStateService;

    private final AiNotificationProducer aiNotificationProducer;

    private final ObjectMapper objectMapper;

    private final DailyFeedbackOutboxProperties
            dailyFeedbackOutboxProperties;

    private final Clock clock;

    /**
     * 현재 발행 가능한 PENDING Outbox를 한 배치 선점하고,
     * 각 항목을 독립적으로 발행한 뒤 처리 결과를 집계합니다.
     *
     * <p>메서드 자체에서는 DB 트랜잭션을 유지하지 않습니다.
     * 선점과 상태 변경은 각각 별도 서비스의 짧은 트랜잭션으로 실행됩니다.
     * 한 Outbox의 발행 또는 상태 기록 실패는 다음 Outbox 처리를
     * 중단시키지 않습니다.</p>
     *
     * <p>단, 최초 선점 작업 자체가 실패하면 정상적인 배치 결과로
     * 변환하지 않고 예외를 호출자에게 그대로 전파합니다.</p>
     *
     * @return 선점 개수와 항목별 처리 결과 개수를 담은 배치 결과
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RelayBatchResult relayPending() {
        LocalDateTime claimedAt = LocalDateTime.now(clock);

        List<ClaimedOutbox> claimedOutboxes =
                dailyFeedbackOutboxClaimService.claimPending(
                        claimedAt,
                        dailyFeedbackOutboxProperties.getBatchSize()
                );

        int publishedCount = 0;
        int retryScheduledCount = 0;
        int failedCount = 0;
        int stateUpdateFailedCount = 0;

        for (ClaimedOutbox claimedOutbox : claimedOutboxes) {
            ItemResult itemResult =
                    relayClaimedOutboxSafely(claimedOutbox);

            switch (itemResult) {
                case PUBLISHED -> publishedCount++;
                case RETRY_SCHEDULED -> retryScheduledCount++;
                case FAILED -> failedCount++;
                case STATE_UPDATE_FAILED -> stateUpdateFailedCount++;
            }
        }

        return new RelayBatchResult(
                claimedOutboxes.size(),
                publishedCount,
                retryScheduledCount,
                failedCount,
                stateUpdateFailedCount
        );
    }

    private ItemResult relayClaimedOutboxSafely(
            ClaimedOutbox claimedOutbox
    ) {
        if (claimedOutbox == null) {
            log.error(
                    "일일 피드백 Outbox 처리 실패: "
                            + "result={}, exceptionType={}",
                    ItemResult.STATE_UPDATE_FAILED,
                    NullPointerException.class.getSimpleName()
            );

            return ItemResult.STATE_UPDATE_FAILED;
        }

        try {
            return relayClaimedOutbox(claimedOutbox);
        } catch (RuntimeException exception) {
            logFailure(
                    claimedOutbox,
                    ItemResult.STATE_UPDATE_FAILED,
                    exceptionType(exception)
            );

            return ItemResult.STATE_UPDATE_FAILED;
        }
    }

    private ItemResult relayClaimedOutbox(
            ClaimedOutbox claimedOutbox
    ) {
        AiEvent.DailyFeedbackGeneratedEvent event;

        try {
            event = restoreAndValidateEvent(claimedOutbox);
        } catch (InvalidOutboxPayloadException exception) {
            return recordPermanentPayloadFailure(
                    claimedOutbox,
                    exception
            );
        }

        String publicationAttemptId =
                createPublicationAttemptId(claimedOutbox);

        try {
            aiNotificationProducer.sendDailyFeedbackConfirmed(
                    event,
                    publicationAttemptId
            );
        } catch (RuntimeException exception) {
            return recordPublicationFailure(
                    claimedOutbox,
                    exception
            );
        }

        return markPublished(claimedOutbox);
    }

    private AiEvent.DailyFeedbackGeneratedEvent
    restoreAndValidateEvent(
            ClaimedOutbox claimedOutbox
    ) {
        AiEvent.DailyFeedbackGeneratedEvent event;

        try {
            event = objectMapper.treeToValue(
                    claimedOutbox.payload(),
                    AiEvent.DailyFeedbackGeneratedEvent.class
            );
        } catch (JsonProcessingException | RuntimeException exception) {
            throw new InvalidOutboxPayloadException();
        }

        if (event == null
                || event.eventId() == null
                || !claimedOutbox.eventId().equals(event.eventId())
                || event.userId() <= 0
                || event.cultivationId() <= 0
                || isBlank(event.cultivationName())
                || isBlank(event.feedbackUrl())
                || isBlank(event.feedbackContent())
                || event.occurredAt() == null) {
            throw new InvalidOutboxPayloadException();
        }

        return event;
    }

    private String createPublicationAttemptId(
            ClaimedOutbox claimedOutbox
    ) {
        return claimedOutbox.outboxId()
                + ":"
                + claimedOutbox.eventId()
                + ":"
                + claimedOutbox.attemptCount();
    }

    private ItemResult recordPermanentPayloadFailure(
            ClaimedOutbox claimedOutbox,
            RuntimeException payloadException
    ) {
        String failureType = exceptionType(payloadException);

        try {
            LocalDateTime failureAt =
                    nowNotBefore(claimedOutbox.claimedAt());

            FailureDisposition disposition =
                    dailyFeedbackOutboxStateService.recordFailure(
                            claimedOutbox.outboxId(),
                            claimedOutbox.attemptCount(),
                            failureAt,
                            null,
                            claimedOutbox.attemptCount(),
                            failureType
                    );

            ItemResult itemResult = mapDisposition(disposition);

            logFailure(
                    claimedOutbox,
                    itemResult,
                    failureType
            );

            return itemResult;
        } catch (RuntimeException stateException) {
            logFailure(
                    claimedOutbox,
                    ItemResult.STATE_UPDATE_FAILED,
                    exceptionType(stateException)
            );

            return ItemResult.STATE_UPDATE_FAILED;
        }
    }

    private ItemResult recordPublicationFailure(
            ClaimedOutbox claimedOutbox,
            RuntimeException publicationException
    ) {
        String failureType = exceptionType(publicationException);

        try {
            LocalDateTime failureAt =
                    nowNotBefore(claimedOutbox.claimedAt());

            int maxAttempts =
                    dailyFeedbackOutboxProperties.getMaxAttempts();

            LocalDateTime nextAttemptAt = null;

            if (claimedOutbox.attemptCount() < maxAttempts) {
                Duration backoff =
                        calculateBackoff(
                                claimedOutbox.attemptCount()
                        );

                nextAttemptAt = failureAt.plus(backoff);
            }

            FailureDisposition disposition =
                    dailyFeedbackOutboxStateService.recordFailure(
                            claimedOutbox.outboxId(),
                            claimedOutbox.attemptCount(),
                            failureAt,
                            nextAttemptAt,
                            maxAttempts,
                            failureType
                    );

            ItemResult itemResult = mapDisposition(disposition);

            logFailure(
                    claimedOutbox,
                    itemResult,
                    failureType
            );

            return itemResult;
        } catch (RuntimeException stateException) {
            logFailure(
                    claimedOutbox,
                    ItemResult.STATE_UPDATE_FAILED,
                    exceptionType(stateException)
            );

            return ItemResult.STATE_UPDATE_FAILED;
        }
    }

    private ItemResult markPublished(
            ClaimedOutbox claimedOutbox
    ) {
        try {
            LocalDateTime publishedAt =
                    nowNotBefore(claimedOutbox.claimedAt());

            dailyFeedbackOutboxStateService.markPublished(
                    claimedOutbox.outboxId(),
                    claimedOutbox.attemptCount(),
                    publishedAt
            );

            log.info(
                    "일일 피드백 Outbox 처리 완료: "
                            + "outboxId={}, eventId={}, dailyFeedbackId={}, "
                            + "attemptCount={}, result={}",
                    claimedOutbox.outboxId(),
                    claimedOutbox.eventId(),
                    claimedOutbox.dailyFeedbackId(),
                    claimedOutbox.attemptCount(),
                    ItemResult.PUBLISHED
            );

            return ItemResult.PUBLISHED;
        } catch (RuntimeException stateException) {
            logFailure(
                    claimedOutbox,
                    ItemResult.STATE_UPDATE_FAILED,
                    exceptionType(stateException)
            );

            return ItemResult.STATE_UPDATE_FAILED;
        }
    }

    private ItemResult mapDisposition(
            FailureDisposition disposition
    ) {
        if (disposition == null) {
            throw new IllegalStateException(
                    "Outbox 실패 처리 결과가 null입니다."
            );
        }

        return switch (disposition) {
            case RETRY_SCHEDULED -> ItemResult.RETRY_SCHEDULED;
            case FAILED -> ItemResult.FAILED;
        };
    }

    private LocalDateTime nowNotBefore(
            LocalDateTime lowerBound
    ) {
        LocalDateTime now = LocalDateTime.now(clock);

        return now.isBefore(lowerBound)
                ? lowerBound
                : now;
    }

    private Duration calculateBackoff(int attemptCount) {
        Duration initialBackoff =
                dailyFeedbackOutboxProperties.getInitialBackoff();

        Duration maxBackoff =
                dailyFeedbackOutboxProperties.getMaxBackoff();

        if (initialBackoff.compareTo(maxBackoff) >= 0) {
            return maxBackoff;
        }

        Duration backoff = initialBackoff;
        int remainingDoublings = attemptCount - 1;

        while (remainingDoublings > 0) {
            try {
                backoff = backoff.multipliedBy(2);
            } catch (ArithmeticException exception) {
                return maxBackoff;
            }

            if (backoff.compareTo(maxBackoff) >= 0) {
                return maxBackoff;
            }

            remainingDoublings--;
        }

        return backoff;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String exceptionType(RuntimeException exception) {
        String simpleName =
                exception.getClass().getSimpleName();

        if (simpleName == null || simpleName.isBlank()) {
            return RuntimeException.class.getSimpleName();
        }

        return simpleName;
    }

    private void logFailure(
            ClaimedOutbox claimedOutbox,
            ItemResult itemResult,
            String exceptionType
    ) {
        log.warn("일일 피드백 Outbox 처리 실패: outboxId={}, eventId={}, dailyFeedbackId={}, "
                        + "attemptCount={}, result={}, exceptionType={}",
                claimedOutbox.outboxId(),
                claimedOutbox.eventId(),
                claimedOutbox.dailyFeedbackId(),
                claimedOutbox.attemptCount(),
                itemResult,
                exceptionType
        );
    }

    private enum ItemResult {

        PUBLISHED,
        RETRY_SCHEDULED,
        FAILED,
        STATE_UPDATE_FAILED
    }

    private static final class InvalidOutboxPayloadException
            extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private InvalidOutboxPayloadException() {
            super(INVALID_PAYLOAD_MESSAGE);
        }
    }

    /**
     * 한 번의 Relay 배치에서 선점한 Outbox 수와 각 처리 결과의
     * 개수를 나타냅니다.
     *
     * <p>모든 개수는 음수가 아니며, 발행 성공·재시도 예약·최종 실패·
     * 상태 저장 실패 개수의 합은 전체 선점 개수와 일치합니다.</p>
     *
     * @param claimedCount 선점한 전체 Outbox 수
     * @param publishedCount 발행과 PUBLISHED 상태 저장을 완료한 수
     * @param retryScheduledCount 다음 발행 시도를 예약한 수
     * @param failedCount 최종 FAILED 상태로 변경한 수
     * @param stateUpdateFailedCount 상태 기록에 실패하여 복구 대상으로 남은 수
     */
    public record RelayBatchResult(
            int claimedCount,
            int publishedCount,
            int retryScheduledCount,
            int failedCount,
            int stateUpdateFailedCount
    ) {

        public RelayBatchResult {
            if (claimedCount < 0 || publishedCount < 0 || retryScheduledCount < 0
                    || failedCount < 0 || stateUpdateFailedCount < 0) {
                throw new IllegalArgumentException("Relay 배치 결과 개수는 음수일 수 없습니다.");
            }

            long processedCount = (long) publishedCount + retryScheduledCount + failedCount + stateUpdateFailedCount;

            if (processedCount != claimedCount) {
                throw new IllegalArgumentException("Relay 처리 결과의 합은 선점 개수와 같아야 합니다.");
            }
        }
    }
}
