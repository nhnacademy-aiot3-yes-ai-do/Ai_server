package site.yesaido.ai_server.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import site.yesaido.ai_server.dto.ai.mush_summary.ApiResponse;
import site.yesaido.ai_server.dto.front.SensorValidationRequest;
import site.yesaido.ai_server.dto.front.SensorValidationResponse;
import site.yesaido.ai_server.service.SensorValidationService;

@RestController
@RequestMapping("/api/ai/sensor-validation")
@RequiredArgsConstructor
public class SensorValidationController {
    private final SensorValidationService sensorValidationService;

    @PostMapping
    public ApiResponse<SensorValidationResponse> validateSensor(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody SensorValidationRequest request
    ) {
        SensorValidationResponse result = sensorValidationService.validateSensorThreshold(userId, request);
        return ApiResponse.success(result);
    }

}
