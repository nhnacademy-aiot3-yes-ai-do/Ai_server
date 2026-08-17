package site.yesaido.ai_server.vision.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import site.yesaido.ai_server.vision.dto.response.VisionResponse;
import site.yesaido.ai_server.vision.service.VisionRelayService;

@Profile("local")
@RestController
@RequestMapping("/api/test/vision")
@RequiredArgsConstructor
public class VisionTestController {

    private final VisionRelayService visionRelayService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public VisionResponse analyzeMushroomHealth(
            @RequestPart("image") MultipartFile image
    ) {
        return visionRelayService.analyzeMushroomHealth(image);
    }
}
