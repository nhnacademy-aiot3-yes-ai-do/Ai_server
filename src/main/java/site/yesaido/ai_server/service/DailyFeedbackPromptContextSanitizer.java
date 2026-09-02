package site.yesaido.ai_server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import site.yesaido.ai_server.exception.AiAnalysisFailedException;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 일일 피드백 Context를 외부 LLM에 전달하기 전에 최소 권한의
 * 데이터 구조로 정제하는 보안 경계입니다.
 *
 * <p>내부 ID, 연결정보, 역할과 저장 시각 등 프롬프트 생성에 필요하지
 * 않은 필드를 제거하고, 보존하는 문자열에 외부 주소나 서명정보가
 * 숨어 있지 않은지 검사합니다.</p>
 *
 * <p>DB의 {@code context_snapshot}으로 보존할 원본 Context JSON은
 * 변경하지 않습니다. object와 array를 새로 생성하여 프롬프트 전송용
 * 복사본만 정제합니다.</p>
 *
 * <p>이 클래스는 Context 직렬화, 프롬프트 렌더링, LLM 호출,
 * 출력 검증, DB 저장 또는 RabbitMQ 이벤트 발행을 수행하지 않습니다.</p>
 */
@Component
public class DailyFeedbackPromptContextSanitizer {

    private static final JsonNodeFactory NODE_FACTORY =
            JsonNodeFactory.instance;

    /**
     * 구분자와 대소문자를 정규화한 민감 필드명입니다.
     *
     * <p>실제 Vision 원본 응답의 {@code detectorModel},
     * {@code healthModel}, {@code cropBbox}도 각각 모델 및
     * bounding box 메타데이터이므로 정확한 별칭으로 제거합니다.</p>
     */
    private static final Set<String> SENSITIVE_FIELD_NAMES = Set.of(
            "presignedurl",
            "objectkey",
            "url",
            "signature",
            "credential",
            "secret",
            "token",
            "password",
            "myrole",
            "model",
            "modelname",
            "modelversion",
            "detectormodel",
            "healthmodel",
            "boundingbox",
            "bbox",
            "cropbbox",
            "createdat",
            "updatedat",
            "expiresat"
    );

    private static final Pattern FORBIDDEN_CONNECTION_VALUE_PATTERN =
            Pattern.compile(
                    "(?:https?|s3)://"
                            + "|x-amz-"
                            + "|x-goog-"
                            + "|(?:signature|credential)\\s*(?:=|%3d)",
                    Pattern.CASE_INSENSITIVE
            );

    private static final String INVALID_CONTEXT_MESSAGE =
            "일일 피드백 Context JSON 형식이 올바르지 않습니다.";

    private static final String FORBIDDEN_CONNECTION_MESSAGE =
            "일일 피드백 Context에 외부 전송이 허용되지 않는 연결 정보가 포함되어 있습니다.";

    /**
     * 일일 피드백 Context JSON을 외부 LLM 전송용으로 정제합니다.
     *
     * <p>최상위 object와 모든 중첩 object·array를 새로 생성합니다.
     * 원본 필드 순서와 배열 요소 순서, 숫자의 정밀도, boolean,
     * 일반 문자열과 JSON null의 의미는 그대로 보존합니다.</p>
     *
     * @param contextNode DailyFeedbackContext를 변환한 원본 Jackson 2 JsonNode
     * @return 내부 ID와 민감정보가 제거된 새로운 object JsonNode
     * @throws AiAnalysisFailedException 입력이 object가 아니거나,
     *         보존 대상 문자열에 외부 주소 또는 서명정보가 포함된 경우
     */
    public JsonNode sanitize(JsonNode contextNode) {
        if (contextNode == null || contextNode.isNull() || !contextNode.isObject()) {
            throw new AiAnalysisFailedException(INVALID_CONTEXT_MESSAGE);
        }

        return copyObjectNode(contextNode);
    }

    private static JsonNode copyAndSanitizeNode(JsonNode sourceNode) {
        if (sourceNode == null || sourceNode.isNull()) {
            return NODE_FACTORY.nullNode();
        }

        if (sourceNode.isObject()) {
            return copyObjectNode(sourceNode);
        }

        if (sourceNode.isArray()) {
            return copyArrayNode(sourceNode);
        }

        return copyValueNode(sourceNode);
    }

    private static ObjectNode copyObjectNode(JsonNode sourceObject) {
        ObjectNode sanitizedObject = NODE_FACTORY.objectNode();

        for (Map.Entry<String, JsonNode> field : sourceObject.properties()) {
            String fieldName = field.getKey();
            if (FORBIDDEN_CONNECTION_VALUE_PATTERN.matcher(fieldName).find()) {
                throw new AiAnalysisFailedException(FORBIDDEN_CONNECTION_MESSAGE);
            }

            if (isSensitiveFieldName(fieldName)) {
                continue;
            }

            JsonNode sanitizedValue = copyAndSanitizeNode(field.getValue());
            sanitizedObject.set(fieldName, sanitizedValue);
        }

        return sanitizedObject;
    }

    private static ArrayNode copyArrayNode(JsonNode sourceArray) {
        ArrayNode sanitizedArray = NODE_FACTORY.arrayNode();
        Iterator<JsonNode> elements = sourceArray.elements();

        while (elements.hasNext()) {
            JsonNode sanitizedElement = copyAndSanitizeNode(elements.next());
            sanitizedArray.add(sanitizedElement);
        }

        return sanitizedArray;
    }

    private static JsonNode copyValueNode(JsonNode sourceValue) {
        if (sourceValue.isTextual() && FORBIDDEN_CONNECTION_VALUE_PATTERN.matcher(sourceValue.textValue()).find()) {
            throw new AiAnalysisFailedException(FORBIDDEN_CONNECTION_MESSAGE);
        }

        return sourceValue.deepCopy();
    }

    private static boolean isSensitiveFieldName(String fieldName) {
        if ("deviceEui".equals(fieldName)) {
            return false;
        }

        String candidate = fieldName.strip();

        if (candidate.equalsIgnoreCase("id")) {
            return true;
        }

        if (candidate.endsWith("Id") || hasUppercaseIdSuffix(candidate)) {
            return true;
        }

        String lowerCaseName = candidate.toLowerCase(Locale.ROOT);

        if (lowerCaseName.endsWith("_id") || lowerCaseName.endsWith("-id")) {
            return true;
        }

        String normalizedName = lowerCaseName
                .replace("_", "")
                .replace("-", "");

        return SENSITIVE_FIELD_NAMES.contains(normalizedName);
    }

    private static boolean hasUppercaseIdSuffix(String fieldName) {
        if ("ID".equals(fieldName)) {
            return true;
        }

        if (!fieldName.endsWith("ID") || fieldName.length() <= 2) {
            return false;
        }

        char precedingCharacter = fieldName.charAt(fieldName.length() - 3);

        return Character.isLowerCase(precedingCharacter) || Character.isDigit(precedingCharacter);
    }
}
