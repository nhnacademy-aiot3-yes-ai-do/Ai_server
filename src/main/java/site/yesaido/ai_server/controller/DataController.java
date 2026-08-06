package site.yesaido.ai_server.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import site.yesaido.ai_server.dto.ApiResponse;
import site.yesaido.ai_server.service.MushVectorService;

@RestController
@RequestMapping("/api/admin/data")
@RequiredArgsConstructor
public class DataController {
    private final MushVectorService mushVectorService;

    @PostMapping("/load-vector")
    public ApiResponse<String> loadVector() {
        String result = mushVectorService.loadCsv();
        return ApiResponse.success(result);
    }
}
