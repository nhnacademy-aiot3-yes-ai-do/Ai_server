package site.yesaido.ai_server.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
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

@EnableCaching // Spring Boot 캐시 전원 켜기
@Configuration
public class RedisCacheConfig {
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
                .enableDefaultTyping(typeValidator) // JSON에 @class 타입 정보 자동 저장
                .build();

        // Redis 캐시 규칙 설정
        RedisCacheConfiguration redisCacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues() // Null 값 캐싱 비활성화 <- allowIfSubType을 설정해놔서 해당하는 클래스만 허용되게 해놨는데 null 리턴해버리면 서버 요청 실패하니 비활성화로 변경
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())) // Key는 눈에 잘 보이는 문자열로 저장
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer)) // Value는 위에서 만든 JSON 직렬화기로 저장
                .entryTtl(Duration.ofDays(7)); // 7일 뒤에 데이터 삭제

        // 캐시 매니저 생성 및 반환
        return RedisCacheManager.RedisCacheManagerBuilder
                .fromConnectionFactory(redisConnectionFactory)
                .cacheDefaults(redisCacheConfiguration)
                .build();
    }
}
