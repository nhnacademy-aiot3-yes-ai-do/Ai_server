package site.yesaido.ai_server.rabbitmq;

public final class RabbitMqConstants {
    private RabbitMqConstants() {

    }
    public static final String NOTIFICATION_EXCHANGE = "yes-nhn.notification.exchange";

    public static final String DAILY_FEEDBACK_QUEUE = "yes-nhn.notification.daily.queue"; // 일일 피드백 완료 알람
    public static final String CULTIVATION_DONE_QUEUE = "yes-nhn.notification.done.queue"; // 재배 완료 알람

    public static final String HARVEST_EXCHANGE = "yes-nhn.harvest.exchange";

    public static final String AI_HARVEST_QUEUE = "yes-nhn.ai.harvest.queue"; // 수확 완료

    // DLQ
    public static final String DLX_NAME = "yes-nhn.dlx";
    public static final String DLQ_QUEUE = "yes-nhn.dlq";
    public static final String DLX_KEY = "x-dead-letter-exchange";
}
