package site.yesaido.ai_server.dto.client.cultivation;

import java.time.LocalDateTime;

/**
 * Cultivation Server의 재배 상세 응답 전체를 받는
 * 일일 피드백 전용 Client DTO입니다.
 *
 * <p>기존 {@link CultivationDetailResponse}는 인사이트와 센서 임계값
 * 검증 기능에서 사용하고 있으므로 기존 계약에 영향을 주지 않도록
 * 이 DTO를 별도로 분리합니다.</p>
 *
 * <p>{@code name}은 일일 피드백 표시와 피드백 생성 완료 이벤트에서
 * 사용합니다. {@code mushroomId}는 버섯 참조정보에서 버섯 이름과
 * 수확 정책을 찾기 위한 키입니다.</p>
 *
 * <p>{@code status}와 {@code mode}는 현재 재배 상태와 수확 모드 여부를
 * 판단하는 값이며, {@code startedAt}은 재배 경과일을 계산하는
 * 기준 시각입니다. 실제 경과일 계산은 이 DTO에서 수행하지 않습니다.</p>
 *
 * <p>{@code myRole}은 일일 배치가 OWNER 사용자 ID로 호출하는 정상
 * 처리에서는 {@code OWNER}일 예정입니다. 다만 Cultivation Server의
 * ADMIN 우회 응답에서는 null일 수 있으므로 DTO에서 null을 허용합니다.</p>
 *
 * <p>{@code status}, {@code mode}, {@code myRole}은 서버에 미래 enum 값이
 * 추가되더라도 역직렬화 자체를 막지 않도록 문자열 원본으로 보존하며,
 * DTO에서 허용값을 제한하거나 값을 변경하지 않습니다.</p>
 *
 * <p>서버 상세 응답에는 버섯 이름이 아니라 {@code mushroomId}만
 * 포함됩니다. 버섯 이름은 이후 버섯 참조정보와 결합해야 합니다.</p>
 *
 * @param cultivationId 경작지 ID
 * @param name 피드백 표시 및 완료 이벤트에 사용할 경작지 이름
 * @param mushroomId 버섯 참조정보를 조회할 버섯 ID
 * @param status 서버가 반환한 현재 재배 상태 문자열
 * @param mode 서버가 반환한 현재 재배 모드 문자열
 * @param myRole 요청 사용자의 재배 멤버 역할 또는 ADMIN 우회 시 null
 * @param startedAt 재배 시작 시각
 * @param finishedAt 재배 종료 시각 또는 활성 재배인 경우 null
 * @param createdAt 재배 생성 시각
 * @param updatedAt 재배 마지막 수정 시각
 */
public record DailyCultivationDetailResponse(
        Long cultivationId,
        String name,
        Long mushroomId,
        String status,
        String mode,
        String myRole,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public DailyCultivationDetailResponse {
        if (cultivationId == null || cultivationId <= 0) {
            throw new IllegalArgumentException("cultivationId는 null이 아니며 0보다 커야 합니다.");
        }

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name은 null 또는 공백일 수 없습니다.");
        }

        if (mushroomId == null || mushroomId <= 0) {
            throw new IllegalArgumentException("mushroomId는 null이 아니며 0보다 커야 합니다.");
        }

        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status는 null 또는 공백일 수 없습니다.");
        }

        if (mode == null || mode.isBlank()) {
            throw new IllegalArgumentException("mode는 null 또는 공백일 수 없습니다.");
        }

        if (myRole != null && myRole.isBlank()) {
            throw new IllegalArgumentException("myRole은 null이거나 공백이 아닌 문자열이어야 합니다.");
        }

        if (startedAt == null) {
            throw new IllegalArgumentException("startedAt은 null일 수 없습니다.");
        }

        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt은 null일 수 없습니다.");
        }

        if (updatedAt == null) {
            throw new IllegalArgumentException("updatedAt은 null일 수 없습니다.");
        }
    }
}
