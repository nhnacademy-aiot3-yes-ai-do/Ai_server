package site.yesaido.ai_server.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import site.yesaido.ai_server.dto.ai.insight.InsightCandidateResponse;
import site.yesaido.ai_server.dto.ai.insight.InsightDetailResponse;
import site.yesaido.ai_server.dto.common.ApiResponse;
import site.yesaido.ai_server.service.InsightService;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/ai/insights")
@RequiredArgsConstructor
public class InsightController {
    private final InsightService insightService;

    /**
     * 현재 재배 환경과 유사한 과거 우수 재배 사례 5개를 추천 조회 (내 재배지 제외)
     */
    @GetMapping("/candidates")
    public ResponseEntity<ApiResponse<List<InsightCandidateResponse>>> getCandidates(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam("mushroom-id") Long mushroomId,
            @RequestParam("temp") BigDecimal temp,
            @RequestParam("hum") BigDecimal hum,
            @RequestParam("co2") BigDecimal co2,
            @RequestParam("light") BigDecimal light
    ) {
        List<InsightCandidateResponse> candidates = insightService.getInsightCandidates(
                userId, mushroomId, temp, hum, co2, light
        );
        return ResponseEntity.ok(ApiResponse.success(candidates));
    }

    /**
     * 특정 수확 인사이트의 상세 정보 및 일자별(1일차, 2일차...) 피드백 타임라인을 조회
     */
    @GetMapping("/{insight-id}")
    public ResponseEntity<ApiResponse<InsightDetailResponse>> getDetail(
            @PathVariable("insight-id") Long insightId
    ) {
        InsightDetailResponse detail = insightService.getInsightDetail(insightId);
        return ResponseEntity.ok(ApiResponse.success(detail));
    }
}
