package site.yesaido.ai_server.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.DateTimeFeature;

/**
 * Vision 응답과 일일 피드백 Context 변환에 공통으로 사용할
 * Jackson 2 ObjectMapper를 구성합니다.
 *
 * <p>Vision 응답 전체를 {@code JsonNode}로 변환하는 과정과
 * 일일 피드백 Context를 PostgreSQL JSONB
 * {@code context_snapshot}으로 저장하는 과정에서 사용합니다.</p>
 *
 * <p>{@link JavaTimeModule}을 등록하고 날짜의 timestamp 직렬화를
 * 비활성화하여 {@code LocalDate}, {@code LocalDateTime},
 * {@code OffsetDateTime}을 ISO-8601 문자열로 직렬화합니다.</p>
 *
 * <p>{@code OffsetDateTime} 역직렬화 시 입력 JSON의 원래 offset을
 * 보존합니다. {@code +09:00}으로 전달된 Snapshot 시각이 UTC로 자동
 * 변경되지 않게 하여 일일 피드백의 Asia/Seoul 날짜·시각 계약을
 * 유지합니다.</p>
 */
@Configuration
public class ObjectMapperConfig {

    /**
     * Java 시간 타입을 ISO-8601 문자열로 처리하고 입력 offset을
     * 그대로 보존하는 ObjectMapper를 생성합니다.
     *
     * @return Vision 및 일일 피드백 Context 변환용 ObjectMapper
     */
    @Bean
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    /**
     * Feign과 Spring MVC가 사용하는 Jackson 3의 시간대와
     * 외부 응답 호환성 설정을 구성합니다.
     *
     * @return HTTP JSON 변환용 Jackson 3 Builder 커스터마이저
     */
    @Bean
    public JsonMapperBuilderCustomizer httpJsonMapperBuilderCustomizer() {
        return builder -> builder
                .disable(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                .disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
}
