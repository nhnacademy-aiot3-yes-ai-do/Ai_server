package site.yesaido.ai_server.dto.cultivation;

import java.time.LocalDateTime;

public record CultivationDetailResponse(
        Long cultivationId,
        Long mushroomId, // 인사이트 검색에 사용할 버섯 ID
        String status, // 인사이트에 한줄 요약에 사용할거라 enum 가져오는 대신 String으로 받음
        String mode,// 인사이트에 한줄 요약에 사용할거라 enum 가져오는 대신 String으로 받음
        LocalDateTime startedAt
) {
}
