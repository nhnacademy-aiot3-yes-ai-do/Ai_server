package site.yesaido.ai_server.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import site.yesaido.ai_server.service.MushService;

@RestController
@RequiredArgsConstructor
public class TestController {
    private final MushService mushService;

    @GetMapping("/api/test/guide")
    public MushService.MushroomGuideResponse testGuide(
            @RequestParam(defaultValue = "느타리버섯") String name) {
        // 서버가 켜질 때 워머가 이미 데이터를 다 뭉쳐서 Redis에 넣어두었기 때문에,
        // 여기서 빈 문자열을 던져도 문지기(@Cacheable)가 알아서 데이터를 꺼내줌
        return mushService.generateRealDataGuide(name, "");
    }
}
