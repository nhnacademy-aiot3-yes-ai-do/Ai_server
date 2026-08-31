package site.yesaido.ai_server.dto.client.cultivation;

import java.time.LocalDateTime;

/**
 * Cultivation Server의 재배 멤버 조회 응답에 포함된 멤버 한 명의 정보입니다.
 *
 * <p>이 응답에는 {@code cultivationId}가 포함되지 않습니다.
 * 어느 재배의 멤버인지는 멤버 목록을 요청할 때 사용한 URL의
 * {@code cultivationId}로 식별해야 합니다.</p>
 *
 * <p>{@code userId}는 OWNER를 찾은 뒤 다음 작업에 사용합니다.</p>
 *
 * <ul>
 *     <li>사용자 권한이 필요한 기존 sensor trend API 호출</li>
 *     <li>기존 일일 environment compliance API 호출</li>
 *     <li>현재 DailyFeedbackGeneratedEvent 계약의 userId 설정</li>
 * </ul>
 *
 * <p>{@code role}은 향후 새로운 역할이 추가돼도 응답 역직렬화가
 * 가능하도록 문자열 원본을 그대로 보존합니다. OWNER 선택은 이후
 * 서비스에서 정확히 {@code "OWNER"}와 비교하여 수행합니다.</p>
 *
 * @param memberId 재배 멤버 레코드 ID
 * @param userId 해당 재배 멤버의 사용자 ID
 * @param nickname 멤버에게 표시할 닉네임
 * @param role 해당 멤버의 재배 권한 문자열
 * @param joinedAt 멤버가 해당 재배에 참여한 시각
 */
public record CultivationMemberResponse(
        Long memberId,
        Long userId,
        String nickname,
        String role,
        LocalDateTime joinedAt
) {

    public CultivationMemberResponse {
        if (memberId == null || memberId <= 0) {
            throw new IllegalArgumentException("memberId는 필수이며 0보다 커야 합니다.");
        }

        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId는 필수이며 0보다 커야 합니다.");
        }

        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role은 null이거나 blank일 수 없습니다.");
        }

        if (joinedAt == null) {
            throw new IllegalArgumentException("joinedAt은 필수이며 null일 수 없습니다.");
        }
    }
}
