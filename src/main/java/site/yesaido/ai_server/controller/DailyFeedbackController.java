package site.yesaido.ai_server.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.yesaido.ai_server.dto.common.ApiResponse;
import site.yesaido.ai_server.dto.daily_feedback.DailyFeedbackResponse;
import site.yesaido.ai_server.service.DailyFeedbackQueryService;

import java.time.LocalDate;

/**
 * 인증된 사용자가 저장된 일일 피드백을 조회하는 운영용 API입니다.
 *
 * <p>Kubernetes CronJob이 사용하는 내부 실행 Controller 및
 * {@code local} 프로필 전용 검증 Controller와 구분되는 사용자 조회
 * API입니다. Gateway가 전달한 사용자 ID를 이용한 접근 권한 검증은
 * {@link DailyFeedbackQueryService}에 위임합니다.</p>
 *
 * <p>운영 응답에는 피드백 생성 근거인 Context Snapshot을 포함하지
 * 않습니다.</p>
 */
@RestController
@RequestMapping("/api/v1/ai/cultivations")
@RequiredArgsConstructor
public class DailyFeedbackController {

    private final DailyFeedbackQueryService dailyFeedbackQueryService;

    /**
     * 지정한 재배지와 날짜의 일일 피드백을 조회합니다.
     *
     * <p>{@code X-User-Id}는 Gateway가 주입한 인증 사용자 ID이며,
     * 피드백 날짜는 ISO-8601 날짜 형식({@code yyyy-MM-dd})을 사용합니다.</p>
     *
     * @param userId Gateway가 전달한 인증 사용자 ID
     * @param cultivationId 조회할 재배지 ID
     * @param feedbackDate 조회할 피드백 날짜
     * @return 운영용 일일 피드백을 포함한 공통 성공 응답
     */
    @GetMapping("/{cultivation-id}/daily-feedbacks/{feedback-date}")
    public ResponseEntity<ApiResponse<DailyFeedbackResponse>>
    getDailyFeedback(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable("cultivation-id") Long cultivationId,
            @PathVariable("feedback-date")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate feedbackDate
    ) {
        DailyFeedbackResponse response = dailyFeedbackQueryService.getDailyFeedback(userId, cultivationId, feedbackDate);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
