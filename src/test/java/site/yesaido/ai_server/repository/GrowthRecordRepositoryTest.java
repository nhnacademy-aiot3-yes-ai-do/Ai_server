package site.yesaido.ai_server.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import site.yesaido.ai_server.entity.GrowthRecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
class GrowthRecordRepositoryTest {

    private static final Long ROUND_TRIP_PHOTO_ID = 9_000_000_000_000_001L;
    private static final Long DUPLICATE_PHOTO_ID = 9_000_000_000_000_002L;

    @Autowired
    private GrowthRecordRepository growthRecordRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("GrowthRecord를 저장하고 cultivationPhotoId로 JSONB 데이터를 재조회한다")
    void savesAndFindsGrowthRecordByCultivationPhotoId() throws JsonProcessingException {
        JsonNode analysisData = objectMapper.readTree("""
                    {
                      "analysisType": "MUSHROOM_HEALTH_CHECK_V1",
                      "status": "SUCCESS",
                      "detectorModel": "mushroom-detector-v1",
                      "healthModel": "mushroom-health-v1",
                      "thresholds": {
                        "detection": 0.25,
                        "minDetectionConfidence": 0.5,
                        "healthUncertain": 0.7
                      },
                      "results": [
                        {
                          "species": "느타리",
                          "speciesCode": "OYSTER",
                          "speciesClassId": 0,
                          "detectedCount": 2,
                          "detectionConfidence": 0.97,
                          "detectionConfidenceMin": 0.45,
                          "healthStatus": "UNCERTAIN",
                          "healthConfidence": null,
                          "healthyProbability": null,
                          "diseaseSuspectedProbability": null,
                          "bbox": [100, 120, 420, 560],
                          "cropBbox": [80, 100, 440, 580]
                        }
                      ],
                      "warnings": [
                        "최저 탐지 신뢰도가 기준보다 낮아 건강 분류를 생략했습니다."
                      ]
                    }
                    """);

        GrowthRecord growthRecord = GrowthRecord.builder()
                .cultivationId(9_000_000_000_000_101L)
                .cultivationPhotoId(ROUND_TRIP_PHOTO_ID)
                .analysisData(analysisData)
                .build();

        GrowthRecord savedRecord = growthRecordRepository.saveAndFlush(growthRecord);

        assertThat(savedRecord.getId()).isNotNull();

        Long savedRecordId = savedRecord.getId();

        entityManager.clear();

        GrowthRecord foundRecord = growthRecordRepository
                .findByCultivationPhotoId(ROUND_TRIP_PHOTO_ID)
                .orElseThrow();

        assertThat(foundRecord).isNotSameAs(savedRecord);
        assertThat(foundRecord.getCultivationId())
                .isEqualTo(9_000_000_000_000_101L);
        assertThat(foundRecord.getCultivationPhotoId())
                .isEqualTo(ROUND_TRIP_PHOTO_ID);
        assertThat(foundRecord.getAnalysisData())
                .isEqualTo(analysisData);
        assertThat(foundRecord.getAnalysisData()
                .path("results")
                .isArray())
                .isTrue();
        assertThat(foundRecord.getAnalysisData()
                .path("results")
                .path(0)
                .path("healthStatus")
                .asText())
                .isEqualTo("UNCERTAIN");
        assertThat(foundRecord.getAnalysisData()
                .path("results")
                .path(0)
                .path("healthConfidence")
                .isNull())
                .isTrue();
        assertThat(foundRecord.getAnalyzedAt()).isNotNull();

        String analysisDataType = jdbcTemplate.queryForObject(
                """
                SELECT pg_typeof(analysis_data)::text
                FROM growth_record
                WHERE id = ?
                """,
                String.class,
                savedRecordId
        );

        assertThat(analysisDataType).isEqualTo("jsonb");
    }

    @Test
    @DisplayName("동일한 cultivationPhotoId를 두 번 저장하면 UNIQUE 제약조건을 위반한다")
    void rejectsDuplicateCultivationPhotoId() {
        GrowthRecord firstRecord = GrowthRecord.builder()
                .cultivationId(9_000_000_000_000_201L)
                .cultivationPhotoId(DUPLICATE_PHOTO_ID)
                .analysisData(objectMapper.createObjectNode().put("status", "FIRST"))
                .build();

        GrowthRecord secondRecord = GrowthRecord.builder()
                .cultivationId(9_000_000_000_000_202L)
                .cultivationPhotoId(DUPLICATE_PHOTO_ID)
                .analysisData(objectMapper.createObjectNode().put("status", "SECOND"))
                .build();

        growthRecordRepository.saveAndFlush(firstRecord);

        assertThatThrownBy(() -> growthRecordRepository.saveAndFlush(secondRecord))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
