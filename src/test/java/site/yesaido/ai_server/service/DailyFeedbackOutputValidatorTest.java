package site.yesaido.ai_server.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import site.yesaido.ai_server.exception.AiAnalysisFailedException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@DisplayName("일일 피드백 출력 검증기 테스트")
class DailyFeedbackOutputValidatorTest {

    private static final String EMPTY_OUTPUT_MESSAGE =
            "일일 피드백 응답이 비어 있습니다.";

    private static final String INVALID_FIRST_HEADING_MESSAGE =
            "일일 피드백 응답의 첫 번째 제목이 올바르지 않습니다.";

    private static final String INVALID_HEADING_STRUCTURE_MESSAGE =
            "일일 피드백 응답의 Markdown 제목 구조가 올바르지 않습니다.";

    private static final String EMPTY_SECTION_MESSAGE =
            "일일 피드백 응답에 본문이 없는 섹션이 있습니다.";

    private static final String CODE_FENCE_MESSAGE =
            "일일 피드백 응답에 코드 펜스가 포함되어 있습니다.";

    private static final String EXTERNAL_RESOURCE_MESSAGE =
            "일일 피드백 응답에 외부 주소 또는 서명정보가 포함되어 있습니다.";

    private static final String INTERNAL_FIELD_MESSAGE =
            "일일 피드백 응답에 노출할 수 없는 내부 필드명이 포함되어 있습니다.";

    private static final List<String> REQUIRED_HEADINGS = List.of(
            "## 오늘의 환경 요약",
            "## 센서별 통계",
            "## 이탈 및 제어",
            "## Vision 분석",
            "## 내일의 관리 포인트"
    );

    private static final List<String> VALID_SECTION_BODIES = List.of(
            "느타리버섯 재배 환경의 하루 데이터를 확인했습니다.",
            "- deviceEui EUI-001의 TEMPERATURE(℃): "
                    + "최솟값 18.2℃, 평균값 20.1℃, 최댓값 21.8℃입니다.\n"
                    + "- 최근 24시간의 15분 평균 집계점은 96개입니다.",
            "- 환경 유지율은 92.5%이며 임계값 이탈 알림은 2건입니다.\n"
                    + "- 액추에이터 제어 성공은 1건, 실패는 0건입니다.",
            "사진이 등록되지 않아 Vision 분석이 없습니다.",
            "- 온도 변화와 액추에이터 제어 결과를 확인해 주세요."
    );

    private DailyFeedbackOutputValidator validator;

    @BeforeEach
    void setUpValidator() {
        validator = new DailyFeedbackOutputValidator();
    }

