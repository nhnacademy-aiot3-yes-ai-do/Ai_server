package site.yesaido.ai_server.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import site.yesaido.ai_server.dto.ai.AiEvaluationDto;
import site.yesaido.ai_server.dto.ai.EnvironmentConditionInfo;
import site.yesaido.ai_server.dto.ai.MushGuideResponse;
import site.yesaido.ai_server.dto.ai.SensorRange;
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
        EnvironmentConditionInfo cultivationCond = new EnvironmentConditionInfo(
                new SensorRange(18.0, 24.0),
                new SensorRange(80.0, 90.0),
                new SensorRange(800.0, 1200.0),
                new SensorRange(100.0, 500.0)
        );
        EnvironmentConditionInfo harvestCond = new EnvironmentConditionInfo(
                new SensorRange(15.0, 18.0),
                new SensorRange(85.0, 95.0),
                new SensorRange(1000.0, 1500.0),
                new SensorRange(100.0, 300.0)
        );

        MushGuideResponse mockResponse = new MushGuideResponse(
                mushroomId,
                "느타리버섯",
                new AiEvaluationDto(1, 5, "민감도 낮음", "매일 습도 확인"),
                "느타리버섯 요약",
                "건조 주의",
                "냉장 보관 팁",
                cultivationCond,
                harvestCond,
                List.of()
        );

        given(mushService.generateRealDataGuide(mushroomId)).willReturn(mockResponse);

        // when & then
        mockMvc.perform(get("/api/mushrooms/{mushroom-id}/guide", mushroomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.mushroomId").value(1))
                .andExpect(jsonPath("$.data.mushroomName").value("느타리버섯"))
                .andExpect(jsonPath("$.data.summary").value("느타리버섯 요약"))
                .andExpect(jsonPath("$.data.caution").value("건조 주의"));
    }
}
