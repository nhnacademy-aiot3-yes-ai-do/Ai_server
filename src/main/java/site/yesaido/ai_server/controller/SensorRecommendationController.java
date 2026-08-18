package site.yesaido.ai_server.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import site.yesaido.ai_server.dto.ai.mush_summary.ApiResponse;
import site.yesaido.ai_server.dto.front.SensorRecommendationRequest;
import site.yesaido.ai_server.dto.front.SensorValidationResponse;
import site.yesaido.ai_server.service.SensorRecommendationService;

@RestController
@RequestMapping("/api/ai/sensor-recommendation")
@RequiredArgsConstructor
public class SensorRecommendationController {
    private final SensorRecommendationService sensorRecommendationService;

    @PostMapping
    public ApiResponse<SensorValidationResponse> validateSensor(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody SensorRecommendationRequest request
    ) {
        SensorValidationResponse result = sensorRecommendationService.validateSensorThreshold(userId, request);
        return ApiResponse.success(result);
    }

}
