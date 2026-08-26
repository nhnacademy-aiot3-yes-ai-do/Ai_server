package site.yesaido.ai_server.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

import java.time.Duration;

@Slf4j
@EnableCaching // Spring Boot 캐시 전원 켜기
@Configuration
public class  RedisCacheConfig implements CachingConfigurer {

    /*
     * 예전 DTO 패키지 경로(site.yesaido.ai_server.dto.ai.mush_summary.MushGuideResponse 등)로
     * 저장된 낡은 Redis 캐시 값을 읽으려다 클래스를 못 찾아 역직렬화가 실패하는 경우,
     * 기본 동작은 예외를 그대로 던져서 요청 자체가 500으로 죽어버림.
     * 캐시 조회/저장/삭제 실패를 여기서 로그만 남기고 삼켜서,
     * 캐시 미스로 취급하고 실제 메서드를 호출해 새로 생성 -> 정상 값으로 캐시를 덮어쓰도록(자가 치유) 함.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Redis 캐시 조회 실패 (cache={}, key={}) - 캐시 미스로 처리하고 새로 생성합니다: {}",
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Redis 캐시 저장 실패 (cache={}, key={}): {}", cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Redis 캐시 삭제 실패 (cache={}, key={}): {}", cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Redis 캐시 전체 삭제 실패 (cache={}): {}", cache.getName(), exception.getMessage());
            }
        };
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        // Redis 보안 처리 코드 추가
        // 기존의 enableUnsafeDefaultTyping()은 해커가 조작한 악성 클래스를 검증 없이 실행할 위험이 있어
        // 프로젝트의 패키지(DTO)와 자바 기본 객체만 조립을 허용하도록 방어막 침
        PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("site.yesaido.ai_server.dto") // 패키지 허용 범위 dto로 변경 <- dto 외 다른 클래스의 무단 역직렬화 방지
                .allowIfSubType("java.lang") // 자바 기본 타입(String, Integer..) 허용
                .allowIfSubType("java.util") // 자바 컬렉션(List, Map..) 허용
                .build();

        GenericJacksonJsonRedisSerializer jsonSerializer = GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(typeValidator) // JSON에 @class 타입 정보 자동 저장(꺼낼 때 어떤 DTO였는지 잃어버리지 않고 역직렬화 하기 위해서)
                .build();

        // Redis 캐시 규칙 설정
        RedisCacheConfiguration redisCacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues() // Null 값 캐싱 비활성화 <- allowIfSubType을 설정해놔서 해당하는 클래스만 허용되게 해놨는데 null 리턴해버리면 서버 요청 실패하니 비활성화로 변경
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())) // Key는 눈에 잘 보이는 문자열로 저장
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer)) // Value는 위에서 만든 JSON 직렬화기로 저장
                .entryTtl(Duration.ofDays(15)); // 15일 뒤에 데이터 삭제

        // 캐시 매니저 생성 및 반환
        return RedisCacheManager.RedisCacheManagerBuilder
                .fromConnectionFactory(redisConnectionFactory)
                .cacheDefaults(redisCacheConfiguration)
                .build();
    }
}
