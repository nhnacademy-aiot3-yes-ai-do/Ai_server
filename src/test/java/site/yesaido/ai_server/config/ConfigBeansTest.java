package site.yesaido.ai_server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.MessageConverter;

import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigBeansTest {

    @Test
    @DisplayName("ObjectMapperConfig 빈 생성 및 설정 검증")
    void objectMapperConfig() {
        ObjectMapperConfig config = new ObjectMapperConfig();
        ObjectMapper mapper = config.objectMapper();

        assertThat(mapper).isNotNull();
        assertThat(config.httpJsonMapperBuilderCustomizer()).isNotNull();
    }

    @Test
    @DisplayName("AsyncConfig TaskExecutor 빈 생성 검증")
    void asyncConfig() {
        AsyncConfig config = new AsyncConfig();
        Executor executor = config.taskExecutor();

        assertThat(executor).isNotNull();
    }

    @Test
    @DisplayName("RabbitMQConfig 빈 생성 및 바인딩 검증")
    void rabbitMQConfig() {
        RabbitMQConfig config = new RabbitMQConfig();

        MessageConverter converter = config.messageConverter();
        assertThat(converter).isNotNull();

        FanoutExchange dlx = config.deadLetterExchange();
        assertThat(dlx).isNotNull();

        Queue dlq = config.deadLetterQueue();
        assertThat(dlq).isNotNull();

        Binding dlBinding = config.deadLetterBinding();
        assertThat(dlBinding).isNotNull();

        DirectExchange harvestExchange = config.harvestExchange();
        assertThat(harvestExchange).isNotNull();

        Queue aiHarvestQueue = config.aiHarvestQueue();
        assertThat(aiHarvestQueue).isNotNull();

        Binding aiHarvestBinding = config.aiHarvestBinding();
        assertThat(aiHarvestBinding).isNotNull();

        DirectExchange notiExchange = config.notificationExchange();
        assertThat(notiExchange).isNotNull();
    }
}
