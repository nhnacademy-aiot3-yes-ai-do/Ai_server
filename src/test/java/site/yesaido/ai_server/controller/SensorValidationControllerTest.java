package site.yesaido.ai_server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import site.yesaido.ai_server.dto.ai.sensor_validation.SensorValidationRequest;
import site.yesaido.ai_server.dto.ai.sensor_validation.SensorValidationResponse;
import site.yesaido.ai_server.service.SensorValidationService;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SensorValidationController.class)
class SensorValidationControllerTest {
    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private SensorValidationService sensorValidationService;

    @Test
    @DisplayName("센서 임계값 검증 API 호출 성공 시 200 OK 및 ApiResponse 반환")
    void validateSensorSuccess() throws Exception {
        // 가짜 입력값과 서비스가 반환할 가짜 응답 정의
        Long userId = 1L;
        Long cultivationId = 10L;
        SensorValidationRequest request = new SensorValidationRequest(
                1L, "TEMPERATURE", "°C", new BigDecimal("15.0"), new BigDecimal("25.0")
        );
        SensorValidationResponse response = new SensorValidationResponse(
                true, "적절한 임계값입니다.", new BigDecimal("15.0"), new BigDecimal("25.0")
        );

        given(sensorValidationService.validateSensorThreshold(eq(userId), eq(cultivationId), any(SensorValidationRequest.class)))
                .willReturn(response);

        // HTTP POST 요청을 전송하고 200 상태코드 및 JSON 필드 검증
        mockMvc.perform(post("/api/v1/ai/cultivations/{cultivation-id}/sensor-validation", cultivationId)
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.isValid").value(true))
                .andExpect(jsonPath("$.data.message").value("적절한 임계값입니다."));
    }
}
