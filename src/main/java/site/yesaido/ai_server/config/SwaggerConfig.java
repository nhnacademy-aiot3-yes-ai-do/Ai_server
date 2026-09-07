package site.yesaido.ai_server.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * AI Server의 OpenAPI(Swagger) 문서 메타데이터를 정의합니다.
 * <p>
 * 엔드포인트별 설명은 {@code controller.docs} 패키지의 {@code XxxControllerDocs} 인터페이스에 두고,
 * 문서 노출 범위와 Swagger UI 경로는 {@code application.yml} 의 springdoc 설정에서 지정합니다.
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI aiOpenAPI(@Value("${server.port:9003}") int serverPort) {
        return new OpenAPI()
                .info(new Info()
                        .title("AI Server API")
                        .version("v1")
                        .description("""
                                AI 기반 재배 지원 서비스 API.

                                재배 상담 챗봇(RAG) · 일일 피드백 조회 · 우수 재배 인사이트 추천 ·
                                버섯 재배 가이드 생성 · 센서 임계값 AI 검증을 제공합니다.

                                ### 인증
                                모든 요청은 API Gateway를 통해 들어오며, Gateway가 JWT를 검증한 뒤
                                `X-User-Id` 헤더를 주입합니다.

                                ### 응답 형식
                                응답은 공통 래퍼 `ApiResponse` 로 감싸집니다:
                                `{ "success": true, "message": "...", "data": ... }`.
                                오류는 RFC 7807 `application/problem+json` 형식으로 반환됩니다.

                                ### 문서 범위
                                Gateway가 노출하는 `/api/v1/ai/**`, `/api/v1/mushrooms/**`, `/api/v1/admin/data` 만
                                문서화합니다. 클러스터 내부 배치 API(`/api/v1/internal/**`)와
                                `local` 프로필 테스트 API(`/api/test/**`)는 제외됩니다.
                                """))
                .servers(List.of(
                        new Server().url("https://api.yes-nhn.site").description("운영 Gateway"),
                        new Server().url("http://localhost:8000").description("로컬 Gateway"),
                        new Server().url("http://localhost:" + serverPort).description("로컬 직접 호출")
                ));
    }
}
