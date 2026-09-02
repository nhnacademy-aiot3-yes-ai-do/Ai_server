package site.yesaido.ai_server.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import site.yesaido.ai_server.dto.common.ApiResponse;
import site.yesaido.ai_server.dto.daily_feedback.DailyFeedbackBatchResult;
import site.yesaido.ai_server.entity.DailyFeedback;
import site.yesaido.ai_server.service.DailyFeedbackBatchService;
import site.yesaido.ai_server.service.DailyFeedbackPersistenceService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 일일 피드백 흐름을 로컬에서 통합 검증하기 위한 전용 컨트롤러입니다.
 *
 * <p>{@code local} 프로필에서만 활성화되며, 현재 활성 센서 Snapshot을
 * 기준으로 일일 피드백 배치를 직접 실행하고 DB에 저장된 피드백 본문과
 * Context Snapshot을 확인하는 용도로 사용합니다.</p>
 *
 * <p>OWNER 권한 검사가 없는 테스트 API이므로 운영용 사용자 조회 API가
 * 아닙니다. 운영용 조회 기능과 정기적인 스케줄 실행은 이후 별도 계층에서
 * 구현해야 합니다.</p>
 *
 * <p>피드백 본문과 Context Snapshot은 응답으로만 반환하며 로그에는
 * 기록하지 않습니다. RabbitMQ 이벤트 발행이나 DB 직접 저장도
 * 수행하지 않습니다.</p>
 */
@Profile("local")
@RestController
@RequestMapping("/api/test/daily-feedbacks")
@RequiredArgsConstructor
public class DailyFeedbackLocalController {

    private final ObjectMapper objectMapper;

    private final DailyFeedbackBatchService dailyFeedbackBatchService;
    private final DailyFeedbackPersistenceService dailyFeedbackPersistenceService;

    /**
     * 지정한 날짜의 일일 피드백 배치를 로컬에서 수동 실행합니다.
     *
     * <p>현재 활성 센서 Snapshot에서 확인된 모든 대상 재배지에 대해
     * 해당 날짜의 피드백 생성을 실행하고 대상별 생성·기존·실패 결과를
     * 반환합니다.</p>
     *
     * <p>로컬 통합 검증을 위한 수동 실행 API이며 운영 스케줄러나
     * 운영용 사용자 API를 대신하지 않습니다.</p>
     *
     * @param date 피드백을 생성할 ISO-8601 달력 날짜
     * @return 배치 실행 결과를 포함한 공통 성공 응답
     */
    @PostMapping("/run")
    public ApiResponse<DailyFeedbackBatchResult> runDailyFeedbackBatch(
            @RequestParam("date")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        DailyFeedbackBatchResult result = dailyFeedbackBatchService.execute(date);

        return ApiResponse.success(result);
    }

    /**
     * 지정한 재배지와 날짜에 저장된 일일 피드백을 로컬에서 조회합니다.
     *
     * <p>DB 엔티티를 직접 노출하지 않고 로컬 조회 전용 응답으로 변환하여
     * 피드백 본문과 생성 근거 Context Snapshot을 확인할 수 있게 합니다.
     * 저장 결과가 없으면 HTTP 404를 반환합니다.</p>
     *
     * <p>{@code local} 프로필에서만 사용할 수 있는 통합 검증 API이며,
     * OWNER 권한을 검사하는 운영용 사용자 조회 API는 이후 별도로
     * 구현해야 합니다.</p>
     *
     * @param cultivationId 조회할 재배지 ID
     * @param date 조회할 피드백 날짜
     * @return 저장된 피드백 응답 또는 존재하지 않으면 HTTP 404
     */
    @GetMapping("/{cultivation-id}")
    public ResponseEntity<ApiResponse<DailyFeedbackLocalResponse>> getDailyFeedback(
            @PathVariable("cultivation-id") Long cultivationId,
            @RequestParam("date")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        Optional<DailyFeedback> existing = dailyFeedbackPersistenceService.findExisting(cultivationId, date);

        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        DailyFeedbackLocalResponse response = DailyFeedbackLocalResponse.from(existing.get(), objectMapper);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 로컬 통합 검증에서 저장된 일일 피드백 내용을 확인하기 위한 응답입니다.
     *
     * <p>JPA 엔티티 자체를 반환하지 않으며, Context Snapshot은
     * {@link DailyFeedback#getContextSnapshot()}이 제공하는 방어적
     * 복사본을 사용합니다.</p>
     *
     * @param id 저장된 일일 피드백 ID
     * @param cultivationId 재배지 ID
     * @param feedbackDate 피드백 대상 날짜
     * @param hasVisionAnalysis Vision 분석 반영 여부
     * @param content 생성된 일일 피드백 원문
     * @param contextSnapshot 피드백 생성 근거 Context Snapshot 복사본
     * @param createdAt 최초 DB 생성 시각
     */
    public record DailyFeedbackLocalResponse(
            Long id,
            Long cultivationId,
            LocalDate feedbackDate,
            boolean hasVisionAnalysis,
            String content,
            Map<String, Object> contextSnapshot,
            LocalDateTime createdAt
    ) {

        private static final TypeReference<LinkedHashMap<String, Object>>
                CONTEXT_SNAPSHOT_TYPE = new TypeReference<>() {
        };

        /**
         * 저장된 엔티티를 로컬 조회 전용 응답으로 변환합니다.
         *
         * @param feedback 조회된 일일 피드백 엔티티
         * @param objectMapper Context Snapshot 변환에 사용할 Jackson 2 ObjectMapper
         * @return 엔티티를 직접 노출하지 않는 로컬 조회 응답
         */
        public static DailyFeedbackLocalResponse from(DailyFeedback feedback, ObjectMapper objectMapper) {
            LinkedHashMap<String, Object> contextSnapshot = objectMapper.convertValue(feedback.getContextSnapshot(), CONTEXT_SNAPSHOT_TYPE);

            return new DailyFeedbackLocalResponse(
                    feedback.getId(),
                    feedback.getCultivationId(),
                    feedback.getFeedbackDate(),
                    feedback.isHasVisionAnalysis(),
                    feedback.getContent(),
                    contextSnapshot,
                    feedback.getCreatedAt()
            );
        }
    }
}
