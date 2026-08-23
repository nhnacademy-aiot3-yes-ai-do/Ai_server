package site.yesaido.ai_server.service;


import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import site.yesaido.ai_server.dto.ai.insight.InsightCandidateResponse;

import static org.junit.jupiter.api.Assertions.*;

@Disabled("Cultivation_server와 실제 DB가 필요한 통합 테스트")
@SpringBootTest
class InsightIntegrationTest {
    @Autowired
    private InsightService insightService;

    @Test
    @DisplayName("OpenFeign + Gemini + PostgreSQL 적재 통합 테스트")
    void  IntegrationTest(){
        InsightCandidateResponse response = insightService.saveHarvestInsight(15L, 100L);

        assertNotNull(response);
        assertNotNull(response.insightId());
        assertEquals(15L, response.cultivationId());
        assertNotNull(response.summary());
        assertFalse(response.summary().isBlank());

        System.out.println("=========================================");
        System.out.println("🎉 실제 OpenFeign + Gemini + DB 적재 성공!");
        System.out.println("적재된 Cultivation ID: " + response.cultivationId());
        System.out.println("Gemini AI 요약문: " + response.summary());
        System.out.println("=========================================");
    }
}
