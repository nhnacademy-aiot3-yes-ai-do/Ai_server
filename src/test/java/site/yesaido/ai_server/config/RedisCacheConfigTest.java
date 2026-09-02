package site.yesaido.ai_server.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RedisCacheConfigTest {

    @Test
    @DisplayName("CacheErrorHandler가 예외를 밖으로 던지지 않고 정상적으로 삼키는지(자가 치유) 테스트")
    void errorHandler_SwallowException_Test() {
        RedisCacheConfig config = new RedisCacheConfig();
        CacheErrorHandler handler = config.errorHandler();
        Cache mockCache = mock(Cache.class);
        RuntimeException exception = new RuntimeException("강제 발생 역직렬화 예외");

        handler.handleCacheGetError(exception, mockCache, "testKey");
        handler.handleCachePutError(exception, mockCache, "testKey", "testValue");
        handler.handleCacheEvictError(exception, mockCache, "testKey");
        handler.handleCacheClearError(exception, mockCache);

        assertThat(handler).isNotNull();
    }

    @Test
    @DisplayName("cacheManager 빈 정상 생성 검증")
    void cacheManager_BeanCreation_Test() {
        RedisCacheConfig config = new RedisCacheConfig();
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);

        CacheManager cacheManager = config.cacheManager(connectionFactory);

        assertThat(cacheManager).isNotNull();
    }
}
