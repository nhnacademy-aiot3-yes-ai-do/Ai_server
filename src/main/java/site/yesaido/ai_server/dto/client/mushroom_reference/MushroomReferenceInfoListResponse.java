package site.yesaido.ai_server.dto.client.mushroom_reference;

import java.util.List;

public record MushroomReferenceInfoListResponse( // 버섯 기준 정보 및 기준 임계값
        List<MushroomReferenceInfoResponse> mushroomReferenceInfoResponses
) {
}
