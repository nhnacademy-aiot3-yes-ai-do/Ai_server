package site.yesaido.ai_server.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import site.yesaido.ai_server.dto.ai.mush_summary.MushGuideResponse;
import site.yesaido.ai_server.dto.common.ApiResponse;

/**
 * {@code MushroomController}의 OpenAPI 문서 정의.
 */
@Tag(name = "버섯 가이드", description = "실측 데이터 기반 버섯 재배 가이드 생성")
public interface MushroomControllerDocs {

    @Operation(summary = "버섯 가이드 조회",
            description = "버섯 종류의 실제 재배 데이터를 바탕으로 권장 환경·레시피 등 재배 가이드를 생성해 반환합니다.")
    ResponseEntity<ApiResponse<MushGuideResponse>> getMushroomGuide(@Parameter(description = "버섯 ID") Long mushroomId);
}
