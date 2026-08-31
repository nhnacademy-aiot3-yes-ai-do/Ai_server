package site.yesaido.ai_server.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.yesaido.ai_server.client.CultivationClient;
import site.yesaido.ai_server.dto.client.cultivation.CultivationMemberListResponse;
import site.yesaido.ai_server.dto.client.cultivation.CultivationMemberResponse;

/**
 * 일일 피드백 수집에 필요한 재배 OWNER의 사용자 ID를 조회하는 서비스입니다.
 *
 * <p>현재 Cultivation Server의 {@code X-User-Role: ADMIN} 우회 계약을
 * 내부 일일 배치에서만 사용하는 임시 연결 방식입니다. 내부 배치 요청자 ID
 * {@code 0L}과 ADMIN 역할은 외부 사용자의 인증·인가 수단이 아니며,
 * 사용자 요청을 대신 인증하는 값으로 사용하면 안 됩니다.</p>
 *
 * <p>조회한 OWNER의 사용자 ID는 사용자 식별 헤더가 필요한 센서 추이 및
 * 환경 유지율 조회와 {@code DailyFeedbackGeneratedEvent}의 사용자 ID에
 * 사용합니다.</p>
 *
 * <p>재배마다 OWNER가 정확히 한 명이어야 한다는 데이터 불변식이 깨지면
 * 첫 번째 OWNER나 임의의 사용자를 선택하지 않고 즉시 실패합니다.</p>
 */
@Service
@RequiredArgsConstructor
public class CultivationOwnerService {

    private static final long INTERNAL_BATCH_REQUESTER_ID = 0L;
    private static final String INTERNAL_ADMIN_ROLE = "ADMIN";
    private static final String OWNER_ROLE = "OWNER";

    private final CultivationClient cultivationClient;

    /**
     * 지정한 재배의 멤버 중 정확히 한 명인 OWNER의 사용자 ID를 반환합니다.
     *
     * @param cultivationId OWNER를 조회할 경작지 ID
     * @return 해당 경작지 OWNER의 사용자 ID
     * @throws IllegalArgumentException cultivationId가 null이거나 0 이하인 경우
     * @throws IllegalStateException 응답이 null이거나 OWNER가 정확히 한 명이 아닌 경우
     */
    public Long findOwnerUserId(Long cultivationId) {
        if (cultivationId == null || cultivationId <= 0) {
            throw new IllegalArgumentException("유효하지 않은 cultivationId입니다: cultivationId=%s".formatted(cultivationId));
        }

        CultivationMemberListResponse response = cultivationClient
                .getCultivationMembers(cultivationId, INTERNAL_BATCH_REQUESTER_ID, INTERNAL_ADMIN_ROLE);

        if (response == null) {
            throw new IllegalStateException("재배 멤버 응답이 null입니다: cultivationId=%s".formatted(cultivationId));
        }

        CultivationMemberResponse owner = null;

        for (CultivationMemberResponse member : response.memberResponses()) {
            if (!OWNER_ROLE.equals(member.role())) {
                continue;
            }

            if (owner != null) {
                throw new IllegalStateException("재배 OWNER가 두 명 이상입니다: cultivationId=%s".formatted(cultivationId));
            }

            owner = member;
        }

        if (owner == null) {
            throw new IllegalStateException("재배 OWNER가 존재하지 않습니다: cultivationId=%s".formatted(cultivationId));
        }

        return owner.userId();
    }
}
