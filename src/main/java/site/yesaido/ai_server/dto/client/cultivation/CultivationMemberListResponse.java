package site.yesaido.ai_server.dto.client.cultivation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Cultivation Server가 반환하는 재배 멤버 목록 응답입니다.
 *
 * <p>이 응답에는 {@code cultivationId}가 포함되지 않으므로 어느 재배의
 * 멤버 목록인지는 요청 URL에 사용한 {@code cultivationId}와 함께
 * 관리해야 합니다.</p>
 *
 * <p>빈 목록은 역직렬화 가능한 응답으로 허용합니다. OWNER가 없거나 여러 명인
 * 상황은 이 DTO에서 판단하지 않으며, 이후 소유자 조회 서비스에서 정확히 한 명의
 * OWNER가 존재하는지 검사하고 위반 시 {@link IllegalStateException}으로 처리합니다.</p>
 *
 * @param memberResponses Cultivation Server가 제공한 순서의 재배 멤버 목록
 */
public record CultivationMemberListResponse(
        List<CultivationMemberResponse> memberResponses
) {

    public CultivationMemberListResponse {
        if (memberResponses == null) {
            throw new IllegalArgumentException("memberResponses는 필수이며 null일 수 없습니다.");
        }

        Set<Long> memberIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();

        for (CultivationMemberResponse memberResponse : memberResponses) {
            if (memberResponse == null) {
                throw new IllegalArgumentException("memberResponses에는 null 요소가 포함될 수 없습니다.");
            }

            if (!memberIds.add(memberResponse.memberId())) {
                throw new IllegalArgumentException("memberResponses에는 동일한 memberId가 중복될 수 없습니다.");
            }

            if (!userIds.add(memberResponse.userId())) {
                throw new IllegalArgumentException("memberResponses에는 동일한 userId가 중복될 수 없습니다.");
            }
        }

        memberResponses = List.copyOf(memberResponses);
    }
}
