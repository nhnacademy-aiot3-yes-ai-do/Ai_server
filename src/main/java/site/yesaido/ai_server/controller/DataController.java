package site.yesaido.ai_server.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import site.yesaido.ai_server.dto.ApiResponse;
import site.yesaido.ai_server.exception.UnauthorizedAccessException;
import site.yesaido.ai_server.service.MushVectorService;

@RestController
@RequestMapping("/api/admin/data")
@RequiredArgsConstructor
public class DataController {
    private final MushVectorService mushVectorService;

    @Value("${PG_STACKING_KEY:}") // 환경 변수 없어도 서버는 가동 가능하게 기본값 빈 문자열로 설정
    private String pgStackingKey;

    @PostMapping("/load-vector")
    public ApiResponse<String> loadVector(@RequestHeader(value = "PG_STACKING_KEY", required = false) String pgKey) {
        if(pgStackingKey.isBlank() || pgKey == null || pgKey.isBlank() || !pgKey.equals(pgStackingKey)) {
            throw new UnauthorizedAccessException();
        }
        String result = mushVectorService.loadCsv();
        return ApiResponse.success(result);
    }
}
