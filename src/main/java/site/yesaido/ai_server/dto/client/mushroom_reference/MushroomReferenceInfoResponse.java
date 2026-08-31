package site.yesaido.ai_server.dto.client.mushroom_reference;

import java.util.List;

public record MushroomReferenceInfoResponse( // 버섯 기준 정보 및 기준 임계값
        long id,
        String mushroomNameKo,
        String mushroomNameEn,
        String mushroomScientificName,
        List<MushroomReferenceThresholdInfoResponse> thresholdInfoResponses
) {
}
