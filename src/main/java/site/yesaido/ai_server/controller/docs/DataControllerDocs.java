package site.yesaido.ai_server.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import site.yesaido.ai_server.dto.common.ApiResponse;

/**
 * {@code DataController}의 OpenAPI 문서 정의.
 */
@Tag(name = "관리자 - AI 데이터", description = "AI 벡터 스토어 데이터 적재 (관리자 전용)")
public interface DataControllerDocs {

    @Operation(summary = "버섯 벡터 데이터 적재",
            description = "버섯 임베딩 CSV를 읽어 pgvector 벡터 스토어에 적재합니다. `data`는 처리 결과 요약 문자열.")
    ResponseEntity<ApiResponse<String>> loadVector();
}
