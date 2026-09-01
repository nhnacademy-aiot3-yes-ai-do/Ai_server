package site.yesaido.ai_server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
 */
@Configuration
public class ObjectMapperConfig {

    /**
     * Java 시간 타입을 ISO-8601 문자열로 처리하는 ObjectMapper를 생성합니다.
     *
     * @return Vision 및 일일 피드백 Context 변환용 ObjectMapper
     */
    @Bean
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }
}
