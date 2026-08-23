package site.yesaido.ai_server.config;

import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.ArgumentMatchers.anyList;
import org.mockito.ArgumentMatchers;

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

    @BeforeEach
    void setUp() {
        // 2초의 딜레이를 0초로 강제 세팅하여 테스트 속도를 0.1초 컷으로 만듦 (TimeOut 박멸)
        ReflectionTestUtils.setField(mushCacheWarmer, "delayMs", 0L);
        lenient().when(cacheManager.getCache("ai:mushroom")).thenReturn(cache);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

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

    @Test
    @DisplayName("캐시 워밍 중 예외 발생 시 catch 블록 실행 후 계속 진행 (catch 분기 검증)")
    void warming_exceptionCaught() {
        // 캐시 확인 시 강제로 예외를 던짐
        when(cache.get(anyString())).thenThrow(new RuntimeException("Redis 타임아웃 예외 발생"));

        mushCacheWarmer.warming();

        // 5번의 for문 동안 5번의 예외가 발생하지만 루프가 멈추지 않고 5번 다 도는지 검증
        verify(cache, times(5)).get(anyString());
    }

    @Test
    @DisplayName("락 획득 후 이중 체크 시 캐시가 이미 생성되어 있으면 조기 종료 (이중 체크 분기 검증)")
    void warming_acquiresLock_butCacheAlreadyFilled() {
        // 메서드 인자 안에서 직접 mock()을 생성하면 검증과 디버깅할 때 추적이 어려워져 mock 객체 지역 변수로 분리 생성
        Cache.ValueWrapper valueWrapper = mock(Cache.ValueWrapper.class);
        // 첫 번째 조회는 null(캐시 없음), 두 번째 조회(락 획득 후)는 캐시 있음(ValueWrapper) 반환
        when(cache.get(anyString()))
                .thenReturn(null)
                .thenReturn(valueWrapper); // 변수 전달
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        mushCacheWarmer.warming();

        // 이중 체크에 걸려 AI 생성이 한 번도 호출되지 않아야 함
        verify(mushService, never()).generateRealDataGuide(anyLong());
    }

    // private 메서드인 renewLock() 내부의 숨겨진 분기문 3가지 강제 테스트
    @Test
    @DisplayName("renewLock 성공 시 1L 반환 로직 검증")
    void renewLock_success() {
        // any(RedisScript.class) 대신 ArgumentMatchers.<RedisScript<Long>>any() 사용
        when(stringRedisTemplate.execute(ArgumentMatchers.<RedisScript<Long>>any(), anyList(), any(), any()))
                .thenReturn(1L);

        ReflectionTestUtils.invokeMethod(mushCacheWarmer, "renewLock", 1L, "lockKey", "uuid", Duration.ofSeconds(30));

        verify(stringRedisTemplate, times(1)).execute(ArgumentMatchers.<RedisScript<Long>>any(), anyList(), any(), any());
    }

    @Test
    @DisplayName("renewLock 실패(소유권 상실 등) 시 0L 반환 로직 검증")
    void renewLock_failure() {
        when(stringRedisTemplate.execute(ArgumentMatchers.<RedisScript<Long>>any(), anyList(), any(), any()))
                .thenReturn(0L);

        ReflectionTestUtils.invokeMethod(mushCacheWarmer, "renewLock", 1L, "lockKey", "uuid", Duration.ofSeconds(30));

        verify(stringRedisTemplate, times(1)).execute(ArgumentMatchers.<RedisScript<Long>>any(), anyList(), any(), any());
    }

    @Test
    @DisplayName("renewLock 실행 중 통신 예외 발생 catch 블록 검증")
    void renewLock_exception() {
        when(stringRedisTemplate.execute(ArgumentMatchers.<RedisScript<Long>>any(), anyList(), any(), any()))
                .thenThrow(new RuntimeException("Redis 끊김"));

        ReflectionTestUtils.invokeMethod(mushCacheWarmer, "renewLock", 1L, "lockKey", "uuid", Duration.ofSeconds(30));

        verify(stringRedisTemplate, times(1)).execute(ArgumentMatchers.<RedisScript<Long>>any(), anyList(), any(), any());
    }

    @Test
    @DisplayName("Thread.sleep 도중 쓰레드 중단(InterruptedException) 발생 시 catch 블록 검증")
    void apiDelay_interrupt() throws InterruptedException {
        ReflectionTestUtils.setField(mushCacheWarmer, "delayMs", 1000L);

        Thread thread = new Thread(() -> {
            org.springframework.test.util.ReflectionTestUtils.invokeMethod(mushCacheWarmer, "apiDelay");
        });
        thread.start();
        thread.interrupt(); // 기다릴 필요 없이 시작하자마자 바로 중단!
        thread.join();

        org.junit.jupiter.api.Assertions.assertFalse(thread.isAlive(), "쓰레드가 정상적으로 종료되어야 합니다.");
    }

    @Test
    @DisplayName("for문 내부의 바깥쪽(outer) catch 블록 커버리지 검증")
    void warming_outerExceptionCaught() {
        // 안쪽 try-catch는 무사 통과시키고, 락을 걸 때(Redis 통신) 예외를 발생시킴
        when(cache.get(anyString())).thenReturn(null);
        when(stringRedisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis 연결 완전 실패"));

        mushCacheWarmer.warming();

        // 5번 반복되는 동안 5번 모두 바깥쪽 catch 블록으로 빠지는지 검증
        verify(stringRedisTemplate, times(5)).opsForValue();
    }

    @Test
    @DisplayName("guideCache 자체가 null일 경우의 if문 분기 검증")
    void warming_nullCache() {
        // 설정 파일 누락 등으로 CacheManager가 null을 반환하는 상황 가정
        when(cacheManager.getCache(anyString())).thenReturn(null);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        mushCacheWarmer.warming();

        // NullPointerException이 터지지 않고 락 획득 시도까지 무사히 내려가는지 검증
        verify(valueOperations, times(5)).setIfAbsent(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("generateWithLock 메서드 내부의 이중 체크 try-catch 블록 검증")
    void generateWithLock_innerException() {
        // 1번째 조회(바깥쪽)는 통과, 2번째 조회(generateWithLock 안쪽)에서 예외 발생
        when(cache.get(anyString()))
                .thenReturn(null) // 첫 번째는 null 반환
                .thenThrow(new RuntimeException("역직렬화 실패 예외 발생")); // 두 번째는 에러 던짐

        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        mushCacheWarmer.warming();

        // 에러를 무시하고 무사히 재생성(generateRealDataGuide) 로직까지 도달하는지 검증
        verify(mushService, times(5)).generateRealDataGuide(anyLong());
    }
}
