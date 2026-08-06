package site.yesaido.ai_server.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import site.yesaido.ai_server.service.MushVectorService;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DataController.class)
class DataControllerTest {

    @Autowired // HTTP  요청
    private MockMvc mockMvc;

    @MockitoBean
    private MushVectorService mushVectorService;

    @Test
    @DisplayName("Vector Db 적재 요청 시 200 OK 및 성공 메시지 반환")
    void loadVectorSuccessTest() throws Exception {
        given(mushVectorService.loadCsv()).willReturn("Vector DB 적재 성공");

        mockMvc.perform(post("/api/admin/data/load-vector"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("Vector DB 적재 성공"));
    }
}
