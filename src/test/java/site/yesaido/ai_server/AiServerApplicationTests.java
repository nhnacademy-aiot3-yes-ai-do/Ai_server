package site.yesaido.ai_server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import static org.assertj.core.api.Assertions.assertThat;

class AiServerApplicationTests {

    @Test
    @DisplayName("AiServerApplication에 필수 어노테이션(@EnableAsync, @EnableFeignClients)이 선언되어 있는지 검증")
    void mainApplicationAnnotationsTest() {
        EnableAsync asyncAnnotation = AiServerApplication.class.getAnnotation(EnableAsync.class);
        EnableFeignClients feignAnnotation = AiServerApplication.class.getAnnotation(EnableFeignClients.class);

        assertThat(asyncAnnotation).isNotNull();
        assertThat(feignAnnotation).isNotNull();
    }

    @Test
    @DisplayName("AiServerApplication 클래스 인스턴스 생성 검증")
    void applicationInstanceTest() {
        assertDoesNotThrow(AiServerApplication::new);
    }
}
