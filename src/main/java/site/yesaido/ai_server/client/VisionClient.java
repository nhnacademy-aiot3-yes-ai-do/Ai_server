package site.yesaido.ai_server.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import site.yesaido.ai_server.dto.client.vision.VisionResponse;

@FeignClient(
        name = "vision-client",
        url = "${feign.client.vision-server.url}"
)
public interface VisionClient {

    @PostMapping(
            value = "/api/v1/internal/mushrooms/health-check",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    VisionResponse analyzeMushroomHealth(
            @RequestPart("image") MultipartFile image
    );
}
