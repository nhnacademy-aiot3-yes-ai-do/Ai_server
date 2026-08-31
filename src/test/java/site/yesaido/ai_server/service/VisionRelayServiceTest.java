package site.yesaido.ai_server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import site.yesaido.ai_server.client.VisionClient;
import site.yesaido.ai_server.dto.client.vision.Result;
import site.yesaido.ai_server.dto.client.vision.Thresholds;
import site.yesaido.ai_server.dto.client.vision.VisionResponse;
import site.yesaido.ai_server.dto.cultivation.DailyCultivationPhotoResponse;
import site.yesaido.ai_server.entity.GrowthRecord;
import site.yesaido.ai_server.exception.ImageDownloadException;
import site.yesaido.ai_server.exception.VisionAnalysisException;
import site.yesaido.ai_server.repository.GrowthRecordRepository;
import site.yesaido.ai_server.storage.image.PresignedImageDownloader;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VisionRelayServiceTest {

    private static final Long CULTIVATION_ID = 7L;
    private static final Long PHOTO_ID = 42L;

    private static final String ANALYSIS_TYPE = "MUSHROOM_HEALTH_CHECK_V1";
    private static final String SUCCESS = "SUCCESS";
    private static final String NO_MUSHROOM_DETECTED = "NO_MUSHROOM_DETECTED";
    private static final String HEALTHY = "HEALTHY";
    private static final String DISEASE_SUSPECTED = "DISEASE_SUSPECTED";
    private static final String UNCERTAIN = "UNCERTAIN";
    private static final String USER_MESSAGE = "Vision 분석 결과를 처리하지 못했습니다.";
    private static final String PRESIGNED_URL = "https://storage.test/photos/42.jpg?X-Amz-Signature=fake-signature";
    private static final String SIGNATURE_PARAMETER = "X-Amz-Signature";
    private static final String FAKE_SIGNATURE = "fake-signature";
    private static final String EXTERNAL_SERIALIZATION_DETAIL = "external-serialization-secret";
    private static final String EXTERNAL_RESPONSE_DETAIL = "external-vision-response-secret";
    private static final String DATABASE_FAILURE_DETAIL = "external-database-constraint-detail";
    private static final List<Integer> VALID_BBOX = List.of(10, 20, 110, 220);
    private static final List<Integer> VALID_CROP_BBOX = List.of(5, 15, 115, 225);

    @Mock
    private VisionClient visionClient;

    @Mock
    private PresignedImageDownloader presignedImageDownloader;

    @Mock
    private GrowthRecordRepository growthRecordRepository;

    private ObjectMapper objectMapper;
    private MultipartFile image;
    private VisionRelayService visionRelayService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        image = new MockMultipartFile(
                "image",
                "photo-42.jpg",
                "image/jpeg",
                new byte[]{1, 2, 3}
        );

        visionRelayService = new VisionRelayService(
                visionClient,
                presignedImageDownloader,
                growthRecordRepository,
                objectMapper
        );
    }

    @Test
    @DisplayName("기존 MultipartFile 분석 요청을 VisionClient에 그대로 위임한다")
    void delegateMultipartAnalysisToVisionClient() {
        // Given: VisionClient가 반환할 응답과 동일한 이미지 객체를 준비한다.
        VisionResponse response = validSuccessResponse();

        when(visionClient.analyzeMushroomHealth(image))
                .thenReturn(response);

        // When: 기존 중계 메서드로 분석을 요청한다.
        VisionResponse actual =
                visionRelayService.analyzeMushroomHealth(image);

        // Then: 같은 이미지가 전달되고 같은 응답 객체가 반환된다.
        assertThat(actual)
                .isSameAs(response);

        verify(visionClient)
                .analyzeMushroomHealth(image);

        verifyNoInteractions(
                presignedImageDownloader,
                growthRecordRepository
        );
    }

    @Test
    @DisplayName("정상 SUCCESS 응답 전체를 JSON으로 변환해 GrowthRecord에 저장한다")
    void saveSuccessfulVisionAnalysis() {
        // Given: 건강 분류가 생략된 UNCERTAIN 응답을 준비한다.
        DailyCultivationPhotoResponse photo = validPhoto();
        VisionResponse response =
                successResponse(validUncertainResult());

        stubSuccessfulSave(photo, response);

        // When: 사진을 다운로드하고 Vision 분석 결과를 저장한다.
        GrowthRecord actual =
                visionRelayService.analyzeAndSave(photo);

        // Then: 조회, 다운로드, Vision 호출, 저장 순서가 유지된다.
        ArgumentCaptor<GrowthRecord> growthRecordCaptor =
                ArgumentCaptor.forClass(GrowthRecord.class);

        InOrder orderedCalls = inOrder(
                growthRecordRepository,
                presignedImageDownloader,
                visionClient
        );

        orderedCalls.verify(growthRecordRepository)
                .findByCultivationPhotoId(PHOTO_ID);

        orderedCalls.verify(presignedImageDownloader)
                .downloadAsMultipart(photo);

        orderedCalls.verify(visionClient)
                .analyzeMushroomHealth(image);

        orderedCalls.verify(growthRecordRepository)
                .saveAndFlush(growthRecordCaptor.capture());

        orderedCalls.verifyNoMoreInteractions();

        GrowthRecord savedRecord =
                growthRecordCaptor.getValue();

        assertThat(actual)
                .isSameAs(savedRecord);

        assertThat(savedRecord.getCultivationId())
                .isEqualTo(CULTIVATION_ID);

        assertThat(savedRecord.getCultivationPhotoId())
                .isEqualTo(PHOTO_ID);

        JsonNode expectedAnalysisData =
                objectMapper.valueToTree(response);

        JsonNode actualAnalysisData =
                savedRecord.getAnalysisData();

        assertThat(actualAnalysisData)
                .isEqualTo(expectedAnalysisData);

        JsonNode storedResult =
                actualAnalysisData.path("results").path(0);

        assertThat(storedResult.path("healthStatus").asText())
                .isEqualTo(UNCERTAIN);

        assertThat(storedResult.has("healthConfidence"))
                .isTrue();

        assertThat(storedResult.path("healthConfidence").isNull())
                .isTrue();

        assertThat(storedResult.has("healthyProbability"))
                .isTrue();

        assertThat(storedResult.path("healthyProbability").isNull())
                .isTrue();

        assertThat(storedResult.has("diseaseSuspectedProbability"))
                .isTrue();

        assertThat(
                storedResult
                        .path("diseaseSuspectedProbability")
                        .isNull()
        ).isTrue();
    }

    @Test
    @DisplayName("버섯 미탐지 응답의 상태와 빈 results를 그대로 저장한다")
    void saveNoMushroomDetectedResponse() {
        // Given: 버섯이 탐지되지 않은 정상 응답을 준비한다.
        DailyCultivationPhotoResponse photo = validPhoto();
        VisionResponse response = noMushroomResponse();

        stubSuccessfulSave(photo, response);

        // When: 분석 결과를 저장한다.
        GrowthRecord savedRecord =
                visionRelayService.analyzeAndSave(photo);

        // Then: 상태와 빈 결과 배열이 가공되지 않고 보존된다.
        JsonNode analysisData =
                savedRecord.getAnalysisData();

        assertThat(analysisData.path("status").asText())
                .isEqualTo(NO_MUSHROOM_DETECTED);

        JsonNode storedResults =
                analysisData.path("results");

        assertThat(storedResults.isArray())
                .isTrue();

        assertThat(storedResults.size())
                .isZero();

        assertThat(analysisData)
                .isEqualTo(objectMapper.valueToTree(response));

        verify(growthRecordRepository)
                .saveAndFlush(any(GrowthRecord.class));
    }

    @Test
    @DisplayName("같은 경작지의 사진이 이미 분석됐다면 기존 레코드를 그대로 반환한다")
    void returnExistingRecordWithoutExternalCalls() {
        // Given: 같은 경작지와 사진 ID를 가진 기존 레코드가 존재한다.
        DailyCultivationPhotoResponse photo = validPhoto();

        GrowthRecord existingRecord =
                existingGrowthRecord(
                        CULTIVATION_ID,
                        PHOTO_ID
                );

        when(
                growthRecordRepository
                        .findByCultivationPhotoId(PHOTO_ID)
        ).thenReturn(Optional.of(existingRecord));

        // When: 같은 사진에 대한 분석을 다시 요청한다.
        GrowthRecord actual =
                visionRelayService.analyzeAndSave(photo);

        // Then: 기존 객체를 반환하고 외부 호출과 저장은 수행하지 않는다.
        assertThat(actual)
                .isSameAs(existingRecord);

        verify(growthRecordRepository)
                .findByCultivationPhotoId(PHOTO_ID);

        verify(growthRecordRepository, never())
                .saveAndFlush(any(GrowthRecord.class));

        verifyNoInteractions(
                presignedImageDownloader,
                visionClient
        );
    }

    @Test
    @DisplayName("같은 사진 ID가 다른 경작지에 저장되어 있으면 멱등성 충돌로 처리한다")
    void rejectExistingRecordOwnedByDifferentCultivation() {
        // Given: 같은 사진 ID가 다른 경작지의 레코드로 저장되어 있다.
        DailyCultivationPhotoResponse photo = validPhoto();

        GrowthRecord differentCultivationRecord =
                existingGrowthRecord(
                        999L,
                        PHOTO_ID
                );

        when(
                growthRecordRepository
                        .findByCultivationPhotoId(PHOTO_ID)
        ).thenReturn(
                Optional.of(differentCultivationRecord)
        );

        // When: 현재 경작지의 사진으로 분석을 요청한다.
        VisionAnalysisException exception =
                catchThrowableOfType(
                        VisionAnalysisException.class,
                        () -> visionRelayService.analyzeAndSave(photo)
                );

        // Then: 안전한 멱등성 충돌 예외만 반환하고 이후 작업은 중단한다.
        assertVisionFailure(
                exception,
                "IDEMPOTENCY_CONFLICT"
        );

        verify(growthRecordRepository)
                .findByCultivationPhotoId(PHOTO_ID);

        verify(growthRecordRepository, never())
                .saveAndFlush(any(GrowthRecord.class));

        verifyNoInteractions(
                presignedImageDownloader,
                visionClient
        );
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("invalidPhotos")
    @DisplayName("잘못된 사진 DTO 계약은 외부 작업 전에 거부한다")
    void rejectInvalidPhotoContract(
            String description,
            DailyCultivationPhotoResponse invalidPhoto
    ) {
        // Given: ID 계약을 위반한 사진 DTO가 주어진다.

        // When: 분석과 저장을 요청한다.
        VisionAnalysisException exception =
                catchThrowableOfType(
                        VisionAnalysisException.class,
                        () -> visionRelayService.analyzeAndSave(
                                invalidPhoto
                        )
                );

        // Then: 사진 계약 오류로 처리하고 어떤 협력 객체도 호출하지 않는다.
        assertThat(description)
                .isNotBlank();

        assertVisionFailure(
                exception,
                "INVALID_PHOTO_CONTRACT"
        );

        verifyNoInteractions(
                growthRecordRepository,
                presignedImageDownloader,
                visionClient
        );
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("invalidTopLevelResponses")
    @DisplayName("잘못된 Vision 최상위 응답 계약을 거부한다")
    void rejectInvalidTopLevelVisionResponse(
            String description,
            VisionResponse invalidResponse
    ) {
        // Given: 최상위 계약을 위반한 Vision 응답이 주어진다.

        // When: 해당 응답을 분석 결과로 처리한다.

        // Then: 저장하지 않고 응답 계약 오류로 처리한다.
        assertThat(description)
                .isNotBlank();

        assertInvalidVisionResponse(invalidResponse);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("invalidThresholds")
    @DisplayName("null이거나 유효 범위를 벗어난 Thresholds 숫자를 거부한다")
    void rejectInvalidThresholdNumbers(
            String description,
            Thresholds invalidThresholds
    ) {
        // Given: 임계값 계약을 위반한 Thresholds가 주어진다.
        VisionResponse response =
                new VisionResponse(
                        ANALYSIS_TYPE,
                        SUCCESS,
                        "detector-v1",
                        "health-v1",
                        invalidThresholds,
                        List.of(validUncertainResult()),
                        List.of()
                );

        // When: 해당 응답을 저장하려고 한다.

        // Then: NPE가 아닌 응답 계약 오류로 처리한다.
        assertThat(description)
                .isNotBlank();

        assertInvalidVisionResponse(response);
    }





    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("validHealthProbabilityCombinations")
    @DisplayName("허용되는 건강 상태와 확률 조합을 그대로 저장한다")
    void acceptValidHealthProbabilityCombination(
            String description,
            Result validResult
    ) {
        // Given: 건강 상태와 확률의 조합이 계약을 충족한다.
        DailyCultivationPhotoResponse photo = validPhoto();
        VisionResponse response =
                successResponse(validResult);

        stubSuccessfulSave(photo, response);

        // When: 분석 결과를 저장한다.
        GrowthRecord savedRecord =
                visionRelayService.analyzeAndSave(photo);

        // Then: 원래 건강 상태와 확률 구조가 그대로 보존된다.
        assertThat(description)
                .isNotBlank();

        assertThat(savedRecord.getAnalysisData())
                .isEqualTo(objectMapper.valueToTree(response));

        verify(growthRecordRepository)
                .saveAndFlush(any(GrowthRecord.class));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource({
             "invalidRequiredNumericResults",
             "invalidStructuredResults",
             "invalidHealthProbabilityCombinations"
    })
    @DisplayName("유효하지 않은 Result 계약을 가진 응답은 거부한다")
    void rejectInvalidResults(
            String description,
            Result invalidResult
    ) {
        assertThat(description).isNotBlank();
        assertInvalidResult(invalidResult);
    }

    @Test
    @DisplayName("JSON 변환 중 IllegalArgumentException이 발생하면 안전한 직렬화 오류로 처리한다")
    void convertObjectMapperExceptionToSerializationFailure() {
        // Given: 외부 상세 메시지를 가진 JSON 변환 실패를 준비한다.
        DailyCultivationPhotoResponse photo = validPhoto();
        VisionResponse response = validSuccessResponse();
        ObjectMapper failingObjectMapper =
                mock(ObjectMapper.class);

        VisionRelayService serviceWithFailingMapper =
                serviceUsing(failingObjectMapper);

        stubAnalysis(photo, response);

        when(
                failingObjectMapper
                        .<JsonNode>valueToTree(response)
        ).thenThrow(
                new IllegalArgumentException(
                        EXTERNAL_SERIALIZATION_DETAIL
                )
        );

        // When: Vision 응답 전체를 JsonNode로 변환한다.
        VisionAnalysisException exception =
                catchThrowableOfType(
                        VisionAnalysisException.class,
                        () -> serviceWithFailingMapper.analyzeAndSave(
                                photo
                        )
                );

        // Then: 외부 원인과 URL을 노출하지 않는 직렬화 오류가 발생한다.
        assertVisionFailure(
                exception,
                "SERIALIZATION_FAILED"
        );

        assertSensitiveValueNotExposed(
                exception,
                EXTERNAL_SERIALIZATION_DETAIL
        );

        verify(failingObjectMapper)
                .valueToTree(response);

        verify(growthRecordRepository, never())
                .saveAndFlush(any(GrowthRecord.class));
    }

    @Test
    @DisplayName("JSON 변환 결과가 null이면 직렬화 오류로 처리한다")
    void rejectNullSerializationResult() {
        // Given: valueToTree가 null을 반환하도록 ObjectMapper를 준비한다.
        DailyCultivationPhotoResponse photo = validPhoto();
        VisionResponse response = validSuccessResponse();
        ObjectMapper nullReturningObjectMapper =
                mock(ObjectMapper.class);

        VisionRelayService serviceWithNullMapper =
                serviceUsing(nullReturningObjectMapper);

        stubAnalysis(photo, response);

        when(
                nullReturningObjectMapper
                        .<JsonNode>valueToTree(response)
        ).thenReturn(null);

        // When: Vision 응답을 JsonNode로 변환한다.
        VisionAnalysisException exception =
                catchThrowableOfType(
                        VisionAnalysisException.class,
                        () -> serviceWithNullMapper.analyzeAndSave(
                                photo
                        )
                );

        // Then: null 저장을 시도하지 않고 직렬화 오류로 처리한다.
        assertVisionFailure(
                exception,
                "SERIALIZATION_FAILED"
        );

        verify(nullReturningObjectMapper)
                .valueToTree(response);

        verify(growthRecordRepository, never())
                .saveAndFlush(any(GrowthRecord.class));
    }

    @Test
    @DisplayName("JSON 변환 결과가 객체 노드가 아니면 직렬화 오류로 처리한다")
    void rejectNonObjectSerializationResult() {
        // Given: 외부 응답 문자열을 담은 TextNode를 반환하도록 준비한다.
        DailyCultivationPhotoResponse photo = validPhoto();
        VisionResponse response = validSuccessResponse();
        ObjectMapper textReturningObjectMapper =
                mock(ObjectMapper.class);

        VisionRelayService serviceWithTextMapper =
                serviceUsing(textReturningObjectMapper);

        stubAnalysis(photo, response);

        when(
                textReturningObjectMapper
                        .<JsonNode>valueToTree(response)
        ).thenReturn(
                TextNode.valueOf(EXTERNAL_RESPONSE_DETAIL)
        );

        // When: Vision 응답을 JSONB 저장 데이터로 변환한다.
        VisionAnalysisException exception =
                catchThrowableOfType(
                        VisionAnalysisException.class,
                        () -> serviceWithTextMapper.analyzeAndSave(
                                photo
                        )
                );

        // Then: 외부 응답 내용을 노출하지 않는 직렬화 오류가 발생한다.
        assertVisionFailure(
                exception,
                "SERIALIZATION_FAILED"
        );

        assertSensitiveValueNotExposed(
                exception,
                EXTERNAL_RESPONSE_DETAIL
        );

        verify(textReturningObjectMapper)
                .valueToTree(response);

        verify(growthRecordRepository, never())
                .saveAndFlush(any(GrowthRecord.class));
    }

    @Test
    @DisplayName("UNIQUE 충돌 후 같은 경작지의 기존 레코드를 찾으면 해당 객체를 반환한다")
    void returnExistingRecordAfterUniqueConflict() {
        // Given: 최초 조회에는 없지만 저장 충돌 후 기존 레코드가 발견된다.
        DailyCultivationPhotoResponse photo = validPhoto();
        VisionResponse response = validSuccessResponse();

        GrowthRecord existingRecord =
                existingGrowthRecord(
                        CULTIVATION_ID,
                        PHOTO_ID
                );

        DataIntegrityViolationException databaseFailure =
                new DataIntegrityViolationException(
                        DATABASE_FAILURE_DETAIL
                );

        stubUniqueConflict(
                photo,
                response,
                Optional.of(existingRecord),
                databaseFailure
        );

        // When: 동시 저장으로 UNIQUE 제약 충돌이 발생한다.
        GrowthRecord actual =
                visionRelayService.analyzeAndSave(photo);

        // Then: 두 번째 조회에서 찾은 기존 객체를 그대로 반환한다.
        assertThat(actual)
                .isSameAs(existingRecord);

        verify(
                growthRecordRepository,
                times(2)
        ).findByCultivationPhotoId(PHOTO_ID);

        verify(growthRecordRepository)
                .saveAndFlush(any(GrowthRecord.class));
    }

    @Test
    @DisplayName("UNIQUE 충돌 후에도 기존 레코드가 없으면 멱등성 충돌로 처리한다")
    void rejectUniqueConflictWithoutExistingRecord() {
        // Given: 저장 충돌 뒤 재조회에서도 레코드가 발견되지 않는다.
        DailyCultivationPhotoResponse photo = validPhoto();
        VisionResponse response = validSuccessResponse();

        DataIntegrityViolationException databaseFailure =
                new DataIntegrityViolationException(
                        DATABASE_FAILURE_DETAIL
                );

        stubUniqueConflict(
                photo,
                response,
                Optional.empty(),
                databaseFailure
        );

        // When: 저장 충돌을 처리한다.
        VisionAnalysisException exception =
                catchThrowableOfType(
                        VisionAnalysisException.class,
                        () -> visionRelayService.analyzeAndSave(photo)
                );

        // Then: DB 상세 정보를 숨긴 멱등성 충돌로 처리한다.
        assertVisionFailure(
                exception,
                "IDEMPOTENCY_CONFLICT"
        );

        assertSensitiveValueNotExposed(
                exception,
                DATABASE_FAILURE_DETAIL
        );

        verify(
                growthRecordRepository,
                times(2)
        ).findByCultivationPhotoId(PHOTO_ID);

        verify(growthRecordRepository)
                .saveAndFlush(any(GrowthRecord.class));
    }

    @Test
    @DisplayName("UNIQUE 충돌 후 다른 경작지의 레코드가 발견되면 멱등성 충돌로 처리한다")
    void rejectUniqueConflictWithDifferentCultivation() {
        // Given: 저장 충돌 뒤 같은 사진 ID의 다른 경작지 레코드가 발견된다.
        DailyCultivationPhotoResponse photo = validPhoto();
        VisionResponse response = validSuccessResponse();

        GrowthRecord differentCultivationRecord =
                existingGrowthRecord(
                        999L,
                        PHOTO_ID
                );

        DataIntegrityViolationException databaseFailure =
                new DataIntegrityViolationException(
                        DATABASE_FAILURE_DETAIL
                );

        stubUniqueConflict(
                photo,
                response,
                Optional.of(differentCultivationRecord),
                databaseFailure
        );

        // When: 저장 충돌을 처리한다.
        VisionAnalysisException exception =
                catchThrowableOfType(
                        VisionAnalysisException.class,
                        () -> visionRelayService.analyzeAndSave(photo)
                );

        // Then: DB 상세 정보를 숨긴 멱등성 충돌로 처리한다.
        assertVisionFailure(
                exception,
                "IDEMPOTENCY_CONFLICT"
        );

        assertSensitiveValueNotExposed(
                exception,
                DATABASE_FAILURE_DETAIL
        );

        verify(
                growthRecordRepository,
                times(2)
        ).findByCultivationPhotoId(PHOTO_ID);

        verify(growthRecordRepository)
                .saveAndFlush(any(GrowthRecord.class));
    }

    @Test
    @DisplayName("이미지 다운로드 예외는 변환하지 않고 같은 인스턴스로 전파한다")
    void propagateImageDownloadExceptionWithoutConversion() {
        // Given: 다운로드 단계에서 안전한 ImageDownloadException이 발생한다.
        DailyCultivationPhotoResponse photo = validPhoto();

        ImageDownloadException expectedException =
                new ImageDownloadException(
                        PHOTO_ID,
                        ImageDownloadException.Reason.NETWORK_ERROR
                );

        when(
                growthRecordRepository
                        .findByCultivationPhotoId(PHOTO_ID)
        ).thenReturn(Optional.empty());

        when(
                presignedImageDownloader
                        .downloadAsMultipart(photo)
        ).thenThrow(expectedException);

        // When: 분석과 저장을 요청한다.
        Throwable actualException =
                catchThrowable(
                        () -> visionRelayService.analyzeAndSave(photo)
                );

        // Then: 원래 다운로드 예외가 그대로 전달되고 이후 단계는 실행되지 않는다.
        assertThat(actualException)
                .isSameAs(expectedException);

        verify(growthRecordRepository)
                .findByCultivationPhotoId(PHOTO_ID);

        verify(presignedImageDownloader)
                .downloadAsMultipart(photo);

        verifyNoInteractions(visionClient);

        verify(growthRecordRepository, never())
                .saveAndFlush(any(GrowthRecord.class));
    }

    @Test
    @DisplayName("VisionClient의 RuntimeException은 변환하지 않고 같은 인스턴스로 전파한다")
    void propagateVisionClientRuntimeExceptionWithoutConversion() {
        // Given: VisionClient 호출에서 외부 RuntimeException이 발생한다.
        DailyCultivationPhotoResponse photo = validPhoto();

        RuntimeException expectedException =
                new RuntimeException(
                        "vision-client-runtime-failure"
                );

        when(
                growthRecordRepository
                        .findByCultivationPhotoId(PHOTO_ID)
        ).thenReturn(Optional.empty());

        when(
                presignedImageDownloader
                        .downloadAsMultipart(photo)
        ).thenReturn(image);

        when(
                visionClient.analyzeMushroomHealth(image)
        ).thenThrow(expectedException);

        // When: 분석과 저장을 요청한다.
        Throwable actualException =
                catchThrowable(
                        () -> visionRelayService.analyzeAndSave(photo)
                );

        // Then: 원래 예외가 그대로 전달되고 DB 저장은 실행되지 않는다.
        assertThat(actualException)
                .isSameAs(expectedException);

        verify(growthRecordRepository)
                .findByCultivationPhotoId(PHOTO_ID);

        verify(presignedImageDownloader)
                .downloadAsMultipart(photo);

        verify(visionClient)
                .analyzeMushroomHealth(image);

        verify(growthRecordRepository, never())
                .saveAndFlush(any(GrowthRecord.class));
    }

    @Test
    @DisplayName("analyzeAndSave는 외부 트랜잭션을 중단하는 NOT_SUPPORTED 정책을 사용한다")
    void declareNotSupportedTransactionPropagation()
            throws NoSuchMethodException {
        // Given: analyzeAndSave 메서드의 리플렉션 정보를 조회한다.
        Method method =
                VisionRelayService.class.getDeclaredMethod(
                        "analyzeAndSave",
                        DailyCultivationPhotoResponse.class
                );

        // When: 메서드에 직접 선언된 트랜잭션 애노테이션을 읽는다.
        Transactional transactional =
                method.getAnnotation(Transactional.class);

        // Then: NOT_SUPPORTED 전파 정책이 명시되어 있다.
        assertThat(transactional)
                .isNotNull();

        assertThat(transactional.propagation())
                .isEqualTo(Propagation.NOT_SUPPORTED);
    }

    private void stubAnalysis(
            DailyCultivationPhotoResponse photo,
            VisionResponse response
    ) {
        when(
                growthRecordRepository
                        .findByCultivationPhotoId(PHOTO_ID)
        ).thenReturn(Optional.empty());

        when(
                presignedImageDownloader
                        .downloadAsMultipart(photo)
        ).thenReturn(image);

        when(
                visionClient.analyzeMushroomHealth(image)
        ).thenReturn(response);
    }

    private void stubSuccessfulSave(
            DailyCultivationPhotoResponse photo,
            VisionResponse response
    ) {
        stubAnalysis(photo, response);

        when(
                growthRecordRepository
                        .saveAndFlush(any(GrowthRecord.class))
        ).thenAnswer(
                invocation -> invocation.getArgument(
                        0,
                        GrowthRecord.class
                )
        );
    }

    private void stubUniqueConflict(
            DailyCultivationPhotoResponse photo,
            VisionResponse response,
            Optional<GrowthRecord> recordAfterConflict,
            DataIntegrityViolationException databaseFailure
    ) {
        when(
                growthRecordRepository
                        .findByCultivationPhotoId(PHOTO_ID)
        ).thenReturn(
                Optional.empty(),
                recordAfterConflict
        );

        when(
                presignedImageDownloader
                        .downloadAsMultipart(photo)
        ).thenReturn(image);

        when(
                visionClient.analyzeMushroomHealth(image)
        ).thenReturn(response);

        when(
                growthRecordRepository
                        .saveAndFlush(any(GrowthRecord.class))
        ).thenThrow(databaseFailure);
    }

    private void assertInvalidVisionResponse(
            VisionResponse invalidResponse
    ) {
        DailyCultivationPhotoResponse photo = validPhoto();

        stubAnalysis(photo, invalidResponse);

        VisionAnalysisException exception =
                catchThrowableOfType(
                        VisionAnalysisException.class,
                        () -> visionRelayService.analyzeAndSave(photo)
                );

        assertVisionFailure(
                exception,
                "INVALID_RESPONSE_CONTRACT"
        );

        verify(growthRecordRepository)
                .findByCultivationPhotoId(PHOTO_ID);

        verify(presignedImageDownloader)
                .downloadAsMultipart(photo);

        verify(visionClient)
                .analyzeMushroomHealth(image);

        verify(growthRecordRepository, never())
                .saveAndFlush(any(GrowthRecord.class));
    }

    private void assertInvalidResult(Result invalidResult) {
        assertInvalidVisionResponse(
                successResponse(invalidResult)
        );
    }

    private void assertVisionFailure(
            VisionAnalysisException exception,
            String expectedReason
    ) {
        assertThat(exception)
                .isNotNull()
                .hasNoCause();

        assertThat(exception.getMessage())
                .isEqualTo(USER_MESSAGE)
                .doesNotContain(
                        PRESIGNED_URL,
                        SIGNATURE_PARAMETER,
                        FAKE_SIGNATURE
                );

        String logContent =
                exception.getLogContent();

        assertThat(logContent)
                .contains(
                        "photoId=",
                        "reason=" + expectedReason
                )
                .doesNotContain(
                        PRESIGNED_URL,
                        SIGNATURE_PARAMETER,
                        FAKE_SIGNATURE
                );
    }

    private void assertSensitiveValueNotExposed(
            VisionAnalysisException exception,
            String sensitiveValue
    ) {
        assertThat(exception.getMessage())
                .doesNotContain(sensitiveValue);

        String logContent =
                exception.getLogContent();

        assertThat(logContent)
                .doesNotContain(sensitiveValue);
    }

    private VisionRelayService serviceUsing(
            ObjectMapper customObjectMapper
    ) {
        return new VisionRelayService(
                visionClient,
                presignedImageDownloader,
                growthRecordRepository,
                customObjectMapper
        );
    }

    private GrowthRecord existingGrowthRecord(
            Long cultivationId,
            Long photoId
    ) {
        JsonNode existingAnalysis =
                objectMapper.createObjectNode()
                        .put("status", "EXISTING");

        return GrowthRecord.builder()
                .cultivationId(cultivationId)
                .cultivationPhotoId(photoId)
                .analysisData(existingAnalysis)
                .build();
    }

    private static DailyCultivationPhotoResponse validPhoto() {
        return photoWithIds(
                CULTIVATION_ID,
                PHOTO_ID
        );
    }

    private static DailyCultivationPhotoResponse photoWithIds(
            Long cultivationId,
            Long photoId
    ) {
        return new DailyCultivationPhotoResponse(
                cultivationId,
                photoId,
                PRESIGNED_URL,
                OffsetDateTime.now().plusMinutes(5)
        );
    }

    private static Thresholds validThresholds() {
        return new Thresholds(
                0.70,
                0.50,
                0.65
        );
    }

    private static Result validUncertainResult() {
        return resultWithHealthProbabilities(
                UNCERTAIN,
                null,
                null,
                null
        );
    }

    private static Result validHealthyResult() {
        return resultWithHealthProbabilities(
                HEALTHY,
                0.90,
                0.90,
                0.10
        );
    }

    private static Result validDiseaseSuspectedResult() {
        return resultWithHealthProbabilities(
                DISEASE_SUSPECTED,
                0.85,
                0.15,
                0.85
        );
    }

    private static Result resultWithRequiredNumbers(
            Integer speciesClassId,
            Integer detectedCount,
            Double detectionConfidence,
            Double detectionConfidenceMin
    ) {
        return new Result(
                "느타리",
                "OYSTER",
                speciesClassId,
                detectedCount,
                detectionConfidence,
                detectionConfidenceMin,
                UNCERTAIN,
                null,
                null,
                null,
                VALID_BBOX,
                VALID_CROP_BBOX
        );
    }

    private static Result resultWithIdentity(
            String species,
            String speciesCode
    ) {
        return new Result(
                species,
                speciesCode,
                0,
                2,
                0.97,
                0.45,
                UNCERTAIN,
                null,
                null,
                null,
                VALID_BBOX,
                VALID_CROP_BBOX
        );
    }

    private static Result resultWithHealthProbabilities(
            String healthStatus,
            Double healthConfidence,
            Double healthyProbability,
            Double diseaseSuspectedProbability
    ) {
        return new Result(
                "느타리",
                "OYSTER",
                0,
                2,
                0.97,
                0.45,
                healthStatus,
                healthConfidence,
                healthyProbability,
                diseaseSuspectedProbability,
                VALID_BBOX,
                VALID_CROP_BBOX
        );
    }

    private static Result resultWithBoundingBoxes(
            List<Integer> bbox,
            List<Integer> cropBbox
    ) {
        return new Result(
                "느타리",
                "OYSTER",
                0,
                2,
                0.97,
                0.45,
                UNCERTAIN,
                null,
                null,
                null,
                bbox,
                cropBbox
        );
    }

    private static VisionResponse validSuccessResponse() {
        return successResponse(
                validUncertainResult()
        );
    }

    private static VisionResponse successResponse(
            Result result
    ) {
        return new VisionResponse(
                ANALYSIS_TYPE,
                SUCCESS,
                "detector-v1",
                "health-v1",
                validThresholds(),
                Collections.singletonList(result),
                List.of(
                        "낮은 탐지 신뢰도로 건강 분류가 생략되었습니다."
                )
        );
    }

    private static VisionResponse noMushroomResponse() {
        return new VisionResponse(
                ANALYSIS_TYPE,
                NO_MUSHROOM_DETECTED,
                "detector-v1",
                "health-v1",
                validThresholds(),
                List.of(),
                List.of("버섯을 탐지하지 못했습니다.")
        );
    }

    private static VisionResponse response(
            String analysisType,
            String status,
            String detectorModel,
            String healthModel,
            Thresholds thresholds,
            List<Result> results,
            List<String> warnings
    ) {
        return new VisionResponse(
                analysisType,
                status,
                detectorModel,
                healthModel,
                thresholds,
                results,
                warnings
        );
    }

    private static Stream<Arguments> invalidPhotos() {
        return Stream.of(
                Arguments.of(
                        "사진 DTO가 null",
                        null
                ),
                Arguments.of(
                        "cultivationId가 null",
                        photoWithIds(null, PHOTO_ID)
                ),
                Arguments.of(
                        "cultivationId가 0",
                        photoWithIds(0L, PHOTO_ID)
                ),
                Arguments.of(
                        "cultivationId가 음수",
                        photoWithIds(-1L, PHOTO_ID)
                ),
                Arguments.of(
                        "photoId가 null",
                        photoWithIds(CULTIVATION_ID, null)
                ),
                Arguments.of(
                        "photoId가 0",
                        photoWithIds(CULTIVATION_ID, 0L)
                ),
                Arguments.of(
                        "photoId가 음수",
                        photoWithIds(CULTIVATION_ID, -1L)
                )
        );
    }

    private static Stream<Arguments> invalidTopLevelResponses() {
        Thresholds thresholds = validThresholds();
        Result result = validUncertainResult();
        List<Result> validResults = List.of(result);
        List<String> validWarnings = List.of();

        return Stream.of(
                Arguments.of(
                        "응답 자체가 null",
                        null
                ),
                Arguments.of(
                        "analysisType이 null",
                        response(
                                null,
                                SUCCESS,
                                "detector-v1",
                                "health-v1",
                                thresholds,
                                validResults,
                                validWarnings
                        )
                ),
                Arguments.of(
                        "analysisType이 지원되지 않음",
                        response(
                                "OTHER_ANALYSIS",
                                SUCCESS,
                                "detector-v1",
                                "health-v1",
                                thresholds,
                                validResults,
                                validWarnings
                        )
                ),
                Arguments.of(
                        "status가 null",
                        response(
                                ANALYSIS_TYPE,
                                null,
                                "detector-v1",
                                "health-v1",
                                thresholds,
                                validResults,
                                validWarnings
                        )
                ),
                Arguments.of(
                        "status가 지원되지 않음",
                        response(
                                ANALYSIS_TYPE,
                                "FAILED",
                                "detector-v1",
                                "health-v1",
                                thresholds,
                                validResults,
                                validWarnings
                        )
                ),
                Arguments.of(
                        "detectorModel이 null",
                        response(
                                ANALYSIS_TYPE,
                                SUCCESS,
                                null,
                                "health-v1",
                                thresholds,
                                validResults,
                                validWarnings
                        )
                ),
                Arguments.of(
                        "detectorModel이 빈 문자열",
                        response(
                                ANALYSIS_TYPE,
                                SUCCESS,
                                "",
                                "health-v1",
                                thresholds,
                                validResults,
                                validWarnings
                        )
                ),
                Arguments.of(
                        "detectorModel이 공백",
                        response(
                                ANALYSIS_TYPE,
                                SUCCESS,
                                "   ",
                                "health-v1",
                                thresholds,
                                validResults,
                                validWarnings
                        )
                ),
                Arguments.of(
                        "healthModel이 null",
                        response(
                                ANALYSIS_TYPE,
                                SUCCESS,
                                "detector-v1",
                                null,
                                thresholds,
                                validResults,
                                validWarnings
                        )
                ),
                Arguments.of(
                        "healthModel이 빈 문자열",
                        response(
                                ANALYSIS_TYPE,
                                SUCCESS,
                                "detector-v1",
                                "",
                                thresholds,
                                validResults,
                                validWarnings
                        )
                ),
                Arguments.of(
                        "healthModel이 공백",
                        response(
                                ANALYSIS_TYPE,
                                SUCCESS,
                                "detector-v1",
                                "   ",
                                thresholds,
                                validResults,
                                validWarnings
                        )
                ),
                Arguments.of(
                        "thresholds가 null",
                        response(
                                ANALYSIS_TYPE,
                                SUCCESS,
                                "detector-v1",
                                "health-v1",
                                null,
                                validResults,
                                validWarnings
                        )
                ),
                Arguments.of(
                        "results가 null",
                        response(
                                ANALYSIS_TYPE,
                                SUCCESS,
                                "detector-v1",
                                "health-v1",
                                thresholds,
                                null,
                                validWarnings
                        )
                ),
                Arguments.of(
                        "warnings가 null",
                        response(
                                ANALYSIS_TYPE,
                                SUCCESS,
                                "detector-v1",
                                "health-v1",
                                thresholds,
                                validResults,
                                null
                        )
                ),
                Arguments.of(
                        "warnings 내부 원소가 null",
                        response(
                                ANALYSIS_TYPE,
                                SUCCESS,
                                "detector-v1",
                                "health-v1",
                                thresholds,
                                validResults,
                                Collections.singletonList(
                                        (String) null
                                )
                        )
                ),
                Arguments.of(
                        "SUCCESS인데 results가 비어 있음",
                        response(
                                ANALYSIS_TYPE,
                                SUCCESS,
                                "detector-v1",
                                "health-v1",
                                thresholds,
                                List.of(),
                                validWarnings
                        )
                ),
                Arguments.of(
                        "NO_MUSHROOM_DETECTED인데 results가 존재함",
                        response(
                                ANALYSIS_TYPE,
                                NO_MUSHROOM_DETECTED,
                                "detector-v1",
                                "health-v1",
                                thresholds,
                                validResults,
                                validWarnings
                        )
                )
        );
    }

    private static Stream<Arguments> invalidThresholds() {
        return invalidProbabilityValues()
                .flatMap(
                        invalidValue -> Stream.of(
                                Arguments.of(
                                        "detection이 "
                                                + invalidValue.description(),
                                        new Thresholds(
                                                invalidValue.value(),
                                                0.50,
                                                0.65
                                        )
                                ),
                                Arguments.of(
                                        "minDetectionConfidence가 "
                                                + invalidValue.description(),
                                        new Thresholds(
                                                0.70,
                                                invalidValue.value(),
                                                0.65
                                        )
                                ),
                                Arguments.of(
                                        "healthUncertain이 "
                                                + invalidValue.description(),
                                        new Thresholds(
                                                0.70,
                                                0.50,
                                                invalidValue.value()
                                        )
                                )
                        )
                );
    }

    private static Stream<Arguments> invalidRequiredNumericResults() {
        Stream<Arguments> integerCases =
                Stream.of(
                        Arguments.of(
                                "speciesClassId가 null",
                                resultWithRequiredNumbers(
                                        null,
                                        2,
                                        0.97,
                                        0.45
                                )
                        ),
                        Arguments.of(
                                "speciesClassId가 음수",
                                resultWithRequiredNumbers(
                                        -1,
                                        2,
                                        0.97,
                                        0.45
                                )
                        ),
                        Arguments.of(
                                "detectedCount가 null",
                                resultWithRequiredNumbers(
                                        0,
                                        null,
                                        0.97,
                                        0.45
                                )
                        ),
                        Arguments.of(
                                "detectedCount가 0",
                                resultWithRequiredNumbers(
                                        0,
                                        0,
                                        0.97,
                                        0.45
                                )
                        ),
                        Arguments.of(
                                "detectedCount가 음수",
                                resultWithRequiredNumbers(
                                        0,
                                        -1,
                                        0.97,
                                        0.45
                                )
                        )
                );

        Stream<Arguments> confidenceCases =
                invalidProbabilityValues()
                        .flatMap(
                                invalidValue -> Stream.of(
                                        Arguments.of(
                                                "detectionConfidence가 "
                                                        + invalidValue.description(),
                                                resultWithRequiredNumbers(
                                                        0,
                                                        2,
                                                        invalidValue.value(),
                                                        0.45
                                                )
                                        ),
                                        Arguments.of(
                                                "detectionConfidenceMin이 "
                                                        + invalidValue.description(),
                                                resultWithRequiredNumbers(
                                                        0,
                                                        2,
                                                        0.97,
                                                        invalidValue.value()
                                                )
                                        )
                                )
                        );

        Stream<Arguments> orderingCase =
                Stream.of(
                        Arguments.of(
                                "최저 탐지 신뢰도가 최고 탐지 신뢰도보다 큼",
                                resultWithRequiredNumbers(
                                        0,
                                        2,
                                        0.60,
                                        0.70
                                )
                        )
                );

        return Stream.concat(
                Stream.concat(
                        integerCases,
                        confidenceCases
                ),
                orderingCase
        );
    }

    private static Stream<Arguments> invalidStructuredResults() {
        return Stream.of(
                Arguments.of(
                        "Result 자체가 null",
                        (Result) null
                ),
                Arguments.of(
                        "species가 null",
                        resultWithIdentity(null, "OYSTER")
                ),
                Arguments.of(
                        "species가 빈 문자열",
                        resultWithIdentity("", "OYSTER")
                ),
                Arguments.of(
                        "species가 공백",
                        resultWithIdentity("   ", "OYSTER")
                ),
                Arguments.of(
                        "speciesCode가 null",
                        resultWithIdentity("느타리", null)
                ),
                Arguments.of(
                        "speciesCode가 빈 문자열",
                        resultWithIdentity("느타리", "")
                ),
                Arguments.of(
                        "speciesCode가 공백",
                        resultWithIdentity("느타리", "   ")
                ),
                Arguments.of(
                        "healthStatus가 알 수 없는 값",
                        resultWithHealthProbabilities(
                                "UNKNOWN",
                                null,
                                null,
                                null
                        )
                ),
                Arguments.of(
                        "bbox가 null",
                        resultWithBoundingBoxes(
                                null,
                                VALID_CROP_BBOX
                        )
                ),
                Arguments.of(
                        "bbox 크기가 4가 아님",
                        resultWithBoundingBoxes(
                                List.of(10, 20, 110),
                                VALID_CROP_BBOX
                        )
                ),
                Arguments.of(
                        "bbox 원소가 null",
                        resultWithBoundingBoxes(
                                Arrays.asList(
                                        10,
                                        20,
                                        null,
                                        220
                                ),
                                VALID_CROP_BBOX
                        )
                ),
                Arguments.of(
                        "bbox 원소가 음수",
                        resultWithBoundingBoxes(
                                List.of(-1, 20, 110, 220),
                                VALID_CROP_BBOX
                        )
                ),
                Arguments.of(
                        "cropBbox가 null",
                        resultWithBoundingBoxes(
                                VALID_BBOX,
                                null
                        )
                ),
                Arguments.of(
                        "cropBbox 크기가 4가 아님",
                        resultWithBoundingBoxes(
                                VALID_BBOX,
                                List.of(5, 15, 115)
                        )
                ),
                Arguments.of(
                        "cropBbox 원소가 null",
                        resultWithBoundingBoxes(
                                VALID_BBOX,
                                Arrays.asList(
                                        5,
                                        15,
                                        null,
                                        225
                                )
                        )
                ),
                Arguments.of(
                        "cropBbox 원소가 음수",
                        resultWithBoundingBoxes(
                                VALID_BBOX,
                                List.of(5, -1, 115, 225)
                        )
                )
        );
    }

    private static Stream<Arguments>
    validHealthProbabilityCombinations() {
        return Stream.of(
                Arguments.of(
                        "HEALTHY이며 세 확률이 모두 존재함",
                        validHealthyResult()
                ),
                Arguments.of(
                        "DISEASE_SUSPECTED이며 세 확률이 모두 존재함",
                        validDiseaseSuspectedResult()
                ),
                Arguments.of(
                        "UNCERTAIN이며 세 확률이 모두 null",
                        validUncertainResult()
                ),
                Arguments.of(
                        "UNCERTAIN이며 세 확률이 모두 존재함",
                        resultWithHealthProbabilities(
                                UNCERTAIN,
                                0.55,
                                0.55,
                                0.45
                        )
                )
        );
    }

    private static Stream<Arguments>
    invalidHealthProbabilityCombinations() {
        Stream<Arguments> presenceCases =
                Stream.of(
                        Arguments.of(
                                "HEALTHY인데 healthConfidence가 null",
                                resultWithHealthProbabilities(
                                        HEALTHY,
                                        null,
                                        0.90,
                                        0.10
                                )
                        ),
                        Arguments.of(
                                "HEALTHY인데 healthyProbability가 null",
                                resultWithHealthProbabilities(
                                        HEALTHY,
                                        0.90,
                                        null,
                                        0.10
                                )
                        ),
                        Arguments.of(
                                "HEALTHY인데 diseaseSuspectedProbability가 null",
                                resultWithHealthProbabilities(
                                        HEALTHY,
                                        0.90,
                                        0.90,
                                        null
                                )
                        ),
                        Arguments.of(
                                "DISEASE_SUSPECTED인데 확률 일부가 null",
                                resultWithHealthProbabilities(
                                        DISEASE_SUSPECTED,
                                        0.85,
                                        null,
                                        0.85
                                )
                        ),
                        Arguments.of(
                                "UNCERTAIN인데 한 값만 null",
                                resultWithHealthProbabilities(
                                        UNCERTAIN,
                                        null,
                                        0.55,
                                        0.45
                                )
                        ),
                        Arguments.of(
                                "UNCERTAIN인데 한 값만 존재",
                                resultWithHealthProbabilities(
                                        UNCERTAIN,
                                        0.55,
                                        null,
                                        null
                                )
                        )
                );

        Stream<Arguments> invalidValueCases =
                invalidPresentProbabilityValues()
                        .flatMap(
                                invalidValue -> Stream.of(
                                        Arguments.of(
                                                "healthConfidence가 "
                                                        + invalidValue.description(),
                                                resultWithHealthProbabilities(
                                                        UNCERTAIN,
                                                        invalidValue.value(),
                                                        0.55,
                                                        0.45
                                                )
                                        ),
                                        Arguments.of(
                                                "healthyProbability가 "
                                                        + invalidValue.description(),
                                                resultWithHealthProbabilities(
                                                        HEALTHY,
                                                        0.90,
                                                        invalidValue.value(),
                                                        0.10
                                                )
                                        ),
                                        Arguments.of(
                                                "diseaseSuspectedProbability가 "
                                                        + invalidValue.description(),
                                                resultWithHealthProbabilities(
                                                        DISEASE_SUSPECTED,
                                                        0.85,
                                                        0.15,
                                                        invalidValue.value()
                                                )
                                        )
                                )
                        );

        return Stream.concat(
                presenceCases,
                invalidValueCases
        );
    }

    private static Stream<InvalidProbability>
    invalidProbabilityValues() {
        return Stream.of(
                new InvalidProbability("null", null),
                new InvalidProbability("NaN", Double.NaN),
                new InvalidProbability("양의 Infinity", Double.POSITIVE_INFINITY),
                new InvalidProbability("음의 Infinity", Double.NEGATIVE_INFINITY),
                new InvalidProbability("0.0 미만", -0.01),
                new InvalidProbability("1.0 초과", 1.01)
        );
    }

    private static Stream<InvalidProbability>
    invalidPresentProbabilityValues() {
        return Stream.of(
                new InvalidProbability("NaN", Double.NaN),
                new InvalidProbability("양의 Infinity", Double.POSITIVE_INFINITY),
                new InvalidProbability("음의 Infinity", Double.NEGATIVE_INFINITY),
                new InvalidProbability("0.0 미만", -0.01),
                new InvalidProbability("1.0 초과", 1.01)
        );
    }

    private record InvalidProbability(
            String description,
            Double value
    ) {
    }
}
