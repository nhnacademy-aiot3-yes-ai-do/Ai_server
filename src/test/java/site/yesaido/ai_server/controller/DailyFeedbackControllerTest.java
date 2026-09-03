package site.yesaido.ai_server.controller;

import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import site.yesaido.ai_server.dto.daily_feedback.DailyFeedbackResponse;
import site.yesaido.ai_server.exception.DailyFeedbackNotFoundException;
import site.yesaido.ai_server.exception.UnauthorizedAccessException;
import site.yesaido.ai_server.service.DailyFeedbackQueryService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DailyFeedbackController.class)
class DailyFeedbackControllerTest {

    private static final String ENDPOINT =
            "/api/v1/ai/cultivations/{cultivation-id}"
                    + "/daily-feedbacks/{feedback-date}";

    private static final Long USER_ID = 1L;
    private static final Long CULTIVATION_ID = 10L;
    private static final Long DAILY_FEEDBACK_ID = 1001L;

    private static final LocalDate FEEDBACK_DATE =
            LocalDate.of(2026, 9, 2);
    private static final LocalDateTime CREATED_AT =
            LocalDateTime.of(2026, 9, 3, 0, 5);

    private static final String FEEDBACK_CONTENT =
            "# 오늘의 재배 환경 요약\n환경이 안정적으로 유지되었습니다.";

    private static final String FEIGN_INTERNAL_MESSAGE =
            "sensitive upstream failure";
    private static final String CONTRACT_INTERNAL_MESSAGE =
            "재배지 응답 ID가 요청과 일치하지 않습니다: sensitive";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DailyFeedbackQueryService dailyFeedbackQueryService;

    @Test
    @DisplayName("인증된 재배지 멤버에게 운영용 일일 피드백을 반환한다")
    void returnDailyFeedbackToAuthorizedCultivationMember()
            throws Exception {
        // 준비
        DailyFeedbackResponse response = new DailyFeedbackResponse(
                DAILY_FEEDBACK_ID,
                CULTIVATION_ID,
                FEEDBACK_DATE,
                true,
                FEEDBACK_CONTENT,
                CREATED_AT
        );

        given(
                dailyFeedbackQueryService.getDailyFeedback(
                        USER_ID,
                        CULTIVATION_ID,
                        FEEDBACK_DATE
                )
        ).willReturn(response);

        // 실행
        ResultActions result = mockMvc.perform(
                get(
                        ENDPOINT,
                        CULTIVATION_ID,
                        FEEDBACK_DATE
                ).header("X-User-Id", USER_ID)
        );

        // 검증
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(
                        jsonPath("$.message").value(
                                "요청이 성공적으로 처리되었습니다."
                        )
                )
                .andExpect(
                        jsonPath("$.data.dailyFeedbackId")
                                .value(DAILY_FEEDBACK_ID)
                )
                .andExpect(
                        jsonPath("$.data.cultivationId")
                                .value(CULTIVATION_ID)
                )
                .andExpect(
                        jsonPath("$.data.feedbackDate")
                                .value("2026-09-02")
                )
                .andExpect(
                        jsonPath("$.data.hasVisionAnalysis")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.data.content")
                                .value(FEEDBACK_CONTENT)
                )
                .andExpect(
                        jsonPath("$.data.createdAt")
                                .value("2026-09-03T00:05:00")
                )
                .andExpect(
                        jsonPath("$.data.contextSnapshot")
                                .doesNotExist()
                );

        then(dailyFeedbackQueryService)
                .should()
                .getDailyFeedback(
                        USER_ID,
                        CULTIVATION_ID,
                        FEEDBACK_DATE
                );
        then(dailyFeedbackQueryService)
                .shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("사용자 헤더가 없으면 서비스 호출 없이 400을 반환한다")
    void rejectRequestWithoutUserHeader() throws Exception {
        // 준비

        // 실행
        ResultActions result = mockMvc.perform(
                get(
                        ENDPOINT,
                        CULTIVATION_ID,
                        FEEDBACK_DATE
                ).param("userId", USER_ID.toString())
        );

        // 검증
        result.andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.detail").value(
                                "필수 헤더가 누락되었습니다: X-User-Id"
                        )
                );

