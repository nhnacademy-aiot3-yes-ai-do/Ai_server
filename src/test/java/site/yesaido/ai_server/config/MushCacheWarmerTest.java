package site.yesaido.ai_server.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import site.yesaido.ai_server.service.MushService;
import java.time.Duration;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MushCacheWarmerTest {
    @Mock
    private MushService mushService;
    @Mock
    private CacheManager cacheManager;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private Cache cache;
    @InjectMocks
    private MushCacheWarmer mushCacheWarmer;

    @Test
    @DisplayName("캐시가 없고 락을 획득했을 때 AI 가이드 생성 메서드가 호출되는지 검증")
    void warmingLockSuccessTest() {
        // given
        given(cacheManager.getCache("ai:mushroom")).willReturn(cache);
        given(cache.get(anyString())).willReturn(null); // 캐시 없음
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).willReturn(true); // 락 획득 성공

        // when
        mushCacheWarmer.warming();

        // then
        verify(mushService, times(5)).generateRealDataGuide(anyLong()); // 버섯 5종 가이드라인 생성 메서드 5회 호출 됬나 확인
        // Redis 락 저장 시 UUID 토큰 형태와 90 TTL로 5회 요청됬나 확인
        verify(valueOperations, times(5)).setIfAbsent(anyString(), argThat(token -> {
            try {
                UUID.fromString(token);
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }), eq(Duration.ofSeconds(90)));
        verify(stringRedisTemplate, times(5)).execute(any(), anyList(), anyString());
    }

    @Test
    @DisplayName("이미 캐시가 존재하는 경우 AI 가이드 생성을 진행하지 않고 Skip")
    void warmingAlreadyCachedTest() {
        // given
        given(cacheManager.getCache("ai:mushroom")).willReturn(cache);
        given(cache.get(anyString())).willReturn(() -> "cachedData"); // 이미 캐시 존재

        // when
        mushCacheWarmer.warming();

        // then
        verify(mushService, never()).generateRealDataGuide(anyLong());
    }
}
