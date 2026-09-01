package site.yesaido.ai_server.dto.daily_feedback;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/**
 * 일일 피드백 Context에 포함할 Vision 분석 Snapshot입니다.
 *
 * <p>{@code hasVisionAnalysis=false}는 대상 날짜에 사진이 없어 Vision
 * 분석을 실행하지 않은 정상 상태만 의미합니다. 사진 다운로드나 Vision
 * 분석이 실패한 상태를 사진 없음으로 변환하여 표현하면 안 됩니다.</p>
 *
 * <p>사진이 있어 Vision 분석이 정상적으로 실행됐다면 버섯이 검출되지 않은
 * {@code NO_MUSHROOM_DETECTED}도 유효한 분석이므로
 * {@code hasVisionAnalysis=true}로 표현합니다.</p>
 *
 * <p>{@code HEALTHY}, {@code DISEASE_SUSPECTED}, {@code UNCERTAIN},
 * {@code NO_MUSHROOM_DETECTED} 상태를 이 DTO에서 해석하거나 변환하지 않고
 * {@code analysisData}에 Vision 원본 JSON 구조를 그대로 보존합니다.</p>
 *
 * <p>GrowthRecord JPA Entity, Presigned URL, OWNER 사용자 ID,
 * LLM 생성 결과와 외부 서비스 호출 책임은 포함하지 않습니다.</p>
 *
 * @param cultivationId Vision 분석 대상 경작지 ID
 * @param hasVisionAnalysis 사진이 존재해 Vision 분석이 정상 완료됐는지 여부
 * @param growthRecordId 저장된 Vision 분석 기록 ID 또는 사진이 없으면 null
 * @param cultivationPhotoId 분석한 Cultivation 사진 ID 또는 사진이 없으면 null
 * @param analysisData 저장된 Vision 원본 분석 JSON 또는 사진이 없으면 null
 * @param analyzedAt Vision 분석 결과가 저장된 시각 또는 사진이 없으면 null
 */
public record DailyVisionAnalysisSnapshot(
        Long cultivationId,
        boolean hasVisionAnalysis,
        Long growthRecordId,
        Long cultivationPhotoId,
        JsonNode analysisData,
        LocalDateTime analyzedAt
) {

    public DailyVisionAnalysisSnapshot {
        if (cultivationId == null || cultivationId <= 0) {
            throw new IllegalArgumentException("cultivationId는 null이 아니며 0보다 커야 합니다.");
        }

        if(!hasVisionAnalysis) {
            if(growthRecordId != null || cultivationPhotoId != null || analysisData != null || analyzedAt != null) {
                throw new IllegalArgumentException("hasVisionAnalysis가 false이면 모든 분석 필드는 null이여야 합니다.");
            }
        } else  {
            if( growthRecordId == null || growthRecordId <= 0) {
                throw new IllegalArgumentException("Vision분석이 있으면 growthRecordId는 0보다 커야 합니다.");
            }
            if (cultivationPhotoId == null || cultivationPhotoId <= 0) {
                throw new IllegalArgumentException("Vision 분석이 있으면 cultivationPhotoId는 0보다 커야 합니다.");
            }

            if (analysisData == null) {
                throw new IllegalArgumentException("Vision 분석이 있으면 analysisData는 null일 수 없습니다.");
            }

            if (!analysisData.isObject()) {
                throw new IllegalArgumentException("analysisData는 JSON object여야 합니다.");
            }
            if (analyzedAt == null) {
                throw new IllegalArgumentException("Vision 분석이 있으면 analyzedAt은 null일 수 없습니다.");
            }

            analysisData = analysisData.deepCopy();

        }
    }

    /**
     * 대상 날짜에 사진이 없는 정상 상태를 생성합니다.
     *
     * @param cultivationId 사진을 확인한 경작지 ID
     * @return Vision 분석 필드가 모두 null인 Snapshot
     */
    public static DailyVisionAnalysisSnapshot withoutPhoto( Long cultivationId) {
        return new DailyVisionAnalysisSnapshot(cultivationId, false, null, null, null, null);
    }

    /**
     * 사진에 대한 Vision 분석과 저장이 정상 완료된 상태를 생성합니다.
     *
     * <p>분석 상태를 해석하지 않으므로 {@code NO_MUSHROOM_DETECTED} 결과도
     * 다른 정상 분석 결과와 동일하게 이 팩토리로 생성합니다.</p>
     *
     * @param cultivationId 분석 대상 경작지 ID
     * @param growthRecordId 저장된 GrowthRecord ID
     * @param cultivationPhotoId 분석한 Cultivation 사진 ID
     * @param analysisData Vision 원본 분석 JSON
     * @param analyzedAt 분석 결과 저장 시각
     * @return Vision 분석이 존재하는 Snapshot
     */
    public static DailyVisionAnalysisSnapshot analyzed(Long cultivationId, Long growthRecordId,
                                                       Long cultivationPhotoId, JsonNode analysisData, LocalDateTime analyzedAt) {
        return new DailyVisionAnalysisSnapshot(cultivationId, true, growthRecordId, cultivationPhotoId, analysisData, analyzedAt);
    }

    /**
     * 내부에 보관된 JSON이 외부 변경으로 훼손되지 않도록 복사본을 반환합니다.
     *
     * @return Vision 분석 JSON의 방어적 복사본 또는 사진이 없으면 null
     */
    @Override
    public JsonNode analysisData() {
        return analysisData == null ? null : analysisData.deepCopy();
    }
}
