package site.yesaido.ai_server.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.yesaido.ai_server.client.CultivationClient;
import site.yesaido.ai_server.dto.client.cultivation.DailyCultivationDetailResponse;

/**
 * 일일 피드백에 필요한 재배 상세정보를 조회하고 응답 계약을 검증합니다.
 *
 * <p>상위 일일 피드백 조립 계층에서 이미 조회한 OWNER 사용자 ID를
 * 재사용하여 Cultivation Server의 현재 멤버 권한 검사를 통과합니다.
 * 이 서비스는 OWNER를 다시 조회하거나 ADMIN 우회를 사용하지 않습니다.</p>
 *
 * <p>요청한 경작지 ID와 응답의 경작지 ID를 정확히 비교하여
 * 다른 경작지의 상세정보가 일일 피드백에 혼입되는 것을 방지합니다.
 * OWNER ID로 호출했는데 응답의 {@code myRole}이 {@code OWNER}가 아니라면
 * Cultivation 멤버 계약이 깨진 것으로 판단하고 즉시 실패합니다.</p>
 *
 * <p>재배 상태와 모드는 서버에 미래 값이 추가될 가능성을 고려하여
 * 이 서비스에서 허용값을 제한하지 않습니다. 상태와 모드에 따른
 * 실제 처리 여부는 이후 오케스트레이터와 수확 정책에서 판단합니다.</p>
 *
 * <p>응답의 이름은 피드백 생성 완료 이벤트에 사용하고,
 * {@code mushroomId}는 버섯 참조정보를 찾는 키로 사용하며,
 * {@code startedAt}은 재배 경과일 계산에 사용합니다.</p>
 *
 * <p>외부 호출 실패와 응답 계약 위반을 데이터 없음이나 기본 상세정보로
 * 숨기지 않고 상위 계층으로 전파합니다.</p>
 */
@Service
@RequiredArgsConstructor
public class DailyCultivationDetailService {

    private static final String OWNER_ROLE = "OWNER";

    private final CultivationClient cultivationClient;

    /**
     * OWNER 권한으로 재배 상세정보를 조회하고 응답 식별정보를 검증합니다.
     *
     * @param cultivationId 상세정보를 조회할 경작지 ID
     * @param ownerUserId Cultivation 권한 검사에 사용할 OWNER 사용자 ID
     * @return Cultivation Server가 반환한 검증 완료 상세 응답
     * @throws IllegalArgumentException 입력 ID가 null이거나 0 이하인 경우
     * @throws IllegalStateException 응답이 null이거나 경작지 ID 또는 OWNER 역할 계약이 다른 경우
     */
    public DailyCultivationDetailResponse fetch(Long cultivationId, Long ownerUserId) {
        if (cultivationId == null || cultivationId <= 0) {
            throw new IllegalArgumentException("cultivationId는 null이 아니며 0보다 커야 합니다.");
        }

        if (ownerUserId == null || ownerUserId <= 0) {
            throw new IllegalArgumentException("ownerUserId는 null이 아니며 0보다 커야 합니다.");
        }

        DailyCultivationDetailResponse response =
                cultivationClient.getDailyCultivationDetail(ownerUserId, cultivationId);

        if (response == null) {
            throw new IllegalStateException("재배 상세 응답이 null입니다: cultivationId=%s".formatted(cultivationId));
        }

        if (!cultivationId.equals(response.cultivationId())) {
            throw new IllegalStateException("재배 상세 응답 ID가 요청과 일치하지 않습니다: requestedCultivationId=%s, responseCultivationId=%s"
                    .formatted(cultivationId, response.cultivationId()));
        }

        if (!OWNER_ROLE.equals(response.myRole())) {
            throw new IllegalStateException("재배 상세 응답 역할이 OWNER가 아닙니다: cultivationId=%s, myRole=%s"
                    .formatted(cultivationId, response.myRole()));
        }

        return response;
    }
}