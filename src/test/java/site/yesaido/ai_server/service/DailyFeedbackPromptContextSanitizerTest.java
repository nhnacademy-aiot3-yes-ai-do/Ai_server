package site.yesaido.ai_server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import site.yesaido.ai_server.exception.AiAnalysisFailedException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@DisplayName("일일 피드백 프롬프트 Context 정제기 테스트")
class DailyFeedbackPromptContextSanitizerTest {

    private static final JsonNodeFactory NODE_FACTORY =
            JsonNodeFactory.instance;

    private static final String INVALID_CONTEXT_MESSAGE =
            "일일 피드백 Context JSON 형식이 올바르지 않습니다.";

    private static final String FORBIDDEN_CONNECTION_MESSAGE =
            "일일 피드백 Context에 외부 전송이 허용되지 않는 연결 정보가 포함되어 있습니다.";

    private static final String REMOVED_VALUE =
            "TEST_ONLY_REMOVED_VALUE";

    private DailyFeedbackPromptContextSanitizer sanitizer;

    @BeforeEach
    void setUpSanitizer() {
        sanitizer = new DailyFeedbackPromptContextSanitizer();
    }

    @Test
    @DisplayName("정상적인 중첩 object는 새로운 object로 정제해 반환한다")
    void returnsNewSanitizedObjectForValidNestedContext() {
        // 준비
        ObjectNode original = validNestedContext();

        // 실행
        JsonNode sanitized = sanitizer.sanitize(original);

        // 검증
        assertThat(sanitized)
                .isInstanceOf(ObjectNode.class)
                .isNotSameAs(original);

        assertThat(sanitized.has("cultivationId")).isFalse();
        assertThat(
                sanitized.path("cultivationDetail")
                        .path("name")
                        .asText()
        ).isEqualTo("테스트 재배지");

        assertThat(
                sanitized.path("sensorStatistics")
                        .get(0)
                        .path("channelKey")
                        .path("deviceEui")
                        .asText()
        ).isEqualTo("EUI-001");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("invalidTopLevelInputs")
    @DisplayName("최상위 입력이 object가 아니면 거부한다")
    void rejectsInvalidTopLevelInput(
            String caseDescription,
            JsonNode invalidInput
    ) {
        // 준비
        JsonNode contextNode = invalidInput;

        // 실행
        AiAnalysisFailedException exception =
                captureFailure(contextNode);

        // 검증
        assertThat(exception)
                .isNotNull()
                .isInstanceOf(AiAnalysisFailedException.class);

        String message = exception.getMessage();

        assertThat(message).isEqualTo(INVALID_CONTEXT_MESSAGE);
    }

    @Test
    @DisplayName("최상위·중첩 object와 배열 내부의 ID 필드를 모두 제거한다")
    void removesIdFieldsFromEntireTree() {
        // 준비
        ObjectNode original = NODE_FACTORY.objectNode();
        original.put("id", 1L);
        original.put("cultivationId", 7L);
        original.put("safeRootValue", "ROOT");

        ObjectNode nested = NODE_FACTORY.objectNode();
        nested.put("mushroomId", 11L);
        nested.put("sensorTypeId", 12L);
        nested.put("growthRecordId", 13L);
        nested.put("safeNestedValue", "NESTED");
        original.set("nested", nested);

        ObjectNode arrayElement = NODE_FACTORY.objectNode();
        arrayElement.put("photoId", 21L);
        arrayElement.put("userID", 22L);
        arrayElement.put("sensor_type_id", 23L);
        arrayElement.put("cultivation-photo-id", 24L);
        arrayElement.put("safeArrayValue", "ARRAY");

        ArrayNode items = NODE_FACTORY.arrayNode();
        items.add(arrayElement);
        original.set("items", items);

        // 실행
        JsonNode sanitized = sanitizer.sanitize(original);

        // 검증
        assertThat(sanitized.has("id")).isFalse();
        assertThat(sanitized.has("cultivationId")).isFalse();
        assertThat(sanitized.path("safeRootValue").asText())
                .isEqualTo("ROOT");

        JsonNode sanitizedNested = sanitized.path("nested");

        assertThat(sanitizedNested.has("mushroomId")).isFalse();
        assertThat(sanitizedNested.has("sensorTypeId")).isFalse();
        assertThat(sanitizedNested.has("growthRecordId")).isFalse();
        assertThat(sanitizedNested.path("safeNestedValue").asText())
                .isEqualTo("NESTED");

        JsonNode sanitizedArrayElement =
                sanitized.path("items").get(0);

        assertThat(sanitizedArrayElement.has("photoId")).isFalse();
        assertThat(sanitizedArrayElement.has("userID")).isFalse();
        assertThat(sanitizedArrayElement.has("sensor_type_id"))
                .isFalse();
        assertThat(sanitizedArrayElement.has("cultivation-photo-id"))
                .isFalse();
        assertThat(sanitizedArrayElement.path("safeArrayValue").asText())
                .isEqualTo("ARRAY");
    }

    @Test
    @DisplayName("ID처럼 보일 수 있는 일반 필드와 필요한 시간 필드를 보존한다")
    void preservesAllowedFieldNames() {
        // 준비
        ObjectNode original = NODE_FACTORY.objectNode();
        original.put("deviceEui", "EUI-ABC-001");
        original.put("valid", true);
        original.put("humidity", new BigDecimal("87.50"));
        original.put("sensorType", "TEMPERATURE");
        original.put("unit", "℃");
        original.put("feedbackDate", "2026-09-01");
        original.put(
                "dataGeneratorSnapshotAt",
                "2026-09-02T00:05:00+09:00"
        );
        original.put("startedAt", "2026-08-20T10:00:00");
        original.putNull("finishedAt");
        original.put("analyzedAt", "2026-09-02T00:06:00");

        // 실행
        JsonNode sanitized = sanitizer.sanitize(original);

        // 검증
        List<String> expectedFields = List.of(
                "deviceEui",
                "valid",
                "humidity",
                "sensorType",
                "unit",
                "feedbackDate",
                "dataGeneratorSnapshotAt",
                "startedAt",
                "finishedAt",
                "analyzedAt"
        );

        for (String expectedField : expectedFields) {
            assertThat(sanitized.has(expectedField)).isTrue();
        }

        assertThat(sanitized.path("deviceEui").asText())
                .isEqualTo("EUI-ABC-001");
        assertThat(sanitized.path("valid").asBoolean()).isTrue();
        assertThat(sanitized.path("humidity").decimalValue())
                .isEqualByComparingTo("87.50");
        assertThat(sanitized.path("finishedAt").isNull()).isTrue();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("allSensitiveFieldNameCases")
    @DisplayName("민감 필드는 표기 형식과 관계없이 제거한다")
    void removesSensitiveFieldNames(
            String sensitiveFieldName
    ) {
        // 준비
        ObjectNode original = NODE_FACTORY.objectNode();
        original.put("safeSibling", "KEEP_ME");
        original.put(sensitiveFieldName, REMOVED_VALUE);

        // 실행
        JsonNode sanitized = sanitizer.sanitize(original);

        // 검증
        assertThat(sanitized.has(sensitiveFieldName)).isFalse();
        assertThat(sanitized.path("safeSibling").asText())
                .isEqualTo("KEEP_ME");
        assertThat(sanitized.toString())
                .doesNotContain(REMOVED_VALUE);
    }

    @Test
    @DisplayName("민감 필드를 제거해도 형제 필드와 배열 요소를 유지한다")
    void preservesSiblingsAndArrayElementsWhenRemovingSensitiveFields() {
        // 준비
        ObjectNode original = NODE_FACTORY.objectNode();
        original.put("safeSibling", "KEEP_ME");
        original.put("secret", "REMOVE_SECRET");

        ArrayNode items = NODE_FACTORY.arrayNode();
        items.add("FIRST_ELEMENT");

        ObjectNode objectElement = NODE_FACTORY.objectNode();
        objectElement.put("token", "REMOVE_TOKEN");
        objectElement.put("sensorType", "TEMPERATURE");
        items.add(objectElement);

        items.add(42);
        original.set("items", items);

        // 실행
        JsonNode sanitized = sanitizer.sanitize(original);

        // 검증
        assertThat(sanitized.has("secret")).isFalse();
        assertThat(sanitized.path("safeSibling").asText())
                .isEqualTo("KEEP_ME");

        JsonNode sanitizedItems = sanitized.path("items");

        assertThat(sanitizedItems.size()).isEqualTo(3);
        assertThat(sanitizedItems.get(0).asText())
                .isEqualTo("FIRST_ELEMENT");
        assertThat(sanitizedItems.get(1).has("token")).isFalse();
        assertThat(
                sanitizedItems.get(1)
                        .path("sensorType")
                        .asText()
        ).isEqualTo("TEMPERATURE");
        assertThat(sanitizedItems.get(2).intValue()).isEqualTo(42);

        assertThat(sanitized.toString())
                .doesNotContain("REMOVE_SECRET")
                .doesNotContain("REMOVE_TOKEN");
    }

    @Test
    @DisplayName("스칼라 값과 빈 컨테이너의 의미 및 정밀도를 유지한다")
    void preservesScalarValuesPrecisionAndEmptyContainers() {
        // 준비
        BigDecimal preciseDecimal = new BigDecimal(
                "12345678901234567890.123456789012345678901234567890"
        );

        ObjectNode original = NODE_FACTORY.objectNode();
        original.put("preciseDecimal", preciseDecimal);
        original.put("integerValue", 987654321);
        original.put("booleanValue", true);
        original.put("textValue", "일반 문자열");
        original.putNull("jsonNull");
        original.set("emptyObject", NODE_FACTORY.objectNode());
        original.set("emptyArray", NODE_FACTORY.arrayNode());

        // 실행
        JsonNode sanitized = sanitizer.sanitize(original);

        // 검증
        BigDecimal actualDecimal =
                sanitized.path("preciseDecimal").decimalValue();

        assertThat(actualDecimal.toPlainString())
                .isEqualTo(preciseDecimal.toPlainString());
        assertThat(actualDecimal.scale())
                .isEqualTo(preciseDecimal.scale());

        assertThat(sanitized.path("integerValue").isIntegralNumber())
                .isTrue();
        assertThat(sanitized.path("integerValue").intValue())
                .isEqualTo(987654321);
        assertThat(sanitized.path("booleanValue").asBoolean()).isTrue();
        assertThat(sanitized.path("textValue").asText())
                .isEqualTo("일반 문자열");
        assertThat(sanitized.path("jsonNull").isNull()).isTrue();

        assertThat(sanitized.path("emptyObject").isObject()).isTrue();
        assertThat(sanitized.path("emptyObject").size()).isZero();
        assertThat(sanitized.path("emptyArray").isArray()).isTrue();
        assertThat(sanitized.path("emptyArray").size()).isZero();
    }

    @Test
    @DisplayName("object 필드 순서와 array 요소 순서를 유지한다")
    void preservesObjectFieldAndArrayElementOrder() {
        // 준비
        ObjectNode original = NODE_FACTORY.objectNode();
        original.put("thirdField", "THIRD");
        original.put("secret", "REMOVE_ME");
        original.put("firstField", "FIRST");
        original.put("secondField", "SECOND");

        ArrayNode orderedValues = NODE_FACTORY.arrayNode();
        orderedValues.add("THIRD");
        orderedValues.add("FIRST");
        orderedValues.add("SECOND");
        original.set("orderedValues", orderedValues);

        // 실행
        JsonNode sanitized = sanitizer.sanitize(original);

        // 검증
        List<String> actualFieldOrder = new ArrayList<>();
        sanitized.fieldNames()
                .forEachRemaining(actualFieldOrder::add);

        assertThat(actualFieldOrder).containsExactly(
                "thirdField",
                "firstField",
                "secondField",
                "orderedValues"
        );

        List<String> actualArrayOrder = new ArrayList<>();
        sanitized.path("orderedValues")
                .forEach(
                        value -> actualArrayOrder.add(value.asText())
                );

        assertThat(actualArrayOrder).containsExactly(
                "THIRD",
                "FIRST",
                "SECOND"
        );
    }

    @Test
    @DisplayName("최상위·중첩 object와 array를 새로운 인스턴스로 생성한다")
    void createsNewContainerInstances() {
        // 준비
        ObjectNode original = NODE_FACTORY.objectNode();

        ObjectNode nested = NODE_FACTORY.objectNode();
        nested.put("name", "중첩 객체");

        ObjectNode arrayElement = NODE_FACTORY.objectNode();
        arrayElement.put("value", "배열 객체");

        ArrayNode items = NODE_FACTORY.arrayNode();
        items.add(arrayElement);

        original.set("nested", nested);
        original.set("items", items);

        // 실행
        JsonNode sanitized = sanitizer.sanitize(original);

        // 검증
        assertThat(sanitized).isNotSameAs(original);
        assertThat(sanitized.path("nested")).isNotSameAs(nested);
        assertThat(sanitized.path("items")).isNotSameAs(items);
        assertThat(sanitized.path("items").get(0))
                .isNotSameAs(arrayElement);
    }

    @Test
    @DisplayName("정제 후에도 원본의 ID와 민감 필드는 변경되지 않는다")
    void doesNotModifyOriginalWhenSanitizing() {
        // 준비
        ObjectNode original = NODE_FACTORY.objectNode();
        original.put("id", 101L);
        original.put("secret", "ORIGINAL_SECRET");

        ObjectNode nested = NODE_FACTORY.objectNode();
        nested.put("growthRecordId", 202L);
        nested.put("name", "원본 중첩 객체");
        original.set("nested", nested);

        ObjectNode arrayElement = NODE_FACTORY.objectNode();
        arrayElement.put("token", "ORIGINAL_TOKEN");
        arrayElement.put("value", "원본 배열 객체");

        ArrayNode items = NODE_FACTORY.arrayNode();
        items.add(arrayElement);
        original.set("items", items);

        // 실행
        JsonNode sanitized = sanitizer.sanitize(original);

        // 검증
        assertThat(original.has("id")).isTrue();
        assertThat(original.path("id").longValue()).isEqualTo(101L);
        assertThat(original.has("secret")).isTrue();
        assertThat(original.path("secret").asText())
                .isEqualTo("ORIGINAL_SECRET");

        assertThat(original.path("nested").has("growthRecordId"))
                .isTrue();
        assertThat(
                original.path("nested")
                        .path("growthRecordId")
                        .longValue()
        ).isEqualTo(202L);

        assertThat(original.path("items").get(0).has("token"))
                .isTrue();
        assertThat(
                original.path("items")
                        .get(0)
                        .path("token")
                        .asText()
        ).isEqualTo("ORIGINAL_TOKEN");

        assertThat(sanitized.has("id")).isFalse();
        assertThat(sanitized.has("secret")).isFalse();
        assertThat(sanitized.path("nested").has("growthRecordId"))
                .isFalse();
        assertThat(sanitized.path("items").get(0).has("token"))
                .isFalse();
    }

    @Test
    @DisplayName("정제 결과를 수정해도 원본 중첩 object와 array는 변경되지 않는다")
    void modifyingSanitizedResultDoesNotChangeOriginalContainers() {
        // 준비
        ObjectNode original = NODE_FACTORY.objectNode();

        ObjectNode nested = NODE_FACTORY.objectNode();
        nested.put("name", "원본 이름");

        ObjectNode arrayElement = NODE_FACTORY.objectNode();
        arrayElement.put("value", "원본 요소");

        ArrayNode items = NODE_FACTORY.arrayNode();
        items.add(arrayElement);

        original.set("nested", nested);
        original.set("items", items);

        // 실행
        ObjectNode sanitized =
                (ObjectNode) sanitizer.sanitize(original);

        ((ObjectNode) sanitized.path("nested"))
                .put("name", "변경된 이름");
        ((ObjectNode) sanitized.path("items").get(0))
                .put("value", "변경된 요소");
        ((ArrayNode) sanitized.path("items"))
                .add("추가 요소");

        // 검증
        assertThat(original.path("nested").path("name").asText())
                .isEqualTo("원본 이름");
        assertThat(
                original.path("items")
                        .get(0)
                        .path("value")
                        .asText()
        ).isEqualTo("원본 요소");
        assertThat(original.path("items").size()).isEqualTo(1);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("forbiddenStringValueCases")
    @DisplayName("안전한 필드의 문자열에 외부 연결정보가 있으면 거부한다")
    void rejectsForbiddenConnectionInformationInStringValues(
            String caseDescription,
            String forbiddenValue
    ) {
        // 준비
        ObjectNode original = NODE_FACTORY.objectNode();
        original.put("warning", forbiddenValue);
        String originalJson = original.toString();

        // 실행
        AiAnalysisFailedException exception =
                captureFailure(original);

        // 검증
        assertForbiddenConnectionFailure(
                exception,
                forbiddenValue,
                originalJson
        );
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("forbiddenFieldNameCases")
    @DisplayName("JSON 필드명 자체에 외부 연결정보가 있으면 거부한다")
    void rejectsForbiddenConnectionInformationInFieldNames(
            String caseDescription,
            String forbiddenFieldName
    ) {
        // 준비
        ObjectNode original = NODE_FACTORY.objectNode();
        original.put(forbiddenFieldName, "SAFE_VALUE");
        String originalJson = original.toString();

        // 실행
        AiAnalysisFailedException exception =
                captureFailure(original);

        // 검증
        assertForbiddenConnectionFailure(
                exception,
                forbiddenFieldName,
                originalJson
        );
    }

    @Test
    @DisplayName("URL을 값으로 가진 presignedUrl 필드는 검사 전에 제거한다")
    void removesPresignedUrlBeforeInspectingItsValue() {
        // 준비
        String testUrl =
                "https://image-test.invalid/object"
                        + "?X-Amz-Signature=FAKE_SIGNATURE";

        ObjectNode original = NODE_FACTORY.objectNode();
        original.put("name", "안전한 재배지");
        original.put("presignedUrl", testUrl);

        // 실행
        JsonNode sanitized = sanitizer.sanitize(original);

        // 검증
        assertThat(sanitized.has("presignedUrl")).isFalse();
        assertThat(sanitized.path("name").asText())
                .isEqualTo("안전한 재배지");
        assertThat(sanitized.toString()).doesNotContain(testUrl);

        assertThat(original.has("presignedUrl")).isTrue();
        assertThat(original.path("presignedUrl").asText())
                .isEqualTo(testUrl);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("allowedStringValueCases")
    @DisplayName("일반 분석 문자열과 EUI 값은 오탐하지 않고 보존한다")
    void preservesAllowedStringValues(
            String caseDescription,
            String allowedValue
    ) {
        // 준비
        ObjectNode original = NODE_FACTORY.objectNode();
        original.put("content", allowedValue);

        // 실행
        JsonNode sanitized = sanitizer.sanitize(original);

        // 검증
        assertThat(sanitized.path("content").asText())
                .isEqualTo(allowedValue);
    }

    @Test
    @DisplayName("빈 입력 object는 새로운 빈 object로 반환한다")
    void returnsNewEmptyObjectForEmptyInputObject() {
        // 준비
        ObjectNode original = NODE_FACTORY.objectNode();

        // 실행
        JsonNode sanitized = sanitizer.sanitize(original);

        // 검증
        assertThat(sanitized)
                .isInstanceOf(ObjectNode.class)
                .isNotSameAs(original);
        assertThat(sanitized.size()).isZero();
    }

    @Test
    @DisplayName("예외 메시지에 원본 URL·서명값·전체 JSON을 포함하지 않는다")
    void doesNotExposeSensitiveContextInExceptionMessage() {
        // 준비
        String testUrl =
                "https://context-test.invalid/private-resource";
        String secretValue =
                "FAKE_SIGNATURE_VALUE_123";

        ObjectNode original = NODE_FACTORY.objectNode();
        original.put(
                "warning",
                testUrl + "?signature = " + secretValue
        );

        String originalJson = original.toString();

        // 실행
        AiAnalysisFailedException exception =
                captureFailure(original);

        // 검증
        assertThat(exception)
                .isNotNull()
                .isInstanceOf(AiAnalysisFailedException.class);

        String message = exception.getMessage();

        assertThat(message)
                .isEqualTo(FORBIDDEN_CONNECTION_MESSAGE)
                .doesNotContain(testUrl)
                .doesNotContain(secretValue)
                .doesNotContain(originalJson);
    }

    private AiAnalysisFailedException captureFailure(
            JsonNode contextNode
    ) {
        return catchThrowableOfType(
                AiAnalysisFailedException.class,
                () -> sanitizer.sanitize(contextNode)
        );
    }

    private static void assertForbiddenConnectionFailure(
            AiAnalysisFailedException exception,
            String secretValue,
            String originalJson
    ) {
        assertThat(exception)
                .isNotNull()
                .isInstanceOf(AiAnalysisFailedException.class);

        String message = exception.getMessage();

        assertThat(message)
                .isEqualTo(FORBIDDEN_CONNECTION_MESSAGE)
                .doesNotContain(secretValue)
                .doesNotContain(originalJson);
    }

    private static Stream<Arguments> invalidTopLevelInputs() {
        return Stream.of(
                Arguments.of(
                        "Java null",
                        (JsonNode) null
                ),
                Arguments.of(
                        "JSON null",
                        NODE_FACTORY.nullNode()
                ),
                Arguments.of(
                        "array",
                        NODE_FACTORY.arrayNode()
                ),
                Arguments.of(
                        "text",
                        NODE_FACTORY.textNode("일반 문자열")
                ),
                Arguments.of(
                        "number",
                        NODE_FACTORY.numberNode(42)
                ),
                Arguments.of(
                        "boolean",
                        NODE_FACTORY.booleanNode(true)
                )
        );
    }

    private static Stream<Arguments> allSensitiveFieldNameCases() {
        return Stream.concat(
                sensitiveFieldNameCases(),
                normalizedSensitiveFieldNameCases()
        );
    }

    private static Stream<Arguments> sensitiveFieldNameCases() {
        return Stream.of(
                Arguments.of("presignedUrl"),
                Arguments.of("objectKey"),
                Arguments.of("url"),
                Arguments.of("signature"),
                Arguments.of("credential"),
                Arguments.of("secret"),
                Arguments.of("token"),
                Arguments.of("password"),
                Arguments.of("myRole"),
                Arguments.of("model"),
                Arguments.of("modelName"),
                Arguments.of("modelVersion"),
                Arguments.of("detectorModel"),
                Arguments.of("healthModel"),
                Arguments.of("boundingBox"),
                Arguments.of("bbox"),
                Arguments.of("cropBbox"),
                Arguments.of("createdAt"),
                Arguments.of("updatedAt"),
                Arguments.of("expiresAt")
        );
    }

    private static Stream<Arguments> normalizedSensitiveFieldNameCases() {
        return Stream.of(
                Arguments.of("presigned_url"),
                Arguments.of("object-key"),
                Arguments.of("model_version"),
                Arguments.of("bounding-box"),
                Arguments.of("created_at")
        );
    }

    private static Stream<Arguments> forbiddenStringValueCases() {
        return Stream.of(
                Arguments.of(
                        "HTTP URL",
                        "http://context-test.invalid/resource"
                ),
                Arguments.of(
                        "HTTPS URL",
                        "https://context-test.invalid/resource"
                ),
                Arguments.of(
                        "S3 URL",
                        "s3://context-test-bucket/resource"
                ),
                Arguments.of(
                        "대소문자가 섞인 URL",
                        "hTtPs://context-test.invalid/resource"
                ),
                Arguments.of(
                        "AWS 서명",
                        "X-Amz-Signature=FAKE_AMZ_VALUE"
                ),
                Arguments.of(
                        "Google 자격 증명",
                        "X-Goog-Credential=FAKE_GOOG_VALUE"
                ),
                Arguments.of(
                        "signature query",
                        "signature=FAKE_VALUE"
                ),
                Arguments.of(
                        "공백이 있는 signature query",
                        "signature = FAKE_VALUE"
                ),
                Arguments.of(
                        "credential query",
                        "credential=FAKE_VALUE"
                ),
                Arguments.of(
                        "공백이 있는 credential query",
                        "credential = FAKE_VALUE"
                ),
                Arguments.of(
                        "인코딩된 signature query",
                        "signature%3DFAKE_VALUE"
                ),
                Arguments.of(
                        "인코딩된 credential query",
                        "credential%3DFAKE_VALUE"
                )
        );
    }

    private static Stream<Arguments> forbiddenFieldNameCases() {
        return Stream.of(
                Arguments.of(
                        "URL이 포함된 필드명",
                        "https://hidden-field.invalid"
                ),
                Arguments.of(
                        "AWS 서명이 포함된 필드명",
                        "X-Amz-Metadata"
                ),
                Arguments.of(
                        "signature query가 포함된 필드명",
                        "signature=FAKE_FIELD_VALUE"
                )
        );
    }

    private static Stream<Arguments> allowedStringValueCases() {
        return Stream.of(
                Arguments.of(
                        "일반적인 Vision warning",
                        "버섯 영역의 색상 편차가 있어 추가 관찰이 필요합니다."
                ),
                Arguments.of(
                        "버섯 한글·영문·학명",
                        "느타리버섯 Oyster mushroom Pleurotus ostreatus"
                ),
                Arguments.of(
                        "등호가 없는 signature 단어",
                        "signature 용어는 일반 설명으로만 사용되었습니다."
                ),
                Arguments.of(
                        "등호가 없는 credential 단어",
                        "credential 용어는 일반 설명으로만 사용되었습니다."
                ),
                Arguments.of(
                        "일반 Markdown 문장",
                        "- **온도 변화**를 계속 확인해 주세요."
                ),
                Arguments.of(
                        "EUI 값",
                        "EUI-ABC-001"
                )
        );
    }

    private static ObjectNode validNestedContext() {
        ObjectNode root = NODE_FACTORY.objectNode();
        root.put("cultivationId", 7L);
        root.put("feedbackDate", "2026-09-01");
        root.put(
                "dataGeneratorSnapshotAt",
                "2026-09-02T00:05:00+09:00"
        );

        ObjectNode cultivationDetail = NODE_FACTORY.objectNode();
        cultivationDetail.put("cultivationId", 7L);
        cultivationDetail.put("name", "테스트 재배지");
        cultivationDetail.put("status", "RUNNING");
        cultivationDetail.put("startedAt", "2026-08-20T10:00:00");
        root.set("cultivationDetail", cultivationDetail);

        ObjectNode channelKey = NODE_FACTORY.objectNode();
        channelKey.put("cultivationId", 7L);
        channelKey.put("deviceEui", "EUI-001");
        channelKey.put("sensorType", "TEMPERATURE");
        channelKey.put("unit", "℃");

        ObjectNode statistics = NODE_FACTORY.objectNode();
        statistics.set("channelKey", channelKey);
        statistics.put("minimumValue", new BigDecimal("18.2"));
        statistics.put("averageValue", new BigDecimal("20.1"));
        statistics.put("maximumValue", new BigDecimal("21.8"));
        statistics.put("aggregationPointCount", 96);

        ArrayNode sensorStatistics = NODE_FACTORY.arrayNode();
        sensorStatistics.add(statistics);
        root.set("sensorStatistics", sensorStatistics);

        return root;
    }
}
