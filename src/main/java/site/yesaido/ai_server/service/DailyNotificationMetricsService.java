package site.yesaido.ai_server.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.yesaido.ai_server.client.NotificationClient;
import site.yesaido.ai_server.dto.client.notification.DailyNotificationSummariesResponse;
import site.yesaido.ai_server.dto.client.notification.DailyNotificationSummaryRequest;
import site.yesaido.ai_server.dto.client.notification.DailyNotificationSummaryResponse;
import site.yesaido.ai_server.dto.daily_feedback.DailyNotificationMetrics;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Notification Server에 저장된 원본 이벤트의 일일 통계를 조회하는 서비스입니다.
 *
 * <p>대상 경작지가 없는 경우는 정상적인 상황으로 보고 빈 Map을 반환하며,
 * Notification Server를 호출하지 않습니다. HTTP 호출 실패는 이벤트가 0건인
 * 상황으로 변환하지 않고 상위 일일 피드백 조립 계층으로 그대로 전파합니다.</p>
 *
 * <p>반환 Map의 순서는 요청 DTO에서 중복을 제거한 경작지 ID 순서와 같습니다.</p>
 */
@Service
@RequiredArgsConstructor
public class DailyNotificationMetricsService {

    private final NotificationClient notificationClient;

    /**
     * 지정한 날짜와 경작지 목록의 Notification 원본 이벤트 통계를 조회합니다.
     *
     * @param date 일일 통계 기준 날짜
     * @param cultivationIds 통계를 조회할 경작지 ID 목록
     * @return 정규화된 요청 순서로 정렬된 경작지별 Notification 지표
     */
    public Map<Long, DailyNotificationMetrics> fetchDailyMetrics(LocalDate date, List<Long> cultivationIds) {
        if (date == null) {
            throw new IllegalArgumentException("date는 필수이며 null일 수 없습니다.");
        }

        if (cultivationIds == null) {
            throw new IllegalArgumentException("cultivationIds는 필수이며 null일 수 없습니다.");
        }

        if (cultivationIds.isEmpty()) {
            return Map.of();
        }

        DailyNotificationSummaryRequest request = new DailyNotificationSummaryRequest(date, cultivationIds);
        DailyNotificationSummariesResponse response = notificationClient.getDailySummaries(request);

        if (response == null) {
            throw new IllegalStateException("Notification 일일 통계 응답이 null입니다.");
        }

        if (!request.date().equals(response.date())) {
            throw new IllegalStateException("Notification 응답 날짜가 요청 날짜와 일치하지 않습니다.");
        }

        Map<Long, DailyNotificationSummaryResponse> summariesByCultivationId = new LinkedHashMap<>();

        for (DailyNotificationSummaryResponse summary : response.summaries()) {
            if (summary == null) {
                throw new IllegalStateException("Notification 응답에 null summary가 포함되어 있습니다.");
            }

            DailyNotificationSummaryResponse previousSummary = summariesByCultivationId.putIfAbsent(summary.cultivationId(), summary);

            if (previousSummary != null) {
                throw new IllegalStateException("Notification 응답에 중복된 cultivationId가 포함되어 있습니다.");
            }
        }

        Set<Long> requestedCultivationIds = new HashSet<>(request.cultivationIds());

        if (!requestedCultivationIds.equals(summariesByCultivationId.keySet())) {
            throw new IllegalStateException("Notification 응답의 경작지 ID가 요청과 일치하지 않습니다.");
        }

        Map<Long, DailyNotificationMetrics> metricsByCultivationId = new LinkedHashMap<>();

        for (Long cultivationId : request.cultivationIds()) {
            DailyNotificationSummaryResponse summary = summariesByCultivationId.get(cultivationId);
            DailyNotificationMetrics metrics = DailyNotificationMetrics.fromSuccessfulResponse(summary);

            metricsByCultivationId.put(cultivationId, metrics);
        }

        return Collections.unmodifiableMap(metricsByCultivationId);
    }
}
/**
 *  요청 ID 목록
 *   → LinkedHashSet으로 중복 제거 + 최초 순서 유지
 *   → Notification 응답을 cultivationId 기준 Map으로 변환
 *   → HashSet으로 요청 ID와 응답 ID가 정확히 같은지 확인
 *   → 요청 ID 순서대로 통계를 세분화
 *   → LinkedHashMap으로 순서 보존
 *   → 수정 불가능한 Map으로 반환
 */