    @Test
    @DisplayName("완전히 유효한 응답은 원문 그대로 반환한다")
    void returnsValidOutputWithoutModification() {
        // 준비
        String output = validFeedback();

        // 실행
        String normalized = validator.validateAndNormalize(output);

        // 검증
        assertThat(normalized).isEqualTo(output);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("lineEndingCases")
    @DisplayName("CRLF와 단독 CR 줄바꿈을 LF로 정규화한다")
    void normalizesLineEndingsToLf(
            String caseDescription,
            String output
    ) {
        // 준비
        String expected = validFeedback();

        // 실행
        String normalized = validator.validateAndNormalize(output);

        // 검증
        assertThat(normalized)
                .isEqualTo(expected)
                .doesNotContain("\r");
    }

    @Test
    @DisplayName("문자열 끝의 빈 줄과 바깥 공백을 제거한다")
    void stripsTrailingBlankLinesAndOuterWhitespace() {
        // 준비
        String expected = validFeedback();
        String output = expected + "\n\n \t\r\n";

        // 실행
        String normalized = validator.validateAndNormalize(output);

        // 검증
        assertThat(normalized).isEqualTo(expected);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("nullOrBlankOutputCases")
    @DisplayName("null 또는 blank 응답을 거부한다")
    void rejectsNullOrBlankOutput(
            String caseDescription,
            String output
    ) {
        // 준비
        String invalidOutput = output;

        // 실행
        AiAnalysisFailedException throwable =
                captureFailure(invalidOutput);

        // 검증
        assertThat(throwable)
                .isNotNull()
                .isInstanceOf(AiAnalysisFailedException.class);

        String message = throwable.getMessage();

        assertThat(message).isEqualTo(EMPTY_OUTPUT_MESSAGE);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("contentBeforeFirstHeadingCases")
    @DisplayName("첫 제목 앞에 빈 줄·공백·서문이 있으면 거부한다")
    void rejectsContentBeforeFirstHeading(
            String caseDescription,
            String output
    ) {
        // 준비
        String invalidOutput = output;

        // 실행
        AiAnalysisFailedException throwable = captureFailure(invalidOutput);

        // 검증
        assertSafeFailure(throwable, INVALID_FIRST_HEADING_MESSAGE, invalidOutput);
    }

    @Test
    @DisplayName("첫 제목이 오늘의 환경 요약이 아니면 거부한다")
    void rejectsWrongFirstHeading() {
        // 준비
        String output = validFeedback().replace("## 오늘의 환경 요약", "## 오늘 환경 요약");

        // 실행
        AiAnalysisFailedException throwable = captureFailure(output);

        // 검증
        assertSafeFailure(throwable, INVALID_FIRST_HEADING_MESSAGE, output);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("invalidHeadingContractCases")
    @DisplayName("필수 제목의 누락·순서·중복·공백 계약 위반을 거부한다")
    void rejectsInvalidHeadingContract(
            String caseDescription,
            String output
    ) {
        // 준비
        String invalidOutput = output;

        // 실행
        AiAnalysisFailedException throwable = captureFailure(invalidOutput);

        // 검증
        assertSafeFailure(throwable, INVALID_HEADING_STRUCTURE_MESSAGE, invalidOutput);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("additionalAtxHeadingCases")
    @DisplayName("필수 제목 이외의 Markdown ATX 제목을 거부한다")
    void rejectsAdditionalAtxHeadings(
            String caseDescription,
            String additionalHeading
    ) {
        // 준비
        String output = feedbackContainingLine(additionalHeading);

        // 실행
        AiAnalysisFailedException throwable = captureFailure(output);

        // 검증
        assertSafeFailure(throwable, INVALID_HEADING_STRUCTURE_MESSAGE, output);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("emptySectionCases")
    @DisplayName("본문이 비어 있는 모든 섹션을 거부한다")
    void rejectsSectionWithoutBody(
            String caseDescription,
            String output
    ) {
        // 준비
        String invalidOutput = output;

        // 실행
        AiAnalysisFailedException throwable = captureFailure(invalidOutput);

        // 검증
        assertSafeFailure(throwable, EMPTY_SECTION_MESSAGE, invalidOutput);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("codeFenceCases")
    @DisplayName("백틱과 물결표 Markdown 코드 펜스를 거부한다")
    void rejectsCodeFences(
            String caseDescription,
            String codeFence
    ) {
        // 준비
        String output = feedbackContainingLine(codeFence);

        // 실행
        AiAnalysisFailedException throwable = captureFailure(output);

        // 검증
        assertSafeFailure(throwable, CODE_FENCE_MESSAGE, output);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("externalAddressCases")
    @DisplayName("외부 주소 표기를 대소문자와 관계없이 거부한다")
    void rejectsExternalAddresses(
            String caseDescription,
            String externalAddress
    ) {
        // 준비
        String output = feedbackContainingLine("- 외부 연결 정보: " + externalAddress);

        // 실행
        AiAnalysisFailedException throwable = captureFailure(output);

        // 검증
        assertSafeFailure(throwable, EXTERNAL_RESOURCE_MESSAGE, output);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("signatureInformationCases")
    @DisplayName("외부 저장소의 서명과 자격 증명 정보를 거부한다")
    void rejectsSignatureInformation(
            String caseDescription,
            String signatureInformation
    ) {
        // 준비
        String output = feedbackContainingLine("- 외부 연결 정보: " + signatureInformation);

        // 실행
        AiAnalysisFailedException throwable = captureFailure(output);

        // 검증
        assertSafeFailure(throwable, EXTERNAL_RESOURCE_MESSAGE, output);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("internalFieldNameCases")
    @DisplayName("노출이 금지된 내부 필드명을 표기 형태와 관계없이 거부한다")
    void rejectsInternalFieldNames(
            String caseDescription,
            String internalFieldName
    ) {
        // 준비
        String output = feedbackContainingLine(
                "- 내부 진단 필드: "
                        + internalFieldName
                        + "=987654321"
        );

        // 실행
        AiAnalysisFailedException throwable = captureFailure(output);

        // 검증
        assertSafeFailure(throwable, INTERNAL_FIELD_MESSAGE, output);
    }

    @Test
    @DisplayName("deviceEui와 실제 EUI 값은 센서 채널 구분 정보로 허용한다")
    void allowsDeviceEuiAndActualEuiValue() {
        // 준비
        String output = feedbackContainingLine("- deviceEui EUI-ABC-001 채널을 별도로 확인했습니다.");

        // 실행
        String normalized = validator.validateAndNormalize(output);

        // 검증
        assertThat(normalized)
                .isEqualTo(output)
                .contains("deviceEui")
                .contains("EUI-ABC-001");
    }

    @Test
    @DisplayName("일반 센서 수치·날짜·퍼센트·집계점 개수와 문장 속 # 기호를 허용한다")
    void allowsOrdinaryMeasurementsDatesPercentagesAndCounts() {
        // 준비
        String output = feedbackContainingLine(
                "- 2026-08-31 15:30 기준 18.25℃, "
                        + "유지율 92.5%, count=96이며 "
                        + "일반 문장의 # 기호도 데이터입니다."
        );

        // 실행
        String normalized = validator.validateAndNormalize(output);

        // 검증
        assertThat(normalized)
                .isEqualTo(output)
                .contains("2026-08-31")
                .contains("18.25℃")
                .contains("92.5%")
                .contains("count=96");
    }

    @Test
    @DisplayName("본문 속 제목 문자열을 실제 Markdown 제목 줄로 오인하지 않는다")
    void allowsHeadingTextInsideOrdinarySentence() {
        // 준비
        String output = feedbackContainingLine("본문에서 ## 센서별 통계라는 제목을 언급하는 것은 허용됩니다.");

        // 실행
        String normalized = validator.validateAndNormalize(output);

        // 검증
        assertThat(normalized).isEqualTo(output);
    }

    @Test
    @DisplayName("실패 예외 메시지에 원본 URL과 서명값을 노출하지 않는다")
    void doesNotExposeSensitiveOutputInExceptionMessage() {
        // 준비
        String testUrl = "https://daily-feedback-test.invalid/private-resource";
        String testSignature = "signature=TEST_ONLY_SIGNATURE_VALUE";

        String output = feedbackContainingLine(
                "- 외부 연결 정보: "
                        + testUrl
                        + "?"
                        + testSignature
        );

        // 실행
        AiAnalysisFailedException throwable = captureFailure(output);

        // 검증
        assertThat(throwable)
                .isNotNull()
                .isInstanceOf(AiAnalysisFailedException.class);

        String message = throwable.getMessage();

        boolean hasSafeFixedMessage = EXTERNAL_RESOURCE_MESSAGE.equals(message);
        boolean exposesTestUrl = message.contains(testUrl);
        boolean exposesTestSignature = message.contains(testSignature);
        boolean exposesOriginalOutput = message.contains(output);

        assertThat(hasSafeFixedMessage).isTrue();
        assertThat(exposesTestUrl).isFalse();
        assertThat(exposesTestSignature).isFalse();
        assertThat(exposesOriginalOutput).isFalse();
    }

    private AiAnalysisFailedException captureFailure(String output) {
        return catchThrowableOfType(AiAnalysisFailedException.class,
                () -> validator.validateAndNormalize(output));
    }

    private static void assertSafeFailure(
            AiAnalysisFailedException throwable,
            String expectedMessage,
            String originalOutput
    ) {
        assertThat(throwable)
                .isNotNull()
                .isInstanceOf(AiAnalysisFailedException.class);

        String message = throwable.getMessage();

        assertThat(message)
                .isEqualTo(expectedMessage)
                .doesNotContain(originalOutput);
    }

    private static Stream<Arguments> lineEndingCases() {
        String validOutput = validFeedback();

        return Stream.of(
                Arguments.of("CRLF 줄바꿈", validOutput.replace("\n", "\r\n")),
                Arguments.of("단독 CR 줄바꿈", validOutput.replace("\n", "\r"))
        );
    }

    private static Stream<Arguments> nullOrBlankOutputCases() {
        return Stream.of(
                Arguments.of("null 입력", (String) null),
                Arguments.of("빈 문자열", ""),
                Arguments.of("공백 문자열", "   "),
                Arguments.of("줄바꿈과 탭 문자열", "\t\r\n")
        );
    }

    private static Stream<Arguments> contentBeforeFirstHeadingCases() {
        String validOutput = validFeedback();

        return Stream.of(
                Arguments.of("첫 제목 앞 빈 줄", "\n" + validOutput),
                Arguments.of("첫 제목 앞 공백", " " + validOutput),
                Arguments.of("첫 제목 앞 서문", "일일 피드백 안내입니다.\n" + validOutput)
        );
    }

    private static Stream<Arguments> invalidHeadingContractCases() {
        String validOutput = validFeedback();

        String missingHeading = validOutput.replace(
                "## 센서별 통계\n",
                ""
        );

        String changedOrder = validOutput
                .replace("## 센서별 통계", "__SENSOR_HEADING__")
                .replace("## 이탈 및 제어", "## 센서별 통계")
                .replace("__SENSOR_HEADING__", "## 이탈 및 제어");

        String duplicatedHeading = validOutput.replace(
                "## 이탈 및 제어",
                """
                ## 센서별 통계
                중복된 제목의 본문입니다.

                ## 이탈 및 제어\
                """
        );

        String additionalH2Heading = validOutput.replace(
                "## Vision 분석",
                """
                ## 추가 분석
                추가 제목의 본문입니다.

                ## Vision 분석\
                """
        );

        String headingWithTrailingSpace = validOutput.replace("## 센서별 통계", "## 센서별 통계 ");
        String headingWithLeadingSpace = validOutput.replace("## 센서별 통계", " ## 센서별 통계");

        return Stream.of(
                Arguments.of("필수 제목 누락", missingHeading),
                Arguments.of("필수 제목 순서 변경", changedOrder),
                Arguments.of("필수 제목 중복", duplicatedHeading),
                Arguments.of("추가 H2 제목", additionalH2Heading),
                Arguments.of("필수 제목 뒤 trailing space", headingWithTrailingSpace),
                Arguments.of("필수 제목 앞 leading space", headingWithLeadingSpace)
        );
    }

    private static Stream<Arguments> additionalAtxHeadingCases() {
        return Stream.of(
                Arguments.of("추가 H1 제목", "# 추가 제목"),
                Arguments.of("추가 H3 제목", "### 추가 제목"),
                Arguments.of("추가 H6 제목", "###### 추가 제목")
        );
    }

    private static Stream<Arguments> emptySectionCases() {
        return Stream.of(
                Arguments.of("오늘의 환경 요약 본문 없음", feedbackWithEmptySection(0)),
                Arguments.of("센서별 통계 본문 없음", feedbackWithEmptySection(1)),
                Arguments.of("이탈 및 제어 본문 없음", feedbackWithEmptySection(2)),
                Arguments.of("Vision 분석 본문 없음", feedbackWithEmptySection(3)),
                Arguments.of("내일의 관리 포인트 본문 없음", feedbackWithEmptySection(4))
        );
    }

    private static Stream<Arguments> codeFenceCases() {
        return Stream.of(
                Arguments.of("들여쓰기 없는 백틱 펜스", "```"),
                Arguments.of("들여쓰기 없는 물결표 펜스", "~~~"),
                Arguments.of("한 칸 들여쓴 백틱 펜스", " ```java"),
                Arguments.of("한 칸 들여쓴 물결표 펜스", " ~~~text"),
                Arguments.of("두 칸 들여쓴 백틱 펜스", "  ```java"),
                Arguments.of("두 칸 들여쓴 물결표 펜스", "  ~~~text"),
                Arguments.of("세 칸 들여쓴 백틱 펜스", "   ```java"),
                Arguments.of("세 칸 들여쓴 물결표 펜스", "   ~~~text")
        );
    }

    private static Stream<Arguments> externalAddressCases() {
        return Stream.of(
                Arguments.of("HTTP 주소", "http://daily-feedback-test.invalid/resource"),
                Arguments.of("HTTPS 주소", "https://daily-feedback-test.invalid/resource"),
                Arguments.of("S3 주소", "s3://daily-feedback-test-bucket/resource"),
                Arguments.of("대소문자가 섞인 URL scheme", "hTtPs://daily-feedback-test.invalid/resource")
        );
    }

    private static Stream<Arguments> signatureInformationCases() {
        return Stream.of(
                Arguments.of("AWS 서명 정보", "X-Amz-Signature=TEST_ONLY_VALUE"),
                Arguments.of("Google 자격 증명 정보", "X-Goog-Credential=TEST_ONLY_VALUE"),
                Arguments.of("signature query parameter", "signature=TEST_ONLY_VALUE"),
                Arguments.of("credential query parameter", "credential=TEST_ONLY_VALUE"),
                Arguments.of("URL 인코딩된 signature query parameter", "signature%3DTEST_ONLY_VALUE")
        );
    }

    private static Stream<Arguments> internalFieldNameCases() {
        return Stream.of(
                Arguments.of("camelCase 필드 1", "cultivationId"),
                Arguments.of("camelCase 필드 2", "mushroomId"),
                Arguments.of("camelCase 필드 3", "thresholdId"),
                Arguments.of("camelCase 필드 4", "sensorTypeId"),
                Arguments.of("camelCase 필드 5", "growthRecordId"),
                Arguments.of("camelCase 필드 6", "cultivationPhotoId"),
                Arguments.of("camelCase 필드 7", "photoId"),
                Arguments.of("camelCase 필드 8", "userId"),
                Arguments.of("camelCase 필드 9", "ownerUserId"),
                Arguments.of("camelCase 필드 10", "presignedUrl"),
                Arguments.of("camelCase 필드 11", "objectKey"),
                Arguments.of("snake_case 필드 1", "cultivation_id"),
                Arguments.of("snake_case 필드 2", "cultivation_photo_id"),
                Arguments.of("snake_case 필드 3", "sensor_type_id"),
                Arguments.of("snake_case 필드 4", "growth_record_id"),
                Arguments.of("snake_case 필드 5", "owner_user_id"),
                Arguments.of("snake_case 필드 6", "presigned_url"),
                Arguments.of("snake_case 필드 7", "object_key"),
                Arguments.of("kebab-case 필드 1", "cultivation-id"),
                Arguments.of("kebab-case 필드 2", "cultivation-photo-id"),
                Arguments.of("kebab-case 필드 3", "threshold-id"),
                Arguments.of("kebab-case 필드 4", "growth-record-id"),
                Arguments.of("kebab-case 필드 5", "owner-user-id"),
                Arguments.of("kebab-case 필드 6", "presigned-url"),
                Arguments.of("kebab-case 필드 7", "object-key"),
                Arguments.of("대소문자가 섞인 내부 필드", "Mushroom_ID")
        );
    }

    private static String validFeedback() {
        return buildFeedback(VALID_SECTION_BODIES);
    }

    private static String feedbackContainingLine(String line) {
        List<String> sectionBodies = new ArrayList<>(VALID_SECTION_BODIES);

        sectionBodies.set(0, sectionBodies.get(0) + "\n" + line);

        return buildFeedback(sectionBodies);
    }

    private static String feedbackWithEmptySection(int sectionIndex) {
        List<String> sectionBodies = new ArrayList<>(VALID_SECTION_BODIES);

        sectionBodies.set(sectionIndex, " \t");

        return buildFeedback(sectionBodies);
    }

    private static String buildFeedback(List<String> sectionBodies) {
        StringBuilder output = new StringBuilder();

        for (int index = 0; index < REQUIRED_HEADINGS.size(); index++) {
            if (index > 0) {
                output.append("\n\n");
            }

            output.append(REQUIRED_HEADINGS.get(index))
                    .append('\n')
                    .append(sectionBodies.get(index));
        }

        return output.toString();
    }
}
