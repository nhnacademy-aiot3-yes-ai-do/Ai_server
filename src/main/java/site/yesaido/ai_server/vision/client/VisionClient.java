package site.yesaido.ai_server.vision.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import site.yesaido.ai_server.vision.dto.response.VisionResponse;

@FeignClient(
        name = "vision-client",
        url = "${vision.server.url}"
)
public interface VisionClient {

    @PostMapping(
            value = "/api/v1/mushroom/health-check",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    VisionResponse analyzeMushroomHealth(
            @RequestPart("image") MultipartFile image
    );
}
