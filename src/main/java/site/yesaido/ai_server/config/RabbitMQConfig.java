package site.yesaido.ai_server.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static site.yesaido.ai_server.rabbitmq.RabbitMqConstants.*;

@Configuration
public class RabbitMQConfig {

    @Bean // 자바 객체 JSON 변환
    public MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean // RabbitTemplate에 주입
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }

    // 공통 Dead Letter Exchange
    @Bean
    public FanoutExchange deadLetterExchange() {
        return new FanoutExchange(DLX_NAME);
    }

    // 공통 Dead Letter Queue
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder
                .durable(DLQ_QUEUE)
                .build();
    }

    // DLX와 DLQ 연결
    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder
                .bind(deadLetterQueue())
                .to(deadLetterExchange());
    }

    // Cultivation -> AI 수확 완료 이벤트 Exchange
    @Bean
    public DirectExchange harvestExchange() {
        return new DirectExchange(HARVEST_EXCHANGE);
    }

    // AI가 수확 이벤트를 받는 Queue
    @Bean
    public Queue aiHarvestQueue() {
        return QueueBuilder
                .durable(AI_HARVEST_QUEUE)
                .withArgument(DLX_KEY, DLX_NAME)
                .build();
    }

    // Exchange -> AI Queue 연결
    @Bean
    public Binding aiHarvestBinding() {
        return BindingBuilder
                .bind(aiHarvestQueue())
                .to(harvestExchange())
                .with(AI_HARVEST_QUEUE);
    }

    @Bean
    public DirectExchange notificationExchange() {
        return new DirectExchange(NOTIFICATION_EXCHANGE);
    }



}
