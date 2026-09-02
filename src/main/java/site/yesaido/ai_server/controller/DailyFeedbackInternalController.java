package site.yesaido.ai_server.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.yesaido.ai_server.dto.common.ApiResponse;
import site.yesaido.ai_server.dto.daily_feedback.DailyFeedbackBatchResult;
import site.yesaido.ai_server.service.DailyFeedbackScheduledBatchService;

/**
 * Kubernetes CronJob이 일일 피드백 배치를 실행하기 위한 내부 전용 진입점입니다.
 *
 * <p>Gateway에 공개하지 않는 클러스터 내부 API이며, AI Server가
 * Asia/Seoul 기준 전날 날짜를 계산하여 피드백 배치를 동기적으로
 * 실행합니다.</p>
 *
 * <p>대상별 부분 실패를 포함한 실행 예외를 그대로 전파하여 HTTP 성공
 * 응답으로 바꾸지 않으며, Kubernetes Job이 실패를 인식하고 재시도할 수
 * 있도록 합니다. 중복 호출 시 기존 피드백을 재사용하는 멱등성은 하위
 * 배치 계층이 담당합니다.</p>
 */
@RestController
@RequestMapping("/api/v1/internal/daily-feedbacks")
@RequiredArgsConstructor
public class DailyFeedbackInternalController {

    private final DailyFeedbackScheduledBatchService
            dailyFeedbackScheduledBatchService;

    /**
     * 서울 시간 기준 전날의 일일 피드백 배치를 동기적으로 실행합니다.
     *
     * <p>서비스 호출이 완료될 때까지 HTTP 요청을 유지하며, 부분 실패를
     * 포함한 예외는 처리하지 않고 상위로 전파하여 Kubernetes CronJob의
     * 재시도를 유도합니다.</p>
     *
     * @return 전날 일일 피드백 배치 결과를 포함한 공통 성공 응답
     */
    @PostMapping("/run-previous-day")
    public ApiResponse<DailyFeedbackBatchResult> runPreviousDay() {
        DailyFeedbackBatchResult result =
                dailyFeedbackScheduledBatchService.executePreviousDay();

        return ApiResponse.success(result);
    }
}
