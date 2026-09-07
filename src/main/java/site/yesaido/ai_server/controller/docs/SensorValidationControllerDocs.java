package site.yesaido.ai_server.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import site.yesaido.ai_server.dto.ai.sensor_validation.SensorValidationRequest;
import site.yesaido.ai_server.dto.ai.sensor_validation.SensorValidationResponse;
import site.yesaido.ai_server.dto.common.ApiResponse;

/**
 * {@code SensorValidationController}의 OpenAPI 문서 정의.
 */
@Tag(name = "AI 센서 검증", description = "사용자가 입력한 센서 임계값이 해당 버섯 재배에 적절한지 AI로 검증")
public interface SensorValidationControllerDocs {

    @Operation(summary = "센서 임계값 AI 검증",
            description = "재배지에 등록하려는 센서 임계값(최소/최대)이 적절한지 AI가 판단해 검증 결과와 사유를 반환합니다.")
    ApiResponse<SensorValidationResponse> validateSensor(
            Long userId,
            @Parameter(description = "재배 ID") Long cultivationId,
            SensorValidationRequest request);
}
