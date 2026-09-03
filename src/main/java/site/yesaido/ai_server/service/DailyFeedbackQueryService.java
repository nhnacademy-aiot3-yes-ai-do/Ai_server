package site.yesaido.ai_server.service;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.yesaido.ai_server.client.CultivationClient;
import site.yesaido.ai_server.dto.client.cultivation.CultivationDetailResponse;
import site.yesaido.ai_server.dto.daily_feedback.DailyFeedbackResponse;
import site.yesaido.ai_server.exception.DailyFeedbackNotFoundException;
import site.yesaido.ai_server.exception.InvalidDailyFeedbackRequestException;
import site.yesaido.ai_server.exception.UnauthorizedAccessException;

import java.time.LocalDate;

/**
 * 운영 환경에서 사용자의 재배지 접근 권한을 확인한 뒤
 * 저장된 일일 피드백을 조회하는 서비스입니다.
 *
 * <p>OWNER뿐만 아니라 해당 재배지에 등록된 모든 멤버가 조회할 수 있도록
 * Cultivation Server의 일반 재배지 조회 API로 접근 권한을 확인합니다.</p>
 *
 * <p>권한이 없는 사용자에게 피드백 존재 여부가 노출되지 않도록 반드시
 * 접근 권한을 먼저 확인한 후 DB에서 일일 피드백을 조회합니다. 외부 Feign
 * 호출과 DB 조회를 하나의 트랜잭션으로 묶지 않습니다.</p>
 */
@Service
@RequiredArgsConstructor
public class DailyFeedbackQueryService {

    private final CultivationClient cultivationClient;
    private final DailyFeedbackPersistenceService dailyFeedbackPersistenceService;

    /**
     * 사용자의 재배지 접근 권한을 확인하고 지정한 날짜의 일일 피드백을
     * 운영용 응답으로 반환합니다.
     *
     * <p>Cultivation Server의 권한 확인이 성공한 경우에만 피드백 존재
     * 여부를 조회합니다. 재배지 또는 피드백이 존재하지 않으면 동일한
     * 404 예외로 처리하며, 권한이 없으면 403 예외로 처리합니다.</p>
     *
     * @param userId 조회를 요청한 사용자 ID
     * @param cultivationId 조회할 재배지 ID
     * @param feedbackDate 조회할 일일 피드백 날짜
     * @return Context Snapshot을 포함하지 않는 운영용 일일 피드백 응답
     * @throws InvalidDailyFeedbackRequestException 입력값이 유효하지 않은 경우
     * @throws UnauthorizedAccessException 사용자가 재배지 멤버가 아닌 경우
     * @throws DailyFeedbackNotFoundException 재배지 또는 일일 피드백이
     *                                        존재하지 않는 경우
     */
    public DailyFeedbackResponse getDailyFeedback(Long userId, Long cultivationId, LocalDate feedbackDate) {
        validateRequest(userId, cultivationId, feedbackDate);
        validateAccess(userId, cultivationId, feedbackDate);

        return dailyFeedbackPersistenceService
                .findExisting(cultivationId, feedbackDate)
                .map(DailyFeedbackResponse::from)
                .orElseThrow(() -> new DailyFeedbackNotFoundException(cultivationId, feedbackDate)
                );
    }

    private void validateRequest(Long userId, Long cultivationId, LocalDate feedbackDate) {
        if (userId == null || userId <= 0 || cultivationId == null
                || cultivationId <= 0 || feedbackDate == null) {
            throw new InvalidDailyFeedbackRequestException();
        }
    }

    private void validateAccess(Long userId, Long cultivationId, LocalDate feedbackDate) {
        CultivationDetailResponse response;

        try {
            response = cultivationClient.getCultivation(userId, cultivationId);
        } catch (FeignException.Forbidden ignored) {
            throw new UnauthorizedAccessException();
        } catch (FeignException.NotFound ignored) {
            throw new DailyFeedbackNotFoundException(cultivationId, feedbackDate);
        }

        if (response == null) {
            throw new IllegalStateException("재배지 조회 응답이 null입니다: cultivationId=%s"
                    .formatted(cultivationId));
        }

        if (!cultivationId.equals(response.cultivationId())) {
            throw new IllegalStateException("재배지 조회 응답 ID가 요청과 일치하지 않습니다: requestedCultivationId=%s, responseCultivationId=%s"
                    .formatted(cultivationId, response.cultivationId())
            );
        }
    }
}
