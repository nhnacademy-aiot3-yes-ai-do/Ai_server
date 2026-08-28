package site.yesaido.ai_server.dto.cultivation;

import java.time.OffsetDateTime;

/**
 * 지정한 날짜에 Vision 분석 대상으로 조회된 경작 사진 정보입니다.
 *
 * @param cultivationId 사진이 등록된 경작지 ID
 * @param photoId 중복 분석 방지와 분석 결과 저장에 사용하는 사진 ID
 * @param presignedUrl AI가 사진을 내려받는 임시 서명 URL
 * @param expiresAt Presigned URL의 절대 만료 시각
 */
public record DailyCultivationPhotoResponse(
        Long cultivationId,
        Long photoId,
        String presignedUrl,
        OffsetDateTime expiresAt
) {
}
