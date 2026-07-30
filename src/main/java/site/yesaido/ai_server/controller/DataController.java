package site.yesaido.ai_server.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import site.yesaido.ai_server.dto.ApiResponse;
import site.yesaido.ai_server.exception.UnauthorizedAccessException;
import site.yesaido.ai_server.service.MushVectorService;

@RestController
@RequestMapping("/api/admin/data")
@RequiredArgsConstructor
public class DataController {
    private final MushVectorService mushVectorService;

    @Value("${PG_STACKING_KEY}")
    private String pgStackingKey;

    @PostMapping("/load-vector")
    public ApiResponse<String> loadVector(@RequestHeader(value = "PG_STACKING_KEY", required = false) String pgKey) {
        if(pgKey == null || !pgKey.equals(pgStackingKey)) {
            throw new UnauthorizedAccessException();
        }
        String result = mushVectorService.loadCsv();
        return ApiResponse.success(result);
    }
}