        verifyNoInteractions(dailyFeedbackQueryService);
    }

    @Test
    @DisplayName("피드백 날짜 형식이 잘못되면 서비스 호출 없이 400을 반환한다")
    void rejectInvalidFeedbackDateFormat() throws Exception {
        // 준비

        // 실행
        ResultActions result = mockMvc.perform(
                get(
                        ENDPOINT,
                        CULTIVATION_ID,
                        "not-a-date"
                ).header("X-User-Id", USER_ID)
        );

        // 검증
        result.andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.detail")
                                .value("유효하지 않은 요청 데이터입니다.")
                );

        verifyNoInteractions(dailyFeedbackQueryService);
    }

    @Test
    @DisplayName("재배지 접근 권한이 없으면 403을 반환한다")
    void returnForbiddenWhenCultivationAccessIsDenied()
            throws Exception {
        // 준비
        given(
                dailyFeedbackQueryService.getDailyFeedback(
                        USER_ID,
                        CULTIVATION_ID,
                        FEEDBACK_DATE
                )
        ).willThrow(new UnauthorizedAccessException());

        // 실행
        ResultActions result = mockMvc.perform(
                get(
                        ENDPOINT,
                        CULTIVATION_ID,
                        FEEDBACK_DATE
                ).header("X-User-Id", USER_ID)
        );

        // 검증
        result.andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.detail")
                                .value("접근 권한이 없습니다.")
                )
                .andExpect(jsonPath("$.success").doesNotExist());

        then(dailyFeedbackQueryService)
                .should()
                .getDailyFeedback(
                        USER_ID,
                        CULTIVATION_ID,
                        FEEDBACK_DATE
                );
        then(dailyFeedbackQueryService)
                .shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("재배지 또는 일일 피드백이 없으면 404를 반환한다")
    void returnNotFoundWhenCultivationOrFeedbackDoesNotExist()
            throws Exception {
        // 준비
        given(
                dailyFeedbackQueryService.getDailyFeedback(
                        USER_ID,
                        CULTIVATION_ID,
                        FEEDBACK_DATE
                )
        ).willThrow(
                new DailyFeedbackNotFoundException(
                        CULTIVATION_ID,
                        FEEDBACK_DATE
                )
        );

        // 실행
        ResultActions result = mockMvc.perform(
                get(
                        ENDPOINT,
                        CULTIVATION_ID,
                        FEEDBACK_DATE
                ).header("X-User-Id", USER_ID)
        );

        // 검증
        result.andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.detail").value(
                                "해당 재배지와 날짜의 일일 피드백이 "
                                        + "존재하지 않습니다. "
                                        + "cultivationId: 10, "
                                        + "feedbackDate: 2026-09-02"
                        )
                )
                .andExpect(jsonPath("$.success").doesNotExist());

        then(dailyFeedbackQueryService)
                .should()
                .getDailyFeedback(
                        USER_ID,
                        CULTIVATION_ID,
                        FEEDBACK_DATE
                );
        then(dailyFeedbackQueryService)
                .shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("외부 서비스 장애는 안전한 502 응답으로 변환한다")
    void convertExternalServiceFailureToSafeBadGatewayResponse()
            throws Exception {
        // 준비
        FeignException.InternalServerError externalFailure =
                new FeignException.InternalServerError(
                        FEIGN_INTERNAL_MESSAGE,
                        cultivationRequest(),
                        null,
                        null
                );

        given(
                dailyFeedbackQueryService.getDailyFeedback(
                        USER_ID,
                        CULTIVATION_ID,
                        FEEDBACK_DATE
                )
        ).willThrow(externalFailure);

        // 실행
        ResultActions result = mockMvc.perform(
                get(
                        ENDPOINT,
                        CULTIVATION_ID,
                        FEEDBACK_DATE
                ).header("X-User-Id", USER_ID)
        );

        // 검증
        result.andExpect(status().isBadGateway())
                .andExpect(
                        jsonPath("$.detail").value(
                                "외부 서비스 연결이 일시적으로 원활하지 않습니다. "
                                        + "잠시 후 다시 시도해 주세요."
                        )
                )
                .andExpect(
                        content().string(
                                not(containsString(FEIGN_INTERNAL_MESSAGE))
                        )
                );

        then(dailyFeedbackQueryService)
                .should()
                .getDailyFeedback(
                        USER_ID,
                        CULTIVATION_ID,
                        FEEDBACK_DATE
                );
        then(dailyFeedbackQueryService)
                .shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("내부 응답 계약 위반은 상세 원인을 숨기고 500을 반환한다")
    void hideInternalContractFailureDetails() throws Exception {
        // 준비
        given(
                dailyFeedbackQueryService.getDailyFeedback(
                        USER_ID,
                        CULTIVATION_ID,
                        FEEDBACK_DATE
                )
        ).willThrow(
                new IllegalStateException(CONTRACT_INTERNAL_MESSAGE)
        );

        // 실행
        ResultActions result = mockMvc.perform(
                get(
                        ENDPOINT,
                        CULTIVATION_ID,
                        FEEDBACK_DATE
                ).header("X-User-Id", USER_ID)
        );

        // 검증
        result.andExpect(status().isInternalServerError())
                .andExpect(
                        jsonPath("$.detail")
                                .value("서버 내부에 오류가 발생했습니다.")
                )
                .andExpect(
                        content().string(
                                not(containsString(CONTRACT_INTERNAL_MESSAGE))
                        )
                );

        then(dailyFeedbackQueryService)
                .should()
                .getDailyFeedback(
                        USER_ID,
                        CULTIVATION_ID,
                        FEEDBACK_DATE
                );
        then(dailyFeedbackQueryService)
                .shouldHaveNoMoreInteractions();
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