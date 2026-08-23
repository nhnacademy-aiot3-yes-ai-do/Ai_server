package site.yesaido.ai_server.dto.cultivation;

public record ChangePhase(
        boolean changeP, // 재배기 -> 수확기 전환 가능 여부
        String message // 사유(프론트에 표시해줄 왜 전환 가능 불가능 여부 설명)
) {
}
