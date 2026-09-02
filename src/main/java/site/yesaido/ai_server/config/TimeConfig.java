package site.yesaido.ai_server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * AI Server에서 사용할 기준 시계를 구성합니다.
 *
 * <p>Outbox Relay와 Recovery가 시스템 시간을 직접 조회하지 않고
 * Asia/Seoul 기준의 {@link Clock}을 주입받아 사용하도록 합니다.
 * 단위 테스트에서는 고정 Clock으로 교체할 수 있습니다.</p>
 */
@Configuration
public class TimeConfig {

    /**
     * Asia/Seoul 시간대를 사용하는 시스템 시계를 제공합니다.
     *
     * @return AI Server의 시간 계산에 사용할 서울 기준 Clock
     */
    @Bean
    public Clock seoulClock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}