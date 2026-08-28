package site.yesaido.ai_server.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import site.yesaido.ai_server.client.VisionClient;
import site.yesaido.ai_server.dto.client.vision.VisionResponse;

@Service
@RequiredArgsConstructor
public class VisionRelayService {

    private final VisionClient visionClient;

    public VisionResponse analyzeMushroomHealth(MultipartFile image) {
        return visionClient.analyzeMushroomHealth(image);
    }

}
