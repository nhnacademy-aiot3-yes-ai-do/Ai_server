package site.yesaido.ai_server.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RedisCacheConfigTest {
    @Test
    @DisplayName("CacheErrorHandler가 예외를 밖으로 던지지 않고 정상적으로 삼키는지(자가 치유) 테스트")
    void errorHandler_SwallowException_Test() {
        // Given
        RedisCacheConfig config = new RedisCacheConfig();
        CacheErrorHandler handler = config.errorHandler();
        Cache mockCache = mock(Cache.class);
        RuntimeException exception = new RuntimeException("강제 발생 역직렬화 예외");

        // When & Then
        // 예외가 터지더라도 밖으로 throw 되지 않고 로그만 남기며 조용히 넘어가야 성공
        handler.handleCacheGetError(exception, mockCache, "testKey");
        handler.handleCachePutError(exception, mockCache, "testKey", "testValue");
        handler.handleCacheEvictError(exception, mockCache, "testKey");
        handler.handleCacheClearError(exception, mockCache);

        assertThat(handler).isNotNull(); // 코드 도달 확인
    }
}
