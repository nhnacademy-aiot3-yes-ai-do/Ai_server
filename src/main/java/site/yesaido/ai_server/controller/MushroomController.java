package site.yesaido.ai_server.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import site.yesaido.ai_server.dto.ai.mush_summary.ApiResponse;
import site.yesaido.ai_server.dto.ai.mush_summary.MushGuideResponse;
import site.yesaido.ai_server.service.MushService;

@RestController
@RequiredArgsConstructor
public class MushroomController {
    private final MushService mushService;

    @GetMapping("/api/mushrooms/{mushroom-id}/guide") // mushroomId -> mushroom-id 수정
    public ResponseEntity<ApiResponse<MushGuideResponse>> getMushroomGuide(@PathVariable("mushroom-id") Long mushroomId) {
        MushGuideResponse response = mushService.generateRealDataGuide(mushroomId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
