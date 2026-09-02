package site.yesaido.ai_server.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import site.yesaido.ai_server.dto.ai.insight.InsightCandidateResponse;
import site.yesaido.ai_server.dto.ai.insight.InsightDetailResponse;
import site.yesaido.ai_server.service.InsightService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InsightController.class)
class InsightControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InsightService insightService;

    @Test
    @DisplayName("GET /api/v1/ai/insights/candidates - 유사 인사이트 후보 조회 성공 시 200 OK")
    void getCandidates() throws Exception {
        InsightCandidateResponse candidate = new InsightCandidateResponse(
                1L, 10L, 1L,
                new BigDecimal("23.5"), new BigDecimal("85.0"), new BigDecimal("900.0"), new BigDecimal("500.0"),
                new BigDecimal("1200.0"), 95, "우수 관리로 다수확 달성", LocalDateTime.now()
        );

        given(insightService.getInsightCandidates(eq(100L), eq(1L), any(), any(), any(), any()))
                .willReturn(List.of(candidate));

        mockMvc.perform(get("/api/v1/ai/insights/candidates")
                        .header("X-User-Id", 100L)
                        .param("mushroom-id", "1")
                        .param("temp", "23.0")
                        .param("hum", "80.0")
                        .param("co2", "800.0")
                        .param("light", "500.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].insightId").value(1L))
                .andExpect(jsonPath("$.data[0].cultivationId").value(10L));
    }

    @Test
    @DisplayName("GET /api/v1/ai/insights/{insight-id} - 인사이트 상세 조회 성공 시 200 OK")
    void getInsightDetail() throws Exception {
        InsightDetailResponse detail = new InsightDetailResponse(
                1L, 10L, 1L,
                new BigDecimal("23.5"), new BigDecimal("85.0"), new BigDecimal("900.0"), new BigDecimal("500.0"),
                new BigDecimal("1200.0"), 95, "요약 내용", LocalDateTime.now(), List.of()
        );

        given(insightService.getInsightDetail(1L)).willReturn(detail);

        mockMvc.perform(get("/api/v1/ai/insights/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.insightId").value(1L))
                .andExpect(jsonPath("$.data.cultivationId").value(10L));
    }
}
