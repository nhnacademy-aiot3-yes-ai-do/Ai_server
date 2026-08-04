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

@WebMvcTest(controllers = DataController.class, properties = {"PG_STACKING_KEY=test-secret-key"}) // 테스트용 가짜 환경변수 주입
class DataControllerTest {

    @Autowired // HTTP  요청
    private MockMvc mockMvc;

    @MockitoBean
    private MushVectorService mushVectorService;

    @Test
    @DisplayName("PG_STACKING_KEY 헤더가 누락되거나 없으면 401 Unauthorized 반환")
    void loadVectorUnauthorizedTest() throws Exception{
        mockMvc.perform(post("/api/admin/data/load-vector")) // 실제 엔드포인트 주소 작성
                .andExpect(status().isUnauthorized()) // 상태 확인(401 에러 반환 확인)
                .andExpect(jsonPath("$.success").value(false)); // JSON 응답 검증
    }

    @Test
    @DisplayName("올바른 PG_STACKING_KEY 헤더 전달 시 200 OK 반환")
    void loadVectorSuccessTest() throws Exception {
        // properties에서 주입된 "test-secret-key"를 통과시키는지 확인
        given(mushVectorService.loadCsv()).willReturn("Vector DB 적재 성공");

        mockMvc.perform(post("/api/admin/data/load-vector")
                        .header("PG_STACKING_KEY", "test-secret-key")) // 주입된 테스트용 키와 일치해야 함
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("Vector DB 적재 성공")); // 느낌표 제거 (given과 맞춤)
    }
}
