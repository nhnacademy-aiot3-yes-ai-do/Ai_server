package site.yesaido.ai_server.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import site.yesaido.ai_server.dto.ai.insight.InsightSearchCondition;
import site.yesaido.ai_server.entity.Insight;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * create-drop은 반드시 격리된 localhost의 testdb에서만 허용합니다.
 *
 *  {@code @DataJpaTest}를 사용해 Repository와 JPA 관련 구성만 실행하고
 * Gemini 등 전체 AI 애플리케이션 Context는 실행하지 않습니다.
 *
 * datasource URL을 inline property로 고정해 환경변수에 원격 DB 주소가
 * 설정되어 있더라도 해당 DB에 연결되지 않도록 차단합니다.
 */
@EnabledIfEnvironmentVariable(
        named = "DB_HOST",
        matches = "^(localhost|127\\.0\\.0\\.1)$"
)
@EnabledIfEnvironmentVariable(
        named = "DB_NAME",
        matches = "^testdb$"
)
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url="
                + "jdbc:postgresql://127.0.0.1:5432/testdb"
                + "?currentSchema=public",
        // CI 전용 PostgreSQL 컨테이너의 공개 테스트 계정이며 운영 비밀정보가 아닙니다.
        "spring.datasource.username=test",
        "spring.datasource.password=test1234"
})
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
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
        List<Insight> result = insightRepository.findSimilarCandidates(
                condition,
                PageRequest.of(0, 5)
        );

        assertThat(result).isNotNull();
    }
}
