package site.yesaido.ai_server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * @EnableAsync만 선언하고 Executor 빈을 만들지 않으면 요청 올 때마다 무제한으로 스레드를 생성하고 버리는 메모리 누수 문제가 발생
 * 스레드 풀 크기와 큐 용량 제한할 수 있는 빈 설정 등록 후 Async에 저장
 */
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);              // 기본 스레드 수
        executor.setMaxPoolSize(10);             // 최대 스레드 수
        executor.setQueueCapacity(25);           // 대기 큐 크기
        executor.setThreadNamePrefix("Async-Executor-"); // 로그에서 확인할 스레드 이름 Prefix
        executor.initialize();
        return executor;
    }
}
