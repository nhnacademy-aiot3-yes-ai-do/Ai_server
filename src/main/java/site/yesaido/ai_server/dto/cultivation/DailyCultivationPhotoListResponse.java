package site.yesaido.ai_server.dto.cultivation;

import java.time.LocalDate;
import java.util.List;

/**
 * 대상 날짜에 Vision 분석할 수 있는 경작 사진 목록입니다.
 *
 * @param targetDate 사진 조회 기준 날짜
 * @param photos 해당 날짜에 사진이 등록된 활성 경작지의 사진 목록
 */
public record DailyCultivationPhotoListResponse(
        LocalDate targetDate,
        List<DailyCultivationPhotoResponse> photos
) {
}
