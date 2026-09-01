package site.yesaido.ai_server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import site.yesaido.ai_server.client.VisionClient;
import site.yesaido.ai_server.dto.client.vision.Result;
import site.yesaido.ai_server.dto.client.vision.Thresholds;
import site.yesaido.ai_server.dto.client.vision.VisionResponse;
import site.yesaido.ai_server.dto.cultivation.DailyCultivationPhotoResponse;
import site.yesaido.ai_server.entity.GrowthRecord;
import site.yesaido.ai_server.exception.VisionAnalysisException;
import site.yesaido.ai_server.exception.VisionAnalysisException.Reason;
import site.yesaido.ai_server.repository.GrowthRecordRepository;
import site.yesaido.ai_server.storage.image.PresignedImageDownloader;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class VisionRelayService {

    private static final String MUSHROOM_HEALTH_CHECK_V1 = "MUSHROOM_HEALTH_CHECK_V1";
    private static final String SUCCESS = "SUCCESS";
    private static final String NO_MUSHROOM_DETECTED = "NO_MUSHROOM_DETECTED";
    private static final String HEALTHY = "HEALTHY";
    private static final String DISEASE_SUSPECTED = "DISEASE_SUSPECTED";
    private static final String UNCERTAIN = "UNCERTAIN";

    private final VisionClient visionClient;
    private final PresignedImageDownloader presignedImageDownloader;
    private final GrowthRecordRepository growthRecordRepository;
    private final ObjectMapper objectMapper;

    public VisionResponse analyzeMushroomHealth(MultipartFile image) {
        return visionClient.analyzeMushroomHealth(image);
    }

    /**
     * 외부 HTTP 작업 중에는 DB 트랜잭션을 점유하지 않고,
     * 저장과 조회는 Repository의 독립된 트랜잭션으로 실행합니다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public GrowthRecord analyzeAndSave( DailyCultivationPhotoResponse photo) {
        validatePhoto(photo);

        Long cultivationId = photo.cultivationId();
        Long photoId = photo.photoId();

        Optional<GrowthRecord> existingRecord = growthRecordRepository.findByCultivationPhotoId(photoId);

        if (existingRecord.isPresent()) {
            return returnMatchingExistingOrThrow(photoId, cultivationId, existingRecord);
        }

        MultipartFile image = presignedImageDownloader.downloadAsMultipart(photo);
        VisionResponse response = visionClient.analyzeMushroomHealth(image);

        validateVisionResponse(photoId, response);

        JsonNode analysisData = toAnalysisData(photoId, response);

        GrowthRecord growthRecord = GrowthRecord.builder()
                .cultivationId(cultivationId)
                .cultivationPhotoId(photoId)
                .analysisData(analysisData)
                .build();

        try {
            return growthRecordRepository.saveAndFlush(growthRecord);
        } catch (DataIntegrityViolationException ignored) {
            return returnMatchingExistingOrThrow(photoId, cultivationId,
                    growthRecordRepository.findByCultivationPhotoId(photoId)
            );
        }
    }

    private void validatePhoto(DailyCultivationPhotoResponse photo) {
        Long photoId = photo == null ? null : photo.photoId();

        if (photo == null || photo.cultivationId() == null || photo.cultivationId() <= 0 || photoId == null || photoId <= 0) {
            throw new VisionAnalysisException(photoId, Reason.INVALID_PHOTO_CONTRACT);
        }
    }

    private void validateVisionResponse(Long photoId, VisionResponse response) {
        if (response == null || !MUSHROOM_HEALTH_CHECK_V1.equals(response.analysisType())
                || (!SUCCESS.equals(response.status()) && !NO_MUSHROOM_DETECTED.equals(response.status()))
                || isNullOrBlank(response.detectorModel()) || isNullOrBlank(response.healthModel())
                || response.thresholds() == null || response.results() == null || response.warnings() == null) {
            throw invalidResponse(photoId);
        }

        Thresholds thresholds = response.thresholds();

        if (!isValidProbability(thresholds.detection()) || !isValidProbability(thresholds.minDetectionConfidence())
                || !isValidProbability(thresholds.healthUncertain())) {
            throw invalidResponse(photoId);
        }

        for (String warning : response.warnings()) {
            if (warning == null) {
                throw invalidResponse(photoId);
            }
        }

        List<Result> results = response.results();

        if (SUCCESS.equals(response.status()) && results.isEmpty()) {
            throw invalidResponse(photoId);
        }

        if (NO_MUSHROOM_DETECTED.equals(response.status()) && !results.isEmpty()) {
            throw invalidResponse(photoId);
        }

        for (Result result : results) {
            validateResult(photoId, result);
        }
    }

    private void validateResult(Long photoId, Result result) {
        if (result == null || isNullOrBlank(result.species()) || isNullOrBlank(result.speciesCode())
                || result.speciesClassId() == null || result.speciesClassId() < 0
                || result.detectedCount() == null || result.detectedCount() <= 0
                || !isValidProbability(result.detectionConfidence())
                || !isValidProbability(result.detectionConfidenceMin())
                || result.detectionConfidenceMin() > result.detectionConfidence()
                || (!HEALTHY.equals(result.healthStatus()) && !DISEASE_SUSPECTED.equals(result.healthStatus())
                && !UNCERTAIN.equals(result.healthStatus()))
                || !isValidBoundingBox(result.bbox()) || !isValidBoundingBox(result.cropBbox())) {
            throw invalidResponse(photoId);
        }

        validateHealthProbabilities(photoId, result);
    }

    private void validateHealthProbabilities(Long photoId, Result result) {
        Double healthConfidence = result.healthConfidence();
        Double healthyProbability = result.healthyProbability();
        Double diseaseSuspectedProbability = result.diseaseSuspectedProbability();

        boolean allPresent = healthConfidence != null && healthyProbability != null && diseaseSuspectedProbability != null;
        boolean allNull = healthConfidence == null && healthyProbability == null && diseaseSuspectedProbability == null;
        boolean classifiedStatus = HEALTHY.equals(result.healthStatus()) || DISEASE_SUSPECTED.equals(result.healthStatus());

        if (classifiedStatus && !allPresent) {
            throw invalidResponse(photoId);
        }

        if (UNCERTAIN.equals(result.healthStatus()) && !(allNull || allPresent)) {
            throw invalidResponse(photoId);
        }

        if (allPresent && (!isValidProbability(healthConfidence) || !isValidProbability(healthyProbability)
                || !isValidProbability(diseaseSuspectedProbability))) {
            throw invalidResponse(photoId);
        }
    }

    private boolean isValidProbability(Double value) {
        return value != null && Double.isFinite(value) && value >= 0.0 && value <= 1.0;
    }

    private boolean isValidBoundingBox(List<Integer> boundingBox) {
        if (boundingBox == null || boundingBox.size() != 4) {
            return false;
        }

        for (Integer coordinate : boundingBox) {
            if (coordinate == null || coordinate < 0) {
                return false;
            }
        }

        return true;
    }

    private JsonNode toAnalysisData(Long photoId, VisionResponse response) {
        JsonNode analysisData;

        try {
            analysisData = objectMapper.valueToTree(response);
        } catch (IllegalArgumentException ignored) {
            throw new VisionAnalysisException(photoId, Reason.SERIALIZATION_FAILED);
        }

        if (analysisData == null || !analysisData.isObject()) {
            throw new VisionAnalysisException(photoId, Reason.SERIALIZATION_FAILED);
        }

        return analysisData;
    }

    private GrowthRecord returnMatchingExistingOrThrow(Long photoId, Long cultivationId, Optional<GrowthRecord> existingRecord) {
        return existingRecord.filter(existing -> Objects.equals(cultivationId, existing.getCultivationId()))
                .orElseThrow(() -> new VisionAnalysisException(photoId, Reason.IDEMPOTENCY_CONFLICT));
    }

    private boolean isNullOrBlank(String value) {
        return value == null || value.isBlank();
    }

    private VisionAnalysisException invalidResponse(Long photoId) {
        return new VisionAnalysisException(photoId, Reason.INVALID_RESPONSE_CONTRACT);
    }
}
