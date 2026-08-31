package site.yesaido.ai_server.dto.client.cultivation;

import java.util.List;

public record CultivationSummaryListResponse(
        List<CultivationSummaryResponse> cultivationSummaryResponses
) {
}
