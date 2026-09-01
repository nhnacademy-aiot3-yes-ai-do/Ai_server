package site.yesaido.ai_server.rabbitmq.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import site.yesaido.ai_server.entity.DailyFeedback;
import site.yesaido.ai_server.rabbitmq.event.AiEvent.DailyFeedbackGeneratedEvent;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class DailyFeedbackGeneratedEventFactoryTest {

    private static final Long FEEDBACK_ID = 1001L;
    private static final Long CULTIVATION_ID = 10L;
    private static final LocalDate FEEDBACK_DATE =
            LocalDate.of(2026, 9, 1);
    private static final Long OWNER_USER_ID = 1L;
    private static final LocalDateTime CREATED_AT =
            LocalDateTime.of(2026, 9, 2, 0, 5);

    private static final String CONTENT =
            "# 오늘의 재배 환경 요약\n환경이 안정적으로 유지되었습니다.";
    private static final String RAW_CULTIVATION_NAME =
            "  테스트 재배지  ";

    private static final String INVALID_CONTRACT_MESSAGE =
            "저장된 일일 피드백 계약이 올바르지 않습니다.";

    private static final String SENSITIVE_CONNECTION_VALUE =
            "https://example.invalid/image"
                    + "?X-Amz-Signature=sensitive-token";

    private DailyFeedbackGeneratedEventFactory factory;

    @BeforeEach
    void setUp() {
        factory = new DailyFeedbackGeneratedEventFactory();
    }

    @Test
    @DisplayName("저장된 일일 피드백을 결정적인 이벤트로 정확히 변환한다")
    void createEventFromPersistedFeedback() {
        // 준비
        DailyFeedback feedback = validFeedback();

        // 실행
        DailyFeedbackGeneratedEvent event =
                factory.create(feedback, OWNER_USER_ID);

        // 검증
        assertThat(event.eventId())
                .isEqualTo(
                        UUID.fromString(
                                "c6c281f8-f8f2-3300-a3bf-9237dcd104b1"
                        )
                );
        assertThat(event.userId()).isEqualTo(1L);
        assertThat(event.cultivationId()).isEqualTo(10L);
        assertThat(event.cultivationName())
                .isEqualTo("테스트 재배지");
        assertThat(event.feedbackUrl())
                .isEqualTo(
                        "/cultivations/10/daily-feedbacks/2026-09-01"
                );
        assertThat(event.feedbackContent())
                .isEqualTo(
                        "# 오늘의 재배 환경 요약\n"
                                + "환경이 안정적으로 유지되었습니다."
                );
        assertThat(event.occurredAt())
                .isEqualTo(
                        OffsetDateTime.parse(
                                "2026-09-02T00:05:00+09:00"
                        )
                );
    }

    @Test
    @DisplayName("동일한 경작지와 피드백 날짜는 다른 부가정보에도 동일한 이벤트 ID를 생성한다")
    void keepEventIdStableForSameLogicalKey() {
        // 준비
        DailyFeedback firstFeedback = createPersistedFeedback(
                1001L,
                10L,
                LocalDate.of(2026, 9, 1),
                "  첫 번째 재배지 이름  ",
                "첫 번째 피드백 내용입니다.",
                LocalDateTime.of(2026, 9, 2, 0, 5)
        );
        DailyFeedback secondFeedback = createPersistedFeedback(
                2002L,
                10L,
                LocalDate.of(2026, 9, 1),
                "  변경된 재배지 이름  ",
                "두 번째 피드백 내용입니다.",
                LocalDateTime.of(2026, 9, 3, 12, 30)
        );
        DailyFeedback differentCultivationFeedback =
                createPersistedFeedback(
                        3003L,
                        11L,
                        LocalDate.of(2026, 9, 1),
                        "다른 경작지",
                        "다른 경작지의 피드백입니다.",
                        LocalDateTime.of(2026, 9, 2, 0, 5)
                );
        DailyFeedback differentDateFeedback =
                createPersistedFeedback(
                        4004L,
                        10L,
                        LocalDate.of(2026, 9, 2),
                        "테스트 재배지",
                        "다른 날짜의 피드백입니다.",
                        LocalDateTime.of(2026, 9, 3, 0, 5)
                );

        // 실행
        DailyFeedbackGeneratedEvent firstEvent =
                factory.create(firstFeedback, 1L);
        DailyFeedbackGeneratedEvent secondEvent =
                factory.create(secondFeedback, 99L);
        DailyFeedbackGeneratedEvent differentCultivationEvent =
                factory.create(differentCultivationFeedback, 1L);
        DailyFeedbackGeneratedEvent differentDateEvent =
                factory.create(differentDateFeedback, 1L);

        // 검증
        assertThat(firstEvent.eventId())
                .isEqualTo(secondEvent.eventId())
                .isEqualTo(
                        UUID.fromString(
                                "c6c281f8-f8f2-3300-a3bf-9237dcd104b1"
                        )
                );

        assertThat(differentCultivationEvent.eventId())
                .isNotEqualTo(firstEvent.eventId());
        assertThat(differentDateEvent.eventId())
                .isNotEqualTo(firstEvent.eventId());
    }

    @Test
    @DisplayName("feedback이 null이면 호출자 입력 오류로 거부한다")
    void rejectNullFeedback() {
        // 준비

        // 실행
        IllegalArgumentException exception = catchThrowableOfType(
                IllegalArgumentException.class,
                () -> factory.create(null, OWNER_USER_ID)
        );

        // 검증
        assertThat(exception)
                .hasMessage("feedback은 null일 수 없습니다.");
    }

    @ParameterizedTest(name = "[{index}] ownerUserId={0}")
    @NullSource
    @ValueSource(longs = {0L, -1L})
    @DisplayName("ownerUserId가 null이거나 양수가 아니면 거부한다")
    void rejectInvalidOwnerUserId(Long ownerUserId) {
        // 준비
        DailyFeedback feedback = validFeedback();

        // 실행
        IllegalArgumentException exception = catchThrowableOfType(
                IllegalArgumentException.class,
                () -> factory.create(feedback, ownerUserId)
        );

        // 검증
        assertThat(exception)
                .hasMessage("ownerUserId는 0보다 커야 합니다.");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(StoredFeedbackViolation.class)
    @DisplayName("저장된 DailyFeedback 필드 계약 위반을 거부한다")
    void rejectInvalidStoredFeedback(
            StoredFeedbackViolation violation
    ) {
        // 준비
        DailyFeedback feedback = validFeedback();
        violation.apply(feedback);

        // 실행
        IllegalStateException exception = catchThrowableOfType(
                IllegalStateException.class,
                () -> factory.create(feedback, OWNER_USER_ID)
        );

        // 검증
        assertThat(exception)
                .hasMessage(INVALID_CONTRACT_MESSAGE);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(ContextSnapshotViolation.class)
    @DisplayName("Context Snapshot 구조와 식별정보 계약 위반을 거부한다")
    void rejectInvalidContextSnapshot(
            ContextSnapshotViolation violation
    ) {
        // 준비
        DailyFeedback feedback = validFeedback();
        ObjectNode validSnapshot = validContextSnapshot(
                CULTIVATION_ID,
                FEEDBACK_DATE,
                RAW_CULTIVATION_NAME
        );
        JsonNode invalidSnapshot =
                violation.corrupt(validSnapshot);

        ReflectionTestUtils.setField(
                feedback,
                "contextSnapshot",
                invalidSnapshot
        );

        // 실행
        IllegalStateException exception = catchThrowableOfType(
                IllegalStateException.class,
                () -> factory.create(feedback, OWNER_USER_ID)
        );

        // 검증
        assertThat(exception)
                .hasMessage(INVALID_CONTRACT_MESSAGE);
    }

    @Test
    @DisplayName("계약 오류 메시지에 피드백 내용과 연결 민감정보를 노출하지 않는다")
    void doNotExposeSensitiveInformationInContractFailure() {
        // 준비
        String sensitiveContent =
                CONTENT + "\n" + SENSITIVE_CONNECTION_VALUE;

        ObjectNode snapshot = validContextSnapshot(
                CULTIVATION_ID,
                FEEDBACK_DATE,
                RAW_CULTIVATION_NAME
        );
        snapshot.put(
                "externalConnection",
                SENSITIVE_CONNECTION_VALUE
        );
        snapshot.put(
                "feedbackDate",
                "2026-09-02"
        );

        DailyFeedback feedback = createPersistedFeedback(
                FEEDBACK_ID,
                CULTIVATION_ID,
                FEEDBACK_DATE,
                sensitiveContent,
                CREATED_AT,
                snapshot
        );

        // 실행
        IllegalStateException exception = catchThrowableOfType(
                IllegalStateException.class,
                () -> factory.create(feedback, OWNER_USER_ID)
        );

        // 검증
        String message = exception.getMessage();

        assertThat(message)
                .isEqualTo(INVALID_CONTRACT_MESSAGE)
                .doesNotContain("sensitive-token")
                .doesNotContain("X-Amz-Signature")
                .doesNotContain("https://")
                .doesNotContain(sensitiveContent)
                .doesNotContain(CONTENT)
                .doesNotContain(RAW_CULTIVATION_NAME);
    }

    private DailyFeedback validFeedback() {
        return createPersistedFeedback(
                FEEDBACK_ID,
                CULTIVATION_ID,
                FEEDBACK_DATE,
                RAW_CULTIVATION_NAME,
                CONTENT,
                CREATED_AT
        );
    }

    private DailyFeedback createPersistedFeedback(
            Long feedbackId,
            Long cultivationId,
            LocalDate feedbackDate,
            String cultivationName,
            String content,
            LocalDateTime createdAt
    ) {
        ObjectNode contextSnapshot = validContextSnapshot(
                cultivationId,
                feedbackDate,
                cultivationName
        );

        return createPersistedFeedback(
                feedbackId,
                cultivationId,
                feedbackDate,
                content,
                createdAt,
                contextSnapshot
        );
    }

    private DailyFeedback createPersistedFeedback(
            Long feedbackId,
            Long cultivationId,
            LocalDate feedbackDate,
            String content,
            LocalDateTime createdAt,
            JsonNode contextSnapshot
    ) {
        DailyFeedback feedback = new DailyFeedback(
                cultivationId,
                feedbackDate,
                false,
                content,
                contextSnapshot
        );

        ReflectionTestUtils.setField(
                feedback,
                "id",
                feedbackId
        );
        ReflectionTestUtils.setField(
                feedback,
                "createdAt",
                createdAt
        );

        return feedback;
    }

    private static ObjectNode validContextSnapshot(
            Long cultivationId,
            LocalDate feedbackDate,
            String cultivationName
    ) {
        ObjectNode snapshot =
                JsonNodeFactory.instance.objectNode();

        snapshot.put(
                "cultivationId",
                cultivationId
        );
        snapshot.put(
                "feedbackDate",
                feedbackDate.toString()
        );

        ObjectNode cultivationDetail =
                snapshot.putObject("cultivationDetail");

        cultivationDetail.put(
                "cultivationId",
                cultivationId
        );
        cultivationDetail.put(
                "name",
                cultivationName
        );

        return snapshot;
    }

    private static ObjectNode cultivationDetail(
            ObjectNode snapshot
    ) {
        JsonNode detailNode =
                snapshot.get("cultivationDetail");

        if (detailNode instanceof ObjectNode detailObject) {
            return detailObject;
        }

        throw new IllegalStateException(
                "정상 테스트 Snapshot의 cultivationDetail이 "
                        + "JSON object가 아닙니다."
        );
    }

    private enum StoredFeedbackViolation {

        NULL_ID(
                "id가 null",
                feedback -> ReflectionTestUtils.setField(
                        feedback,
                        "id",
                        null
                )
        ),
        ZERO_ID(
                "id가 0",
                feedback -> ReflectionTestUtils.setField(
                        feedback,
                        "id",
                        0L
                )
        ),
        NULL_CULTIVATION_ID(
                "cultivationId가 null",
                feedback -> ReflectionTestUtils.setField(
                        feedback,
                        "cultivationId",
                        null
                )
        ),
        ZERO_CULTIVATION_ID(
                "cultivationId가 0",
                feedback -> ReflectionTestUtils.setField(
                        feedback,
                        "cultivationId",
                        0L
                )
        ),
        NULL_FEEDBACK_DATE(
                "feedbackDate가 null",
                feedback -> ReflectionTestUtils.setField(
                        feedback,
                        "feedbackDate",
                        null
                )
        ),
        NULL_CONTENT(
                "content가 null",
                feedback -> ReflectionTestUtils.setField(
                        feedback,
                        "content",
                        null
                )
        ),
        BLANK_CONTENT(
                "content가 공백",
                feedback -> ReflectionTestUtils.setField(
                        feedback,
                        "content",
                        "   "
                )
        ),
        NULL_CREATED_AT(
                "createdAt이 null",
                feedback -> ReflectionTestUtils.setField(
                        feedback,
                        "createdAt",
                        null
                )
        );

        private final String description;
        private final Consumer<DailyFeedback> violation;

        StoredFeedbackViolation(
                String description,
                Consumer<DailyFeedback> violation
        ) {
            this.description = description;
            this.violation = violation;
        }

        private void apply(DailyFeedback feedback) {
            violation.accept(feedback);
        }

        @Override
        public String toString() {
            return description;
        }
    }

    private enum ContextSnapshotViolation {

        NULL_CONTEXT(
                "contextSnapshot이 null",
                snapshot -> null
        ),
        ROOT_NOT_OBJECT(
                "root가 JSON object가 아님",
                snapshot -> JsonNodeFactory.instance.arrayNode()
        ),
        ROOT_CULTIVATION_ID_MISSING(
                "/cultivationId가 누락됨",
                snapshot -> {
                    snapshot.remove("cultivationId");
                    return snapshot;
                }
        ),
        ROOT_CULTIVATION_ID_TEXT(
                "/cultivationId가 문자열",
                snapshot -> {
                    snapshot.put("cultivationId", "10");
                    return snapshot;
                }
        ),
        ROOT_CULTIVATION_ID_MISMATCH(
                "/cultivationId가 entity cultivationId와 다름",
                snapshot -> {
                    snapshot.put("cultivationId", 11L);
                    return snapshot;
                }
        ),
        FEEDBACK_DATE_MISSING(
                "/feedbackDate가 누락됨",
                snapshot -> {
                    snapshot.remove("feedbackDate");
                    return snapshot;
                }
        ),
        FEEDBACK_DATE_NUMBER(
                "/feedbackDate가 숫자",
                snapshot -> {
                    snapshot.put("feedbackDate", 20260901);
                    return snapshot;
                }
        ),
        FEEDBACK_DATE_MISMATCH(
                "/feedbackDate가 entity 날짜와 다름",
                snapshot -> {
                    snapshot.put(
                            "feedbackDate",
                            "2026-09-02"
                    );
                    return snapshot;
                }
        ),
        CULTIVATION_DETAIL_MISSING(
                "/cultivationDetail이 누락됨",
                snapshot -> {
                    snapshot.remove("cultivationDetail");
                    return snapshot;
                }
        ),
        CULTIVATION_DETAIL_NOT_OBJECT(
                "/cultivationDetail이 object가 아님",
                snapshot -> {
                    snapshot.put(
                            "cultivationDetail",
                            "not-object"
                    );
                    return snapshot;
                }
        ),
        DETAIL_CULTIVATION_ID_MISSING(
                "/cultivationDetail/cultivationId가 누락됨",
                snapshot -> {
                    cultivationDetail(snapshot)
                            .remove("cultivationId");
                    return snapshot;
                }
        ),
        DETAIL_CULTIVATION_ID_TEXT(
                "/cultivationDetail/cultivationId가 문자열",
                snapshot -> {
                    cultivationDetail(snapshot)
                            .put("cultivationId", "10");
                    return snapshot;
                }
        ),
        DETAIL_CULTIVATION_ID_MISMATCH(
                "/cultivationDetail/cultivationId가 "
                        + "entity cultivationId와 다름",
                snapshot -> {
                    cultivationDetail(snapshot)
                            .put("cultivationId", 11L);
                    return snapshot;
                }
        ),
        DETAIL_NAME_MISSING(
                "/cultivationDetail/name이 누락됨",
                snapshot -> {
                    cultivationDetail(snapshot)
                            .remove("name");
                    return snapshot;
                }
        ),
        DETAIL_NAME_NUMBER(
                "/cultivationDetail/name이 숫자",
                snapshot -> {
                    cultivationDetail(snapshot)
                            .put("name", 123);
                    return snapshot;
                }
        ),
        DETAIL_NAME_EMPTY(
                "/cultivationDetail/name이 빈 문자열",
                snapshot -> {
                    cultivationDetail(snapshot)
                            .put("name", "");
                    return snapshot;
                }
        ),
        DETAIL_NAME_BLANK(
                "/cultivationDetail/name이 공백 문자열",
                snapshot -> {
                    cultivationDetail(snapshot)
                            .put("name", "   ");
                    return snapshot;
                }
        );

        private final String description;
        private final Function<ObjectNode, JsonNode> corruption;

        ContextSnapshotViolation(
                String description,
                Function<ObjectNode, JsonNode> corruption
        ) {
            this.description = description;
            this.corruption = corruption;
        }

        private JsonNode corrupt(ObjectNode snapshot) {
            return corruption.apply(snapshot);
        }

        @Override
        public String toString() {
            return description;
        }
    }
}
