package site.yesaido.ai_server.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import site.yesaido.ai_server.dto.ai.insight.InsightSearchCondition;
import site.yesaido.ai_server.entity.Insight;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
// CI/CD 환경에서 테스트용 테이블을 자동으로 생성하고 끝나면 삭제하도록 덮어쓰기
@TestPropertySource(properties = { "spring.jpa.hibernate.ddl-auto=create-drop"})
class InsightRepositoryTest {
    @Autowired
    private InsightRepository insightRepository;

    @Test
    @DisplayName("findSimilarCandidates JPQL SpEL 쿼리 파싱 및 정상 실행 검증")
    void findSimilarCandidatesQueryParsingTest() {
        // given
        InsightSearchCondition condition = new InsightSearchCondition(
                1L,
                new BigDecimal("18.00"), new BigDecimal("22.00"),
                new BigDecimal("75.00"), new BigDecimal("85.00"),
                new BigDecimal("650.00"), new BigDecimal("850.00"),
                new BigDecimal("50.00"), new BigDecimal("150.00"),
                List.of(-1L)
        );

        // when & then: SpEL 표현식이 포함된 JPQL 쿼리가 예외 없이 정상 해석 및 실행되는지 확인
        List<Insight> result = insightRepository.findSimilarCandidates(condition, PageRequest.of(0, 5));

        assertThat(result).isNotNull();
    }
}
