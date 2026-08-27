package site.yesaido.ai_server.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import site.yesaido.ai_server.dto.common.ApiResponse;
import site.yesaido.ai_server.dto.ai.sensor_validation.SensorValidationRequest;
import site.yesaido.ai_server.dto.ai.sensor_validation.SensorValidationResponse;
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
