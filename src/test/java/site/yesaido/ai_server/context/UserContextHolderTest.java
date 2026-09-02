package site.yesaido.ai_server.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserContextHolderTest {

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    @DisplayName("UserContextHolder ThreadLocal 저장, 조회, 삭제 검증")
    void contextHolder_success() {
        UserContextHolder.setUserId(12345L);
        assertThat(UserContextHolder.getUserId()).isEqualTo(12345L);

        UserContextHolder.clear();
        assertThat(UserContextHolder.getUserId()).isNull();
    }
}
