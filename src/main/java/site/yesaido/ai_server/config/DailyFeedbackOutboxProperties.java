package site.yesaido.ai_server.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 일일 피드백 Outbox의 발행, 재시도 및 복구 정책을 제공하는 설정입니다.
 *
 * <p>이 클래스는 Outbox 처리 정책의 단일 설정 원본이며,
 * {@code application.yml}의 {@code daily-feedback.outbox} 설정을
 * 타입 안전한 Java 값으로 바인딩합니다.</p>
 *
 * <p>환경변수 처리는 {@code application.yml}에서 수행하고,
 * 이 클래스는 변환이 끝난 값만 사용합니다. Spring Boot의 relaxed
 * binding에 따라 kebab-case 설정 이름은 camelCase 필드와 setter에
 * 자동으로 연결됩니다.</p>
 *
 * <p>잘못된 값은 서버 시작 시점에 거부하여 운영 중 무한 재시도,
 * 과도한 배치 처리 또는 발행 확인 중인 이벤트의 잘못된 복구를
 * 방지합니다.</p>
 *
 * <p>{@code RabbitTemplate} 자체의 retry는 사용하지 않으며,
 * 발행 재시도 횟수와 백오프는 Outbox가 관리합니다.</p>
 */
@Getter
@Component
@ConfigurationProperties(prefix = "daily-feedback.outbox")
public class DailyFeedbackOutboxProperties {

    private int batchSize = 20;

    private int maxAttempts = 3;

    private Duration initialBackoff = Duration.ofSeconds(30);

    private Duration maxBackoff = Duration.ofMinutes(5);

    private Duration publisherConfirmTimeout = Duration.ofSeconds(5);

    private Duration staleTimeout = Duration.ofMinutes(2);

    private Duration relayInterval = Duration.ofSeconds(5);

    private Duration recoveryInterval = Duration.ofMinutes(1);

    /**
     * 한 번에 선점할 Outbox 최대 개수를 설정합니다.
     *
     * @param batchSize 한 번에 선점할 최대 개수
     */
    public void setBatchSize(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batch-size는 0보다 커야 합니다.");
        }

        this.batchSize = batchSize;
    }

    /**
     * 최종 실패 전 허용할 최대 발행 시도 횟수를 설정합니다.
     *
     * @param maxAttempts 최대 발행 시도 횟수
     */
    public void setMaxAttempts(int maxAttempts) {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("max-attempts는 0보다 커야 합니다.");
        }

        this.maxAttempts = maxAttempts;
    }

    /**
     * 첫 번째 발행 실패 후 적용할 대기 시간을 설정합니다.
     *
     * @param initialBackoff 최초 재시도 대기 시간
     */
    public void setInitialBackoff(Duration initialBackoff) {
        this.initialBackoff = requirePositiveDuration(initialBackoff, "initial-backoff");
    }

    /**
     * 지수 백오프가 증가할 수 있는 최대 시간을 설정합니다.
     *
     * @param maxBackoff 최대 재시도 대기 시간
     */
    public void setMaxBackoff(Duration maxBackoff) {
        this.maxBackoff = requirePositiveDuration(
                maxBackoff,
                "max-backoff"
        );
    }

    /**
     * RabbitMQ Broker의 발행 확인을 기다릴 최대 시간을 설정합니다.
     *
     * @param publisherConfirmTimeout 발행 확인 제한 시간
     */
    public void setPublisherConfirmTimeout(Duration publisherConfirmTimeout) {
        this.publisherConfirmTimeout = requirePositiveDuration(publisherConfirmTimeout, "publisher-confirm-timeout");
    }

    /**
     * SENDING 상태를 오래된 선점으로 판단할 시간을 설정합니다.
     *
     * @param staleTimeout 오래된 선점 판단 시간
     */
    public void setStaleTimeout(Duration staleTimeout) {
        this.staleTimeout = requirePositiveDuration(staleTimeout, "stale-timeout");
    }

    /**
     * PENDING Outbox 발행 작업의 실행 간격을 설정합니다.
     *
     * @param relayInterval 발행 작업 실행 간격
     */
    public void setRelayInterval(Duration relayInterval) {
        this.relayInterval = requirePositiveDuration(relayInterval, "relay-interval");
    }

    /**
     * 오래된 SENDING Outbox 복구 작업의 실행 간격을 설정합니다.
     *
     * @param recoveryInterval 복구 작업 실행 간격
     */
    public void setRecoveryInterval(Duration recoveryInterval) {
        this.recoveryInterval = requirePositiveDuration(
                recoveryInterval,
                "recovery-interval"
        );
    }

    /**
     * 모든 설정이 바인딩된 뒤 설정값 사이의 관계를 검증합니다.
     */
    @PostConstruct
    private void validateRelationships() {
        if (maxBackoff.compareTo(initialBackoff) < 0) {
            throw new IllegalStateException("max-backoff는 initial-backoff보다 짧을 수 없습니다.");
        }

        if (staleTimeout.compareTo(publisherConfirmTimeout) <= 0) {
            throw new IllegalStateException("stale-timeout은 publisher-confirm-timeout보다 길어야 합니다.");
        }
    }

    private Duration requirePositiveDuration(Duration value, String propertyName) {
        if (value == null) {
            throw new IllegalArgumentException(propertyName + "은 null일 수 없습니다.");
        }

        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(propertyName + "은 0보다 긴 기간이어야 합니다.");
        }

        return value;
    }
}
