package site.yesaido.ai_server.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import site.yesaido.ai_server.dto.ai.mush_summary.ApiResponse;
import site.yesaido.ai_server.dto.front.SensorValidationRequest;
import site.yesaido.ai_server.dto.front.SensorValidationResponse;
import site.yesaido.ai_server.service.SensorValidationService;

@RestController
@RequestMapping("/api/v1/ai/cultivations")
@RequiredArgsConstructor
public class SensorValidationController {
    private final SensorValidationService sensorValidationService;

    @PostMapping("/{cultivation-id}/sensor-validation")
    public ApiResponse<SensorValidationResponse> validateSensor(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable("cultivation-id") Long cultivationId,
            @RequestBody SensorValidationRequest request
    ) {
        SensorValidationResponse result = sensorValidationService.validateSensorThreshold(userId, cultivationId, request);
        return ApiResponse.success(result);
    }

}
