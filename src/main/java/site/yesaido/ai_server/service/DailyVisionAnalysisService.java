package site.yesaido.ai_server.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.yesaido.ai_server.client.CultivationClient;
import site.yesaido.ai_server.dto.cultivation.DailyCultivationPhotoListResponse;
import site.yesaido.ai_server.dto.cultivation.DailyCultivationPhotoResponse;
import site.yesaido.ai_server.entity.GrowthRecord;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * 일일 피드백에 사용할 날짜별 사진을 조회하고 개별 Vision 분석을 실행합니다.
 *
 * <p>날짜별 사진 목록은 일일 피드백 배치마다 한 번만 조회해
 * 경작지 ID별 Map으로 보관합니다. 현재 Cultivation Server 계약은
 * 하나의 날짜에 경작지별 최대 한 장의 사진을 반환합니다.</p>
 *
 * <p>사진이 없는 것은 오류가 아니라 Vision 분석 없이 일일 피드백을
 * 생성할 수 있는 정상 상태입니다. 사진이 있는 경작지만 기존
 * {@link VisionRelayService}의 다운로드, 응답 검증 및 멱등 저장 흐름을
 * 실행하며, 반환된 {@link GrowthRecord}는 이후
 * {@code hasVisionAnalysis=true}의 근거로 사용합니다.</p>
 *
 * <p>Vision의 {@code NO_MUSHROOM_DETECTED}, {@code UNCERTAIN},
 * {@code HEALTHY}, {@code DISEASE_SUSPECTED} 상태를 이 서비스에서
 * 다른 상태로 변환하거나 분석 JSON을 변경하지 않습니다.</p>
 *
 * <p>Presigned URL은 서명정보가 포함된 민감한 값이므로 예외 메시지나
 * 로그에 노출하지 않습니다. URL 만료 여부는 기존
 * {@code PresignedImageDownloader}가 HTTP 요청 전에 검증합니다.</p>
 *
 * <p>한 경작지의 Vision 실패를 다른 경작지와 격리하는 책임은
 * 이후 최상위 일일 피드백 오케스트레이터에 있습니다. 이 서비스는
 * Vision 실패를 사진 없음이나 정상 건강 상태로 숨기지 않습니다.</p>
 */
@Service
@RequiredArgsConstructor
public class DailyVisionAnalysisService {

    private final CultivationClient cultivationClient;
    private final VisionRelayService visionRelayService;

    /**
     * 대상 날짜의 사진 목록을 한 번 조회해 경작지 ID 오름차순 Map으로 반환합니다.
     *
     * @param targetDate 사진을 조회할 대상 날짜
     * @return 경작지 ID를 키로 갖는 정렬되고 수정 불가능한 사진 Map
     * @throws IllegalArgumentException targetDate가 null인 경우
     * @throws IllegalStateException 외부 사진 목록 응답 계약이 잘못된 경우
     */
    public Map<Long, DailyCultivationPhotoResponse> fetchPhotosByCultivationId(LocalDate targetDate) {
        validateTargetDate(targetDate);

        DailyCultivationPhotoListResponse response =
                cultivationClient.getDailyCultivationPhotos(targetDate);

        validatePhotoListResponse(targetDate, response);

        return indexPhotosByCultivationId(targetDate, response);
    }

    private static void validateTargetDate(LocalDate targetDate) {
        if (targetDate == null) {
            throw new IllegalArgumentException("targetDate는 null일 수 없습니다.");
        }
    }

    private static void validatePhotoListResponse(
            LocalDate targetDate,
            DailyCultivationPhotoListResponse response
    ) {
        if (response == null) {
            throw new IllegalStateException("일일 경작 사진 목록 응답이 null입니다: targetDate=%s".formatted(targetDate));
        }

        if (response.targetDate() == null || !targetDate.equals(response.targetDate())) {
            throw new IllegalStateException("일일 경작 사진 응답 날짜가 요청과 일치하지 않습니다: requestedDate=%s, responseDate=%s"
                    .formatted(targetDate, response.targetDate()));
        }

        if (response.photos() == null) {
            throw new IllegalStateException("일일 경작 사진 목록이 null입니다: targetDate=%s".formatted(targetDate));
        }
    }

    private static Map<Long, DailyCultivationPhotoResponse> indexPhotosByCultivationId(
            LocalDate targetDate,
            DailyCultivationPhotoListResponse response
    ) {
        TreeMap<Long, DailyCultivationPhotoResponse> photosByCultivationId = new TreeMap<>();
        Set<Long> photoIds = new HashSet<>();

        for (DailyCultivationPhotoResponse photo : response.photos()) {
            validatePhoto(targetDate, photo);

            if (photosByCultivationId.containsKey(photo.cultivationId())) {
                throw new IllegalStateException("같은 경작지의 사진이 중복되었습니다: targetDate=%s, cultivationId=%s"
                        .formatted(targetDate, photo.cultivationId()));
            }

            if (!photoIds.add(photo.photoId())) {
                throw new IllegalStateException("같은 photoId가 중복되었습니다: targetDate=%s, photoId=%s"
                        .formatted(targetDate, photo.photoId()));
            }

            photosByCultivationId.put(photo.cultivationId(), photo);
        }

        return Collections.unmodifiableMap(photosByCultivationId);
    }

