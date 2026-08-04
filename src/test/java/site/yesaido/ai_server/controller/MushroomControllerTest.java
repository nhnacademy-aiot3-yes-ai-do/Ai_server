package site.yesaido.ai_server.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import site.yesaido.ai_server.dto.AiEvaluationDto;
import site.yesaido.ai_server.dto.EnvironmentConditionInfo;
import site.yesaido.ai_server.dto.MushGuideResponse;
import site.yesaido.ai_server.service.MushService;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MushroomController.class)
class MushroomControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MushService mushService;

    @Test
    @DisplayName("버섯 가이드 조회 성공 시 200 OK 및 MushGuideResponse 반환")
    void getMushroomGuideSuccessTest() throws Exception {
        // given
        Long mushroomId = 1L;
        MushGuideResponse mockResponse = new MushGuideResponse(
                new AiEvaluationDto(1, 5, "민감도 낮음", "매일 습도 확인"),
                "느타리버섯 요약",
                "건조 주의",
                "냉장 보관 팁",
                new EnvironmentConditionInfo("20c", "85%", "1000ppm", "500lux"),
                List.of()
        );

        given(mushService.generateRealDataGuide(mushroomId)).willReturn(mockResponse);

        // when & then
        mockMvc.perform(get("/api/mushrooms/{mushroom-id}/guide", mushroomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.summary").value("느타리버섯 요약"))
                .andExpect(jsonPath("$.data.caution").value("건조 주의"));
    }
}
