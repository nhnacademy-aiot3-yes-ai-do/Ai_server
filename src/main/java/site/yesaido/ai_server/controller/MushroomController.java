package site.yesaido.ai_server.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import site.yesaido.ai_server.dto.MushGuideResponse;
import site.yesaido.ai_server.service.MushService;

@RestController
@RequiredArgsConstructor
public class MushroomController {
    private final MushService mushService;

    @GetMapping("/api/mushrooms/{mushroomId}/guide")
    public MushGuideResponse getMushroomGuide(@PathVariable Long mushroomId) {
        return mushService.generateRealDataGuide(mushroomId);
    }
}