    private static void validatePhoto(
            LocalDate targetDate,
            DailyCultivationPhotoResponse photo
    ) {
        if (photo == null) {
            throw new IllegalStateException("일일 경작 사진 목록에 null 요소가 있습니다: targetDate=%s".formatted(targetDate));
        }

        if (photo.cultivationId() == null || photo.cultivationId() <= 0) {
            throw new IllegalStateException("사진의 cultivationId가 유효하지 않습니다: targetDate=%s, cultivationId=%s"
                    .formatted(targetDate, photo.cultivationId()));
        }

        if (photo.photoId() == null || photo.photoId() <= 0) {
            throw new IllegalStateException("사진의 photoId가 유효하지 않습니다: targetDate=%s, cultivationId=%s, photoId=%s"
                    .formatted(targetDate, photo.cultivationId(), photo.photoId()));
        }

        if (photo.presignedUrl() == null || photo.presignedUrl().isBlank()) {
            throw new IllegalStateException("사진의 Presigned URL이 유효하지 않습니다: targetDate=%s, cultivationId=%s, photoId=%s"
                    .formatted(targetDate, photo.cultivationId(), photo.photoId()));
        }

        if (photo.expiresAt() == null) {
            throw new IllegalStateException("사진의 URL 만료 시각이 null입니다: targetDate=%s, cultivationId=%s, photoId=%s"
                    .formatted(targetDate, photo.cultivationId(), photo.photoId()));
        }
    }

    /**
     * 경작지의 사진이 존재할 때만 기존 Vision 분석 및 멱등 저장을 실행합니다.
     *
     * @param photosByCultivationId 배치 시작 시 조회한 경작지별 사진 Map
     * @param cultivationId 분석할 경작지 ID
     * @return 사진이 없으면 빈 Optional, 있으면 검증된 GrowthRecord
     * @throws IllegalArgumentException 입력 Map 또는 cultivationId가 유효하지 않은 경우
     * @throws IllegalStateException Map 계약이나 저장 결과의 식별정보가 잘못된 경우
     */
    public Optional<GrowthRecord> analyzeIfPresent(
            Map<Long, DailyCultivationPhotoResponse> photosByCultivationId,
            Long cultivationId
    ) {
        if (photosByCultivationId == null) {
            throw new IllegalArgumentException("photosByCultivationId는 null일 수 없습니다.");
        }

        if (cultivationId == null || cultivationId <= 0) {
            throw new IllegalArgumentException("cultivationId는 null이 아니며 0보다 커야 합니다.");
        }

        if (!photosByCultivationId.containsKey(cultivationId)) {
            return Optional.empty();
        }

        DailyCultivationPhotoResponse photo = photosByCultivationId.get(cultivationId);

        if (photo == null) {
            throw new IllegalStateException("경작지별 사진 Map에 null 값이 있습니다: cultivationId=%s".formatted(cultivationId));
        }

        if (!Objects.equals(cultivationId, photo.cultivationId())) {
            throw new IllegalStateException("사진 Map의 key와 사진 cultivationId가 일치하지 않습니다: mapCultivationId=%s, photoCultivationId=%s"
                    .formatted(cultivationId, photo.cultivationId()));
        }

        GrowthRecord growthRecord = visionRelayService.analyzeAndSave(photo);

        if (growthRecord == null) {
            throw new IllegalStateException("Vision 분석 저장 결과가 null입니다: cultivationId=%s, photoId=%s"
                    .formatted(cultivationId, photo.photoId()));
        }

        if (!Objects.equals(cultivationId, growthRecord.getCultivationId())) {
            throw new IllegalStateException("Vision 분석 저장 결과의 cultivationId가 요청과 일치하지 않습니다: requestedCultivationId=%s, resultCultivationId=%s"
                    .formatted(cultivationId, growthRecord.getCultivationId()));
        }

        if (!Objects.equals(photo.photoId(), growthRecord.getCultivationPhotoId())) {
            throw new IllegalStateException("Vision 분석 저장 결과의 photoId가 요청 사진과 일치하지 않습니다: requestedPhotoId=%s, resultPhotoId=%s"
                    .formatted(photo.photoId(), growthRecord.getCultivationPhotoId()));
        }

        return Optional.of(growthRecord);
    }
}
