package site.yesaido.ai_server.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import site.yesaido.ai_server.service.MushVectorService;

@RestController
@RequestMapping("/api/admin/data")
@RequiredArgsConstructor
public class DataController {
    private final MushVectorService mushVectorService;

    @Value("${PG_STACKING_KEY}")
    private String pgStackingKey;

    @PostMapping("/load-vector")
    public ResponseEntity<String> loadVector(@RequestHeader(value = "PG_Key", required = false) String pgKey) {
        if(pgKey == null || !pgKey.equals(pgStackingKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("접근 권한이 없습니다.");
        }
        String result = mushVectorService.loadCsv();
        return ResponseEntity.ok(result);
    }
}
