package site.yesaido.ai_server.service;

import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.yesaido.ai_server.client.CultivationClient;
import site.yesaido.ai_server.dto.client.cultivation.CultivationDetailResponse;
import site.yesaido.ai_server.dto.daily_feedback.DailyFeedbackResponse;
import site.yesaido.ai_server.entity.DailyFeedback;
import site.yesaido.ai_server.exception.DailyFeedbackNotFoundException;
import site.yesaido.ai_server.exception.InvalidDailyFeedbackRequestException;
import site.yesaido.ai_server.exception.UnauthorizedAccessException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DailyFeedbackQueryServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long CULTIVATION_ID = 10L;
    private static final Long OTHER_CULTIVATION_ID = 20L;
    private static final Long MUSHROOM_ID = 5L;
    private static final Long DAILY_FEEDBACK_ID = 1001L;

    private static final LocalDate FEEDBACK_DATE =
            LocalDate.of(2026, 9, 2);
    private static final LocalDateTime CULTIVATION_STARTED_AT =
            LocalDateTime.of(2026, 8, 1, 9, 0);
    private static final LocalDateTime CREATED_AT =
            LocalDateTime.of(2026, 9, 3, 0, 5);

    private static final String CONTENT =
            "# 오늘의 재배 환경 요약\n환경이 안정적으로 유지되었습니다.";

    @Mock
    private CultivationClient cultivationClient;

    @Mock
    private DailyFeedbackPersistenceService dailyFeedbackPersistenceService;

    @InjectMocks
    private DailyFeedbackQueryService service;

    @Test
    @DisplayName("권한을 먼저 확인한 뒤 저장된 일일 피드백을 반환한다")
    void returnStoredFeedbackAfterCheckingAccess() {
        // 준비
        CultivationDetailResponse cultivation =
                validCultivationResponse(CULTIVATION_ID);
        DailyFeedback feedback = storedFeedback();

        given(
                cultivationClient.getCultivation(
                        USER_ID,
                        CULTIVATION_ID
                )
        ).willReturn(cultivation);

        given(
                dailyFeedbackPersistenceService.findExisting(
                        CULTIVATION_ID,
                        FEEDBACK_DATE
                )
        ).willReturn(Optional.of(feedback));

        // 실행
        DailyFeedbackResponse response = service.getDailyFeedback(
                USER_ID,
                CULTIVATION_ID,
                FEEDBACK_DATE
        );

        // 검증
        assertThat(response.dailyFeedbackId())
                .isEqualTo(DAILY_FEEDBACK_ID);
        assertThat(response.cultivationId())
                .isEqualTo(CULTIVATION_ID);
        assertThat(response.feedbackDate())
                .isEqualTo(FEEDBACK_DATE);
        assertThat(response.hasVisionAnalysis()).isTrue();
        assertThat(response.content()).isEqualTo(CONTENT);
        assertThat(response.createdAt()).isEqualTo(CREATED_AT);

        InOrder inOrder = inOrder(
                cultivationClient,
                dailyFeedbackPersistenceService
        );

        then(cultivationClient)
                .should(inOrder)
                .getCultivation(USER_ID, CULTIVATION_ID);
        then(dailyFeedbackPersistenceService)
                .should(inOrder)
                .findExisting(CULTIVATION_ID, FEEDBACK_DATE);

        inOrder.verifyNoMoreInteractions();
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    @DisplayName("유효하지 않은 조회 요청은 외부 호출 전에 거부한다")
    void rejectInvalidRequestBeforeExternalCalls(
            Long userId,
            Long cultivationId,
            LocalDate feedbackDate
    ) {
        // 준비

        // 실행
        InvalidDailyFeedbackRequestException exception =
                catchThrowableOfType(
                        InvalidDailyFeedbackRequestException.class,
                        () -> service.getDailyFeedback(
                                userId,
                                cultivationId,
                                feedbackDate
                        )
                );

        // 검증
        assertThat(exception)
                .hasMessage(
                        "일일 피드백 조회 요청값이 올바르지 않습니다."
                );

        verifyNoInteractions(
                cultivationClient,
                dailyFeedbackPersistenceService
        );
    }

    @Test
    @DisplayName("재배지 접근이 거부되면 AI의 403 예외로 변환한다")
    void convertCultivationForbiddenToUnauthorizedAccess() {
        // 준비
        FeignException.Forbidden forbidden =
                new FeignException.Forbidden(
                        "Forbidden",
                        cultivationRequest(),
                        null,
                        null
                );

        given(
                cultivationClient.getCultivation(
                        USER_ID,
                        CULTIVATION_ID
                )
        ).willThrow(forbidden);

        // 실행
        UnauthorizedAccessException exception =
                catchThrowableOfType(
                        UnauthorizedAccessException.class,
                        () -> service.getDailyFeedback(
                                USER_ID,
                                CULTIVATION_ID,
                                FEEDBACK_DATE
                        )
                );

        // 검증
        assertThat(exception).hasMessage("접근 권한이 없습니다.");

        then(cultivationClient)
                .should()
                .getCultivation(USER_ID, CULTIVATION_ID);
        verifyNoInteractions(dailyFeedbackPersistenceService);
    }

    @Test
    @DisplayName("재배지가 없으면 일일 피드백 404 예외로 변환한다")
    void convertCultivationNotFoundToDailyFeedbackNotFound() {
        // 준비
        FeignException.NotFound notFound =
                new FeignException.NotFound(
                        "Not Found",
                        cultivationRequest(),
                        null,
                        null
                );

        given(
                cultivationClient.getCultivation(
                        USER_ID,
                        CULTIVATION_ID
                )
        ).willThrow(notFound);

        // 실행
        DailyFeedbackNotFoundException exception =
                catchThrowableOfType(
                        DailyFeedbackNotFoundException.class,
                        () -> service.getDailyFeedback(
                                USER_ID,
                                CULTIVATION_ID,
                                FEEDBACK_DATE
                        )
                );

        // 검증
        assertThat(exception)
                .hasMessage(
                        "해당 재배지와 날짜의 일일 피드백이 존재하지 않습니다. "
                                + "cultivationId: 10, "
                                + "feedbackDate: 2026-09-02"
                );

        then(cultivationClient)
                .should()
                .getCultivation(USER_ID, CULTIVATION_ID);
        verifyNoInteractions(dailyFeedbackPersistenceService);
    }

    @Test
    @DisplayName("그 밖의 외부 서비스 장애는 원래 Feign 예외를 전파한다")
    void propagateOtherFeignException() {
        // 준비
        FeignException.InternalServerError externalFailure =
                new FeignException.InternalServerError(
                        "Internal Server Error",
                        cultivationRequest(),
                        null,
                        null
                );

        given(
                cultivationClient.getCultivation(
                        USER_ID,
                        CULTIVATION_ID
                )
        ).willThrow(externalFailure);

        // 실행
        FeignException.InternalServerError exception =
                catchThrowableOfType(
                        FeignException.InternalServerError.class,
                        () -> service.getDailyFeedback(
                                USER_ID,
                                CULTIVATION_ID,
                                FEEDBACK_DATE
                        )
                );

        // 검증
        assertThat(exception).isSameAs(externalFailure);

        then(cultivationClient)
                .should()
                .getCultivation(USER_ID, CULTIVATION_ID);
        verifyNoInteractions(dailyFeedbackPersistenceService);
    }

    @Test
    @DisplayName("재배지 응답이 null이면 계약 위반으로 실패한다")
    void rejectNullCultivationResponse() {
        // 준비
        given(
                cultivationClient.getCultivation(
                        USER_ID,
                        CULTIVATION_ID
                )
        ).willReturn(null);

        // 실행
        IllegalStateException exception =
                catchThrowableOfType(
                        IllegalStateException.class,
                        () -> service.getDailyFeedback(
                                USER_ID,
                                CULTIVATION_ID,
                                FEEDBACK_DATE
                        )
                );

        // 검증
        assertThat(exception)
                .hasMessageContaining("재배지 조회 응답이 null입니다");

        then(cultivationClient)
                .should()
                .getCultivation(USER_ID, CULTIVATION_ID);
        verifyNoInteractions(dailyFeedbackPersistenceService);
    }

    @Test
    @DisplayName("재배지 응답 ID가 요청과 다르면 계약 위반으로 실패한다")
    void rejectMismatchedCultivationResponseId() {
        // 준비
        CultivationDetailResponse mismatchedResponse =
                validCultivationResponse(OTHER_CULTIVATION_ID);

        given(
                cultivationClient.getCultivation(
                        USER_ID,
                        CULTIVATION_ID
                )
        ).willReturn(mismatchedResponse);

        // 실행
        IllegalStateException exception =
                catchThrowableOfType(
                        IllegalStateException.class,
                        () -> service.getDailyFeedback(
                                USER_ID,
                                CULTIVATION_ID,
                                FEEDBACK_DATE
                        )
                );

        // 검증
        assertThat(exception)
                .hasMessageContaining(
                        "재배지 조회 응답 ID가 요청과 일치하지 않습니다"
                );

        then(cultivationClient)
                .should()
                .getCultivation(USER_ID, CULTIVATION_ID);
        verifyNoInteractions(dailyFeedbackPersistenceService);
    }

    @Test
    @DisplayName("권한 확인 후 저장된 피드백이 없으면 404 예외를 발생시킨다")
    void throwNotFoundAfterAccessCheckWhenFeedbackDoesNotExist() {
        // 준비
        CultivationDetailResponse cultivation =
                validCultivationResponse(CULTIVATION_ID);

        given(
                cultivationClient.getCultivation(
                        USER_ID,
                        CULTIVATION_ID
                )
        ).willReturn(cultivation);

        given(
                dailyFeedbackPersistenceService.findExisting(
                        CULTIVATION_ID,
                        FEEDBACK_DATE
                )
        ).willReturn(Optional.empty());

        // 실행
        DailyFeedbackNotFoundException exception =
                catchThrowableOfType(
                        DailyFeedbackNotFoundException.class,
                        () -> service.getDailyFeedback(
                                USER_ID,
                                CULTIVATION_ID,
                                FEEDBACK_DATE
                        )
                );

        // 검증
        assertThat(exception)
                .hasMessage(
                        "해당 재배지와 날짜의 일일 피드백이 존재하지 않습니다. "
                                + "cultivationId: 10, "
                                + "feedbackDate: 2026-09-02"
                );

        then(cultivationClient)
                .should()
                .getCultivation(USER_ID, CULTIVATION_ID);
        then(dailyFeedbackPersistenceService)
                .should()
                .findExisting(CULTIVATION_ID, FEEDBACK_DATE);
    }

    private static Stream<Arguments> invalidRequests() {
        return Stream.of(
                Arguments.of(
                        null,
                        CULTIVATION_ID,
                        FEEDBACK_DATE
                ),
                Arguments.of(
                        0L,
                        CULTIVATION_ID,
                        FEEDBACK_DATE
                ),
                Arguments.of(
                        -1L,
                        CULTIVATION_ID,
                        FEEDBACK_DATE
                ),
                Arguments.of(
                        USER_ID,
                        null,
                        FEEDBACK_DATE
                ),
                Arguments.of(
                        USER_ID,
                        0L,
                        FEEDBACK_DATE
                ),
                Arguments.of(
                        USER_ID,
                        -1L,
                        FEEDBACK_DATE
                ),
                Arguments.of(
                        USER_ID,
                        CULTIVATION_ID,
                        null
                )
        );
    }

    private static CultivationDetailResponse validCultivationResponse(
            Long cultivationId
    ) {
        return new CultivationDetailResponse(
                cultivationId,
                MUSHROOM_ID,
                "PROCEEDING",
                "GROWTH",
                CULTIVATION_STARTED_AT
        );
    }

    private static DailyFeedback storedFeedback() {
        DailyFeedback feedback = mock(DailyFeedback.class);

        given(feedback.getId()).willReturn(DAILY_FEEDBACK_ID);
        given(feedback.getCultivationId()).willReturn(CULTIVATION_ID);
        given(feedback.getFeedbackDate()).willReturn(FEEDBACK_DATE);
        given(feedback.isHasVisionAnalysis()).willReturn(true);
        given(feedback.getContent()).willReturn(CONTENT);
        given(feedback.getCreatedAt()).willReturn(CREATED_AT);

        return feedback;
    }

    private static Request cultivationRequest() {
        return Request.create(
                Request.HttpMethod.GET,
                "/api/v1/cultivations/10",
                new HashMap<>(),
                Request.Body.empty(),
                new RequestTemplate()
        );
    }
}
