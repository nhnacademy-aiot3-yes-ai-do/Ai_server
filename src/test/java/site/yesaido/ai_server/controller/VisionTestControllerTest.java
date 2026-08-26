package site.yesaido.ai_server.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import site.yesaido.ai_server.dto.vision.response.VisionResponse;
import site.yesaido.ai_server.service.VisionRelayService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("local")
@WebMvcTest(controllers = VisionTestController.class)
class VisionTestControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VisionRelayService visionRelayService;

    @Test
    @DisplayName("비전 테스트 이미지 분석 요청 성공 시 200 OK 및 VisionResponse 반환")
    void analyzeMushroomHealthSuccess() throws Exception {
        // 가짜 이미지 MultipartFile 및 가짜 비전 분석 결과 준비
        MockMultipartFile file = new MockMultipartFile(
                "image", "mushroom.jpg", "image/jpeg", "dummy image content".getBytes()
        );
        VisionResponse mockResponse = new VisionResponse(
                "MUSHROOM_HEALTH_CHECK_V1",
                "SUCCESS",
                "yolo-detector",
                "health-classifier",
                null,
                List.of(),
                List.of()
        );

        given(visionRelayService.analyzeMushroomHealth(any())).willReturn(mockResponse);

        // [When & Then] multipart/form-data 요청 전송 후 200 OK 및 응답 필드 검증
        mockMvc.perform(multipart("/api/test/vision").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.analysisType").value("MUSHROOM_HEALTH_CHECK_V1"));
    }
}
