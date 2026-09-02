package site.yesaido.ai_server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import site.yesaido.ai_server.config.DailyFeedbackOutboxProperties;
import site.yesaido.ai_server.rabbitmq.AiNotificationProducer;
import site.yesaido.ai_server.rabbitmq.event.AiEvent.DailyFeedbackGeneratedEvent;
import site.yesaido.ai_server.service.DailyFeedbackOutboxClaimService.ClaimedOutbox;
import site.yesaido.ai_server.service.DailyFeedbackOutboxRelayService.RelayBatchResult;
import site.yesaido.ai_server.service.DailyFeedbackOutboxStateService.FailureDisposition;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyFeedbackOutboxRelayServiceTest {

    private static final int BATCH_SIZE = 20;

    private static final int MAX_ATTEMPTS = 3;

    private static final long FIRST_OUTBOX_ID = 100L;

    private static final long SECOND_OUTBOX_ID = 101L;

    private static final long FIRST_DAILY_FEEDBACK_ID = 1001L;

    private static final long SECOND_DAILY_FEEDBACK_ID = 1002L;

    private static final long USER_ID = 1L;

    private static final long CULTIVATION_ID = 10L;

    private static final UUID FIRST_EVENT_ID = UUID.fromString(
            "123e4567-e89b-12d3-a456-426614174000"
    );

    private static final UUID SECOND_EVENT_ID = UUID.fromString(
            "223e4567-e89b-12d3-a456-426614174000"
    );

    private static final UUID DIFFERENT_EVENT_ID = UUID.fromString(
            "323e4567-e89b-12d3-a456-426614174000"
    );

    private static final Instant FIXED_INSTANT =
            Instant.parse("2026-09-03T00:00:00Z");

    private static final ZoneId SEOUL_ZONE =
            ZoneId.of("Asia/Seoul");

    private static final LocalDateTime FIXED_NOW =
            LocalDateTime.of(2026, 9, 3, 9, 0);

    private static final LocalDateTime DEFAULT_CLAIMED_AT =
            FIXED_NOW.minusMinutes(1);

    private static final OffsetDateTime OCCURRED_AT =
            OffsetDateTime.parse("2026-09-03T08:55:00+09:00");

    private static final Duration INITIAL_BACKOFF =
            Duration.ofSeconds(30);

    private static final Duration MAX_BACKOFF =
            Duration.ofMinutes(5);

    private static final String CULTIVATION_NAME =
            "테스트 재배지";

    private static final String FEEDBACK_URL =
            "/cultivations/10/daily-feedbacks/2026-09-02";

    private static final String FEEDBACK_CONTENT =
            "오늘의 재배 환경이 안정적으로 유지되었습니다.";

    private static final String INVALID_PAYLOAD_EXCEPTION_TYPE =
            "InvalidOutboxPayloadException";

    private static final String AMQP_EXCEPTION_TYPE =
            "AmqpException";

    @Mock
    private DailyFeedbackOutboxClaimService
            dailyFeedbackOutboxClaimService;

    @Mock
    private DailyFeedbackOutboxStateService
            dailyFeedbackOutboxStateService;

    @Mock
    private AiNotificationProducer aiNotificationProducer;

    @Mock
    private DailyFeedbackOutboxProperties
            dailyFeedbackOutboxProperties;

    private ObjectMapper objectMapper;

    private DailyFeedbackOutboxRelayService service;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(
                        SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
                )
                .build();

        Clock clock = Clock.fixed(
                FIXED_INSTANT,
                SEOUL_ZONE
        );

        service = new DailyFeedbackOutboxRelayService(
                dailyFeedbackOutboxClaimService,
                dailyFeedbackOutboxStateService,
                aiNotificationProducer,
                objectMapper,
                dailyFeedbackOutboxProperties,
                clock
        );
    }

    @Test
    @DisplayName("선점할 Outbox가 없으면 모든 처리 결과 개수가 0이다")
    void returnEmptyResultWhenThereIsNoPendingOutbox() {
        // 준비
        stubClaimedOutboxes(List.of());

        // 실행
        RelayBatchResult result = service.relayPending();

        // 검증
        assertRelayCounts(
                result,
                0,
                0,
                0,
                0,
                0
        );

        verify(dailyFeedbackOutboxProperties)
                .getBatchSize();

        verify(dailyFeedbackOutboxClaimService)
                .claimPending(
                        FIXED_NOW,
                        BATCH_SIZE
                );

        verifyNoInteractions(
                aiNotificationProducer,
                dailyFeedbackOutboxStateService
        );
    }

    @Test
    @DisplayName("RabbitMQ 발행에 성공하면 고정 현재 시각으로 PUBLISHED 처리한다")
    void publishClaimedOutboxSuccessfully() {
        // 준비
        DailyFeedbackGeneratedEvent event =
                dailyFeedbackEvent(
                        FIRST_EVENT_ID,
                        FEEDBACK_CONTENT
                );

        ClaimedOutbox claimedOutbox = claimedOutbox(
                FIRST_OUTBOX_ID,
                FIRST_DAILY_FEEDBACK_ID,
                FIRST_EVENT_ID,
                1,
                event,
                DEFAULT_CLAIMED_AT
        );

        stubClaimedOutboxes(List.of(claimedOutbox));

        ArgumentCaptor<DailyFeedbackGeneratedEvent> eventCaptor =
                ArgumentCaptor.forClass(
                        DailyFeedbackGeneratedEvent.class
                );

        ArgumentCaptor<String> publicationAttemptIdCaptor =
                ArgumentCaptor.forClass(String.class);

        // 실행
        RelayBatchResult result = service.relayPending();

        // 검증
        verify(aiNotificationProducer)
                .sendDailyFeedbackConfirmed(
                        eventCaptor.capture(),
                        publicationAttemptIdCaptor.capture()
                );

        DailyFeedbackGeneratedEvent publishedEvent =
                eventCaptor.getValue();

        assertThat(publishedEvent.eventId())
                .isEqualTo(FIRST_EVENT_ID);

        assertThat(publishedEvent.userId())
                .isEqualTo(USER_ID);

        assertThat(publishedEvent.cultivationId())
                .isEqualTo(CULTIVATION_ID);

        assertThat(publishedEvent.cultivationName())
                .isEqualTo(CULTIVATION_NAME);

        assertThat(publishedEvent.feedbackContent())
                .isEqualTo(FEEDBACK_CONTENT);

        assertThat(publishedEvent.occurredAt())
                .isNotNull();

        assertThat(publicationAttemptIdCaptor.getValue())
                .isEqualTo(
                        FIRST_OUTBOX_ID
                                + ":"
                                + FIRST_EVENT_ID
                                + ":1"
                );

        verify(dailyFeedbackOutboxStateService)
                .markPublished(
                        FIRST_OUTBOX_ID,
                        1,
                        FIXED_NOW
                );

        verifyNoMoreInteractions(
                dailyFeedbackOutboxStateService
        );

        assertRelayCounts(
                result,
                1,
                1,
                0,
                0,
                0
        );
    }

    @Test
    @DisplayName("Outbox 선점 실패는 같은 예외를 상위로 전파한다")
    void propagateClaimFailure() {
        // 준비
        RuntimeException claimFailure =
                new RuntimeException("claim failure");

        when(dailyFeedbackOutboxProperties.getBatchSize())
                .thenReturn(BATCH_SIZE);

        when(
                dailyFeedbackOutboxClaimService.claimPending(
                        FIXED_NOW,
                        BATCH_SIZE
                )
        ).thenThrow(claimFailure);

        // 실행
        RuntimeException propagatedException =
                catchThrowableOfType(
                        RuntimeException.class,
                        service::relayPending
                );

        // 검증
        assertThat(propagatedException)
                .isSameAs(claimFailure);

        verify(dailyFeedbackOutboxClaimService)
                .claimPending(
                        FIXED_NOW,
                        BATCH_SIZE
                );

        verifyNoInteractions(
                aiNotificationProducer,
                dailyFeedbackOutboxStateService
        );
    }

    @Test
    @DisplayName("Payload eventId가 선점 정보와 다르면 발행 없이 즉시 FAILED 처리한다")
    void failPermanentlyWhenPayloadEventIdDoesNotMatch() {
        // 준비
        int attemptCount = 2;

        DailyFeedbackGeneratedEvent mismatchedEvent =
                dailyFeedbackEvent(
                        DIFFERENT_EVENT_ID,
                        FEEDBACK_CONTENT
                );

        ClaimedOutbox claimedOutbox = claimedOutbox(
                FIRST_OUTBOX_ID,
                FIRST_DAILY_FEEDBACK_ID,
                FIRST_EVENT_ID,
                attemptCount,
                mismatchedEvent,
                DEFAULT_CLAIMED_AT
        );

        stubClaimedOutboxes(List.of(claimedOutbox));

        when(
                dailyFeedbackOutboxStateService.recordFailure(
                        FIRST_OUTBOX_ID,
                        attemptCount,
                        FIXED_NOW,
                        null,
                        attemptCount,
                        INVALID_PAYLOAD_EXCEPTION_TYPE
                )
        ).thenReturn(FailureDisposition.FAILED);

        // 실행
        RelayBatchResult result = service.relayPending();

        // 검증
        verifyNoInteractions(aiNotificationProducer);

        verify(dailyFeedbackOutboxStateService)
                .recordFailure(
                        FIRST_OUTBOX_ID,
                        attemptCount,
                        FIXED_NOW,
                        null,
                        attemptCount,
                        INVALID_PAYLOAD_EXCEPTION_TYPE
                );

        verifyNoMoreInteractions(
                dailyFeedbackOutboxStateService
        );

        assertRelayCounts(
                result,
                1,
                0,
                0,
                1,
                0
        );
    }

    @Test
    @DisplayName("Payload의 피드백 본문이 공백이면 발행 없이 즉시 FAILED 처리한다")
    void failPermanentlyWhenFeedbackContentIsBlank() {
        // 준비
        int attemptCount = 1;

        DailyFeedbackGeneratedEvent invalidEvent =
                dailyFeedbackEvent(
                        FIRST_EVENT_ID,
                        "   "
                );

        ClaimedOutbox claimedOutbox = claimedOutbox(
                FIRST_OUTBOX_ID,
                FIRST_DAILY_FEEDBACK_ID,
                FIRST_EVENT_ID,
                attemptCount,
                invalidEvent,
                DEFAULT_CLAIMED_AT
        );

        stubClaimedOutboxes(List.of(claimedOutbox));

        when(
                dailyFeedbackOutboxStateService.recordFailure(
                        FIRST_OUTBOX_ID,
                        attemptCount,
                        FIXED_NOW,
                        null,
                        attemptCount,
                        INVALID_PAYLOAD_EXCEPTION_TYPE
                )
        ).thenReturn(FailureDisposition.FAILED);

        // 실행
        RelayBatchResult result = service.relayPending();

        // 검증
        verifyNoInteractions(aiNotificationProducer);

        verify(dailyFeedbackOutboxStateService)
                .recordFailure(
                        FIRST_OUTBOX_ID,
                        attemptCount,
                        FIXED_NOW,
                        null,
                        attemptCount,
                        INVALID_PAYLOAD_EXCEPTION_TYPE
                );

        verifyNoMoreInteractions(
                dailyFeedbackOutboxStateService
        );

        assertRelayCounts(
                result,
                1,
                0,
                0,
                1,
                0
        );
    }

    @Test
    @DisplayName("첫 번째 발행 실패에는 initialBackoff으로 재시도를 예약한다")
    void scheduleRetryWithInitialBackoffAfterFirstFailure() {
        // 준비
        int attemptCount = 1;

        DailyFeedbackGeneratedEvent event =
                dailyFeedbackEvent(
                        FIRST_EVENT_ID,
                        FEEDBACK_CONTENT
                );

        ClaimedOutbox claimedOutbox = claimedOutbox(
                FIRST_OUTBOX_ID,
                FIRST_DAILY_FEEDBACK_ID,
                FIRST_EVENT_ID,
                attemptCount,
                event,
                DEFAULT_CLAIMED_AT
        );

        AmqpException publishingFailure =
                new AmqpException("publishing failure");

        stubClaimedOutboxes(List.of(claimedOutbox));
        stubRetryPolicy(MAX_ATTEMPTS);

        doThrow(publishingFailure)
                .when(aiNotificationProducer)
                .sendDailyFeedbackConfirmed(
                        any(DailyFeedbackGeneratedEvent.class),
                        eq(publicationAttemptId(claimedOutbox))
                );

        when(
                dailyFeedbackOutboxStateService.recordFailure(
                        FIRST_OUTBOX_ID,
                        attemptCount,
                        FIXED_NOW,
                        FIXED_NOW.plus(INITIAL_BACKOFF),
                        MAX_ATTEMPTS,
                        AMQP_EXCEPTION_TYPE
                )
        ).thenReturn(FailureDisposition.RETRY_SCHEDULED);

        // 실행
        RelayBatchResult result = service.relayPending();

        // 검증
        verify(aiNotificationProducer)
                .sendDailyFeedbackConfirmed(
                        any(DailyFeedbackGeneratedEvent.class),
                        eq(publicationAttemptId(claimedOutbox))
                );

        verify(dailyFeedbackOutboxStateService)
                .recordFailure(
                        FIRST_OUTBOX_ID,
                        attemptCount,
                        FIXED_NOW,
                        FIXED_NOW.plusSeconds(30),
                        MAX_ATTEMPTS,
                        AMQP_EXCEPTION_TYPE
                );

        verifyNoMoreInteractions(
                dailyFeedbackOutboxStateService
        );

        assertRelayCounts(
                result,
                1,
                0,
                1,
                0,
                0
        );
    }

    @ParameterizedTest(
            name = "attemptCount={0}, expectedBackoff={1}"
    )
    @MethodSource("exponentialBackoffCases")
    @DisplayName("발행 시도 횟수에 따라 지수 백오프를 적용하고 최대값으로 제한한다")
    void applyCappedExponentialBackoff(
            int attemptCount,
            Duration expectedBackoff
    ) {
        // 준비
        int maxAttempts = 10;

        DailyFeedbackGeneratedEvent event =
                dailyFeedbackEvent(
                        FIRST_EVENT_ID,
                        FEEDBACK_CONTENT
                );

        ClaimedOutbox claimedOutbox = claimedOutbox(
                FIRST_OUTBOX_ID,
                FIRST_DAILY_FEEDBACK_ID,
                FIRST_EVENT_ID,
                attemptCount,
                event,
                DEFAULT_CLAIMED_AT
        );

        AmqpException publishingFailure =
                new AmqpException("publishing failure");

        LocalDateTime expectedNextAttemptAt =
                FIXED_NOW.plus(expectedBackoff);

        stubClaimedOutboxes(List.of(claimedOutbox));
        stubRetryPolicy(maxAttempts);

        doThrow(publishingFailure)
                .when(aiNotificationProducer)
                .sendDailyFeedbackConfirmed(
                        any(DailyFeedbackGeneratedEvent.class),
                        eq(publicationAttemptId(claimedOutbox))
                );

        when(
                dailyFeedbackOutboxStateService.recordFailure(
                        FIRST_OUTBOX_ID,
                        attemptCount,
                        FIXED_NOW,
                        expectedNextAttemptAt,
                        maxAttempts,
                        AMQP_EXCEPTION_TYPE
                )
        ).thenReturn(FailureDisposition.RETRY_SCHEDULED);

        // 실행
        RelayBatchResult result = service.relayPending();

        // 검증
        verify(dailyFeedbackOutboxStateService)
                .recordFailure(
                        FIRST_OUTBOX_ID,
                        attemptCount,
                        FIXED_NOW,
                        expectedNextAttemptAt,
                        maxAttempts,
                        AMQP_EXCEPTION_TYPE
                );

        verifyNoMoreInteractions(
                dailyFeedbackOutboxStateService
        );

        assertRelayCounts(
                result,
                1,
                0,
                1,
                0,
                0
        );
    }

    @Test
    @DisplayName("최대 발행 시도 횟수에 도달하면 다음 시각 없이 FAILED 처리한다")
    void failPermanentlyWhenMaximumAttemptsAreReached() {
        // 준비
        int attemptCount = MAX_ATTEMPTS;

        DailyFeedbackGeneratedEvent event =
                dailyFeedbackEvent(
                        FIRST_EVENT_ID,
                        FEEDBACK_CONTENT
                );

        ClaimedOutbox claimedOutbox = claimedOutbox(
                FIRST_OUTBOX_ID,
                FIRST_DAILY_FEEDBACK_ID,
                FIRST_EVENT_ID,
                attemptCount,
                event,
                DEFAULT_CLAIMED_AT
        );

        AmqpException publishingFailure =
                new AmqpException("publishing failure");

        stubClaimedOutboxes(List.of(claimedOutbox));

        when(dailyFeedbackOutboxProperties.getMaxAttempts())
                .thenReturn(MAX_ATTEMPTS);

        doThrow(publishingFailure)
                .when(aiNotificationProducer)
                .sendDailyFeedbackConfirmed(
                        any(DailyFeedbackGeneratedEvent.class),
                        eq(publicationAttemptId(claimedOutbox))
                );

        when(
                dailyFeedbackOutboxStateService.recordFailure(
                        FIRST_OUTBOX_ID,
                        attemptCount,
                        FIXED_NOW,
                        null,
                        MAX_ATTEMPTS,
                        AMQP_EXCEPTION_TYPE
                )
        ).thenReturn(FailureDisposition.FAILED);

        // 실행
        RelayBatchResult result = service.relayPending();

        // 검증
        verify(dailyFeedbackOutboxStateService)
                .recordFailure(
                        FIRST_OUTBOX_ID,
                        attemptCount,
                        FIXED_NOW,
                        null,
                        MAX_ATTEMPTS,
                        AMQP_EXCEPTION_TYPE
                );

        verifyNoMoreInteractions(
                dailyFeedbackOutboxStateService
        );

        assertRelayCounts(
                result,
                1,
                0,
                0,
                1,
                0
        );
    }

    @Test
    @DisplayName("발행 성공 후 PUBLISHED 저장이 실패하면 SENDING 복구 대상으로 남긴다")
    void leaveSendingWhenPublishedStateUpdateFails() {
        // 준비
        DailyFeedbackGeneratedEvent event =
                dailyFeedbackEvent(
                        FIRST_EVENT_ID,
                        FEEDBACK_CONTENT
                );

        ClaimedOutbox claimedOutbox = claimedOutbox(
                FIRST_OUTBOX_ID,
                FIRST_DAILY_FEEDBACK_ID,
                FIRST_EVENT_ID,
                1,
                event,
                DEFAULT_CLAIMED_AT
        );

        RuntimeException stateFailure =
                new RuntimeException("state update failure");

        stubClaimedOutboxes(List.of(claimedOutbox));

        doThrow(stateFailure)
                .when(dailyFeedbackOutboxStateService)
                .markPublished(
                        FIRST_OUTBOX_ID,
                        1,
                        FIXED_NOW
                );

        // 실행
        RelayBatchResult result = service.relayPending();

        // 검증
        verify(aiNotificationProducer)
                .sendDailyFeedbackConfirmed(
                        any(DailyFeedbackGeneratedEvent.class),
                        eq(publicationAttemptId(claimedOutbox))
                );

        verify(dailyFeedbackOutboxStateService)
                .markPublished(
                        FIRST_OUTBOX_ID,
                        1,
                        FIXED_NOW
                );

        verifyNoMoreInteractions(
                dailyFeedbackOutboxStateService
        );

        assertRelayCounts(
                result,
                1,
                0,
                0,
                0,
                1
        );
    }

    @Test
    @DisplayName("첫 Outbox의 실패 상태 기록 오류가 다음 Outbox 발행을 막지 않는다")
    void continueWithNextOutboxWhenFailureStateUpdateFails() {
        // 준비
        DailyFeedbackGeneratedEvent firstEvent =
                dailyFeedbackEvent(
                        FIRST_EVENT_ID,
                        FEEDBACK_CONTENT
                );

        DailyFeedbackGeneratedEvent secondEvent =
                dailyFeedbackEvent(
                        SECOND_EVENT_ID,
                        FEEDBACK_CONTENT
                );

        ClaimedOutbox firstClaimedOutbox = claimedOutbox(
                FIRST_OUTBOX_ID,
                FIRST_DAILY_FEEDBACK_ID,
                FIRST_EVENT_ID,
                1,
                firstEvent,
                DEFAULT_CLAIMED_AT
        );

        ClaimedOutbox secondClaimedOutbox = claimedOutbox(
                SECOND_OUTBOX_ID,
                SECOND_DAILY_FEEDBACK_ID,
                SECOND_EVENT_ID,
                1,
                secondEvent,
                DEFAULT_CLAIMED_AT
        );

        AmqpException publishingFailure =
                new AmqpException("publishing failure");

        RuntimeException stateFailure =
                new RuntimeException("state update failure");

        stubClaimedOutboxes(
                List.of(
                        firstClaimedOutbox,
                        secondClaimedOutbox
                )
        );

        stubRetryPolicy(MAX_ATTEMPTS);

        doThrow(publishingFailure)
                .when(aiNotificationProducer)
                .sendDailyFeedbackConfirmed(
                        any(DailyFeedbackGeneratedEvent.class),
                        eq(publicationAttemptId(firstClaimedOutbox))
                );

        when(
                dailyFeedbackOutboxStateService.recordFailure(
                        FIRST_OUTBOX_ID,
                        1,
                        FIXED_NOW,
                        FIXED_NOW.plus(INITIAL_BACKOFF),
                        MAX_ATTEMPTS,
                        AMQP_EXCEPTION_TYPE
                )
        ).thenThrow(stateFailure);

        // 실행
        RelayBatchResult result = service.relayPending();

        // 검증
        verify(aiNotificationProducer)
                .sendDailyFeedbackConfirmed(
                        any(DailyFeedbackGeneratedEvent.class),
                        eq(publicationAttemptId(firstClaimedOutbox))
                );

        verify(aiNotificationProducer)
                .sendDailyFeedbackConfirmed(
                        any(DailyFeedbackGeneratedEvent.class),
                        eq(publicationAttemptId(secondClaimedOutbox))
                );

        verify(dailyFeedbackOutboxStateService)
                .recordFailure(
                        FIRST_OUTBOX_ID,
                        1,
                        FIXED_NOW,
                        FIXED_NOW.plusSeconds(30),
                        MAX_ATTEMPTS,
                        AMQP_EXCEPTION_TYPE
                );

        verify(dailyFeedbackOutboxStateService)
                .markPublished(
                        SECOND_OUTBOX_ID,
                        1,
                        FIXED_NOW
                );

        verifyNoMoreInteractions(
                dailyFeedbackOutboxStateService,
                aiNotificationProducer
        );

        assertRelayCounts(
                result,
                2,
                1,
                0,
                0,
                1
        );
    }

    @Test
    @DisplayName("현재 시각이 claimedAt보다 이전이면 publishedAt을 claimedAt으로 보정한다")
    void clampPublishedAtToClaimedAtWhenClockMovesBackwards() {
        // 준비
        LocalDateTime futureClaimedAt =
                FIXED_NOW.plusMinutes(1);

        DailyFeedbackGeneratedEvent event =
                dailyFeedbackEvent(
                        FIRST_EVENT_ID,
                        FEEDBACK_CONTENT
                );

        ClaimedOutbox claimedOutbox = claimedOutbox(
                FIRST_OUTBOX_ID,
                FIRST_DAILY_FEEDBACK_ID,
                FIRST_EVENT_ID,
                1,
                event,
                futureClaimedAt
        );

        stubClaimedOutboxes(List.of(claimedOutbox));

        // 실행
        RelayBatchResult result = service.relayPending();

        // 검증
        verify(aiNotificationProducer)
                .sendDailyFeedbackConfirmed(
                        any(DailyFeedbackGeneratedEvent.class),
                        eq(publicationAttemptId(claimedOutbox))
                );

        verify(dailyFeedbackOutboxStateService)
                .markPublished(
                        FIRST_OUTBOX_ID,
                        1,
                        futureClaimedAt
                );

        verifyNoMoreInteractions(
                dailyFeedbackOutboxStateService
        );

        assertRelayCounts(
                result,
                1,
                1,
                0,
                0,
                0
        );
    }

    @ParameterizedTest(name = "음수 필드: {5}")
    @MethodSource("negativeRelayBatchResultCases")
    @DisplayName("RelayBatchResult는 음수 개수를 거부한다")
    void rejectNegativeRelayBatchResultCount(
            int claimedCount,
            int publishedCount,
            int retryScheduledCount,
            int failedCount,
            int stateUpdateFailedCount,
            String fieldName
    ) {
        // 준비

        // 실행
        IllegalArgumentException exception =
                catchThrowableOfType(
                        IllegalArgumentException.class,
                        () -> new RelayBatchResult(
                                claimedCount,
                                publishedCount,
                                retryScheduledCount,
                                failedCount,
                                stateUpdateFailedCount
                        )
                );

        // 검증
        assertThat(exception)
                .as(fieldName + " 음수 검증")
                .hasMessage(
                        "Relay 배치 결과 개수는 음수일 수 없습니다."
                );
    }

    @Test
    @DisplayName("RelayBatchResult는 처리 결과 합계가 선점 개수와 다르면 거부한다")
    void rejectMismatchedRelayBatchResultTotal() {
        // 준비

        // 실행
        IllegalArgumentException exception =
                catchThrowableOfType(
                        IllegalArgumentException.class,
                        () -> new RelayBatchResult(
                                2,
                                1,
                                0,
                                0,
                                0
                        )
                );

        // 검증
        assertThat(exception)
                .hasMessage(
                        "Relay 처리 결과의 합은 선점 개수와 같아야 합니다."
                );
    }

    private void stubClaimedOutboxes(
            List<ClaimedOutbox> claimedOutboxes
    ) {
        when(dailyFeedbackOutboxProperties.getBatchSize())
                .thenReturn(BATCH_SIZE);

        when(
                dailyFeedbackOutboxClaimService.claimPending(
                        FIXED_NOW,
                        BATCH_SIZE
                )
        ).thenReturn(claimedOutboxes);
    }

    private void stubRetryPolicy(int maxAttempts) {
        when(dailyFeedbackOutboxProperties.getMaxAttempts())
                .thenReturn(maxAttempts);

        when(dailyFeedbackOutboxProperties.getInitialBackoff())
                .thenReturn(INITIAL_BACKOFF);

        when(dailyFeedbackOutboxProperties.getMaxBackoff())
                .thenReturn(MAX_BACKOFF);
    }

    private DailyFeedbackGeneratedEvent dailyFeedbackEvent(
            UUID eventId,
            String feedbackContent
    ) {
        return new DailyFeedbackGeneratedEvent(
                eventId,
                USER_ID,
                CULTIVATION_ID,
                CULTIVATION_NAME,
                FEEDBACK_URL,
                feedbackContent,
                OCCURRED_AT
        );
    }

    private ClaimedOutbox claimedOutbox(
            long outboxId,
            long dailyFeedbackId,
            UUID eventId,
            int attemptCount,
            DailyFeedbackGeneratedEvent payloadEvent,
            LocalDateTime claimedAt
    ) {
        JsonNode payload =
                objectMapper.valueToTree(payloadEvent);

        return new ClaimedOutbox(
                outboxId,
                eventId,
                dailyFeedbackId,
                attemptCount,
                payload,
                claimedAt
        );
    }

    private String publicationAttemptId(
            ClaimedOutbox claimedOutbox
    ) {
        return claimedOutbox.outboxId()
                + ":"
                + claimedOutbox.eventId()
                + ":"
                + claimedOutbox.attemptCount();
    }

    private void assertRelayCounts(
            RelayBatchResult result,
            int claimedCount,
            int publishedCount,
            int retryScheduledCount,
            int failedCount,
            int stateUpdateFailedCount
    ) {
        assertThat(result.claimedCount())
                .isEqualTo(claimedCount);

        assertThat(result.publishedCount())
                .isEqualTo(publishedCount);

        assertThat(result.retryScheduledCount())
                .isEqualTo(retryScheduledCount);

        assertThat(result.failedCount())
                .isEqualTo(failedCount);

        assertThat(result.stateUpdateFailedCount())
                .isEqualTo(stateUpdateFailedCount);
    }

    private static Stream<Arguments> exponentialBackoffCases() {
        return Stream.of(
                Arguments.of(
                        2,
                        Duration.ofMinutes(1)
                ),
                Arguments.of(
                        3,
                        Duration.ofMinutes(2)
                ),
                Arguments.of(
                        4,
                        Duration.ofMinutes(4)
                ),
                Arguments.of(
                        5,
                        Duration.ofMinutes(5)
                )
        );
    }

    private static Stream<Arguments>
    negativeRelayBatchResultCases() {
        return Stream.of(
                Arguments.of(
                        -1,
                        0,
                        0,
                        0,
                        0,
                        "claimedCount"
                ),
                Arguments.of(
                        0,
                        -1,
                        0,
                        0,
                        0,
                        "publishedCount"
                ),
                Arguments.of(
                        0,
                        0,
                        -1,
                        0,
                        0,
                        "retryScheduledCount"
                ),
                Arguments.of(
                        0,
                        0,
                        0,
                        -1,
                        0,
                        "failedCount"
                ),
                Arguments.of(
                        0,
                        0,
                        0,
                        0,
                        -1,
                        "stateUpdateFailedCount"
                )
        );
    }
}
