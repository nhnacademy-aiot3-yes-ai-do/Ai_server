package site.yesaido.ai_server.vision.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import site.yesaido.ai_server.storage.minio.service.MinioImageService;
import site.yesaido.ai_server.vision.client.VisionClient;
import site.yesaido.ai_server.vision.dto.Vision.response.VisionResponse;

@Service
@RequiredArgsConstructor
public class VisionRelayService {

    private final VisionClient visionClient;
    private final MinioImageService minioImageService;

    public VisionResponse analyzeMushroomHealth(MultipartFile image) {
        return visionClient.analyzeMushroomHealth(image);
    }

}
