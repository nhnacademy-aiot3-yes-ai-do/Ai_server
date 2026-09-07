package site.yesaido.ai_server.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import site.yesaido.ai_server.controller.docs.DataControllerDocs;
import site.yesaido.ai_server.dto.common.ApiResponse;
import site.yesaido.ai_server.service.MushVectorService;

@RestController
@RequestMapping("/api/v1/admin/data")
@RequiredArgsConstructor
public class DataController implements DataControllerDocs {
    private final MushVectorService mushVectorService;

    @Override
    @PostMapping("/load-vector")
    public ResponseEntity<ApiResponse<String>> loadVector() {
        String result = mushVectorService.loadCsv();
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
