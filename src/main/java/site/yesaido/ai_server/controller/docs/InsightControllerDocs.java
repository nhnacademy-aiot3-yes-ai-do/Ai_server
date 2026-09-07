package site.yesaido.ai_server.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import site.yesaido.ai_server.dto.ai.insight.InsightCandidateResponse;
import site.yesaido.ai_server.dto.ai.insight.InsightDetailResponse;
import site.yesaido.ai_server.dto.common.ApiResponse;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@code InsightController}의 OpenAPI 문서 정의.
 */
@Tag(name = "AI 인사이트", description = "현재 재배 환경과 유사한 과거 우수 재배 사례 추천 및 상세 조회")
public interface InsightControllerDocs {

    @Operation(summary = "우수 재배 사례 추천",
            description = "현재 재배 환경과 유사한 과거 우수 재배 사례 5개를 추천합니다(내 재배지 제외). "
                    + "`cultivationId` 를 주면 해당 재배의 현재 환경을 기준으로, 없으면 개별 파라미터(temp/hum/co2/light)로 검색합니다.")
    ResponseEntity<ApiResponse<List<InsightCandidateResponse>>> getCandidates(
            Long userId,
            @Parameter(description = "기준 재배 ID") Long cultivationId,
            @Parameter(description = "버섯 ID") Long mushroomId,
            @Parameter(description = "온도") BigDecimal temp,
            @Parameter(description = "습도") BigDecimal hum,
            @Parameter(description = "CO2") BigDecimal co2,
            @Parameter(description = "조도") BigDecimal light);

    @Operation(summary = "인사이트 상세 조회",
            description = "특정 수확 인사이트의 상세 정보와 일자별(1일차, 2일차...) 피드백 타임라인을 반환합니다.")
    ResponseEntity<ApiResponse<InsightDetailResponse>> getDetail(@Parameter(description = "인사이트 ID") Long insightId);
}
