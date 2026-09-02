package site.yesaido.ai_server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import site.yesaido.ai_server.config.ObjectMapperConfig;
import site.yesaido.ai_server.entity.DailyFeedback;
import site.yesaido.ai_server.service.DailyFeedbackBatchService;
import site.yesaido.ai_server.service.DailyFeedbackPersistenceService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("local")
@WebMvcTest(controllers = DailyFeedbackLocalController.class)
@Import(ObjectMapperConfig.class)
class DailyFeedbackLocalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DailyFeedbackBatchService dailyFeedbackBatchService;

    @MockitoBean
    private DailyFeedbackPersistenceService dailyFeedbackPersistenceService;

    @Test
    @DisplayName("저장된 Context Snapshot을 실제 JSON object 구조로 반환한다")
    void getDailyFeedbackReturnsContextSnapshotAsJsonObject() throws Exception {
        LocalDate feedbackDate = LocalDate.of(2026, 9, 1);

        ObjectNode contextSnapshot = objectMapper.createObjectNode();
        contextSnapshot.put("cultivationId", 28L);
        contextSnapshot.put("feedbackDate", feedbackDate.toString());

        contextSnapshot.putArray("sensorStatistics")
                .addObject()
                .put("sensorType", "TEMPERATURE")
                .put("average", 24.1);

        ObjectNode visionAnalysis =
                contextSnapshot.putObject("visionAnalysis");

        visionAnalysis.put("hasVisionAnalysis", false);
        visionAnalysis.putNull("analysisData");

        DailyFeedback feedback = mock(DailyFeedback.class);

        given(feedback.getId()).willReturn(1L);
        given(feedback.getCultivationId()).willReturn(28L);
        given(feedback.getFeedbackDate()).willReturn(feedbackDate);
        given(feedback.isHasVisionAnalysis()).willReturn(false);
        given(feedback.getContent()).willReturn(
                "## 오늘의 환경 요약\n환경 상태가 확인되었습니다."
        );
        given(feedback.getContextSnapshot()).willReturn(contextSnapshot);
        given(feedback.getCreatedAt()).willReturn(
                LocalDateTime.of(2026, 9, 2, 0, 5)
        );

        given(
                dailyFeedbackPersistenceService.findExisting(
                        28L,
                        feedbackDate
                )
        ).willReturn(Optional.of(feedback));

        mockMvc.perform(
                        get("/api/test/daily-feedbacks/{cultivation-id}", 28L)
                                .param("date", feedbackDate.toString())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.cultivationId").value(28))
                .andExpect(jsonPath("$.data.contextSnapshot").isMap())
                .andExpect(
                        jsonPath("$.data.contextSnapshot.cultivationId")
                                .value(28)
                )
                .andExpect(
                        jsonPath("$.data.contextSnapshot.sensorStatistics")
                                .isArray()
                )
                .andExpect(
                        jsonPath(
                                "$.data.contextSnapshot"
                                        + ".visionAnalysis.hasVisionAnalysis"
                        ).value(false)
                )
                .andExpect(
                        jsonPath(
                                "$.data.contextSnapshot"
                                        + ".visionAnalysis.analysisData"
                        ).value(nullValue())
                )
                .andExpect(
                        jsonPath("$.data.contextSnapshot.array")
                                .doesNotExist()
                )
                .andExpect(
                        jsonPath("$.data.contextSnapshot.textual")
                                .doesNotExist()
                )
                .andExpect(
                        jsonPath("$.data.contextSnapshot.nodeType")
                                .doesNotExist()
                );
    }

    @Test
    @DisplayName("저장된 일일 피드백이 없으면 404를 반환한다")
    void getDailyFeedbackReturnsNotFoundWhenFeedbackDoesNotExist()
            throws Exception {
        LocalDate feedbackDate = LocalDate.of(2026, 9, 1);

        given(
                dailyFeedbackPersistenceService.findExisting(
                        99L,
                        feedbackDate
                )
        ).willReturn(Optional.empty());

        mockMvc.perform(
                        get("/api/test/daily-feedbacks/{cultivation-id}", 99L)
                                .param("date", feedbackDate.toString())
                )
                .andExpect(status().isNotFound());
    }
}
