package site.yesaido.ai_server.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import site.yesaido.ai_server.dto.common.ApiResponse;
import site.yesaido.ai_server.dto.daily_feedback.DailyFeedbackResponse;

import java.time.LocalDate;

/**
 * {@code DailyFeedbackController}의 OpenAPI 문서 정의.
 */
@Tag(name = "AI 일일 피드백", description = "저장된 일일 재배 피드백 조회")
public interface DailyFeedbackControllerDocs {

    @Operation(summary = "일일 피드백 조회",
            description = "지정한 재배지와 날짜(yyyy-MM-dd)의 일일 피드백을 반환합니다. 재배 멤버만 조회할 수 있습니다. "
                    + "운영 응답에는 생성 근거 Context Snapshot을 포함하지 않습니다.")
    ResponseEntity<ApiResponse<DailyFeedbackResponse>> getDailyFeedback(
            Long userId,
            String role,
            @Parameter(description = "재배 ID") Long cultivationId,
            @Parameter(description = "피드백 날짜 (yyyy-MM-dd)") LocalDate feedbackDate);
}
