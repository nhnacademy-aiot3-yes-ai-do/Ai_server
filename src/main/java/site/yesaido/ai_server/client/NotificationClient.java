package site.yesaido.ai_server.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import site.yesaido.ai_server.dto.client.notification.DailyNotificationSummariesResponse;
import site.yesaido.ai_server.dto.client.notification.DailyNotificationSummaryRequest;

/**
 * Gateway를 거치지 않고 Kubernetes 내부의 Notification Server를 직접 호출하는 Client입니다.
 *
 * <p>조회하는 통계는 사용자에게 실제 전달된 Delivery 기록이 아니라,
 * Notification Server에 저장된 Notification 원본 이벤트를 기준으로 집계됩니다.</p>
 */
@FeignClient(
        name = "notification-server",
        url = "${feign.client.notification-server.url}",
        path = "/api/v1/internal/notifications"
)
public interface NotificationClient {

    @PostMapping(
            value = "/daily-summaries",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    DailyNotificationSummariesResponse getDailySummaries(
            @RequestBody DailyNotificationSummaryRequest request
    );
}
