package site.yesaido.ai_server.service;

import org.springframework.stereotype.Component;
import site.yesaido.ai_server.exception.AiAnalysisFailedException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * LLM이 생성한 일일 피드백을 저장하기 전에 출력 형식과
 * 민감정보 노출 여부를 검사하는 최종 검증 게이트입니다.
 *
 * <p>프롬프트의 출력 지시만 신뢰하지 않고, 필수 Markdown 제목과
 * 섹션 본문, 코드 펜스, 외부 주소 및 내부 필드명 노출 여부를
 * 결정론적인 코드로 다시 검증합니다.</p>
 *
 * <p>검증에 성공하면 줄바꿈과 전체 앞뒤 공백을 정규화한 문자열을
 * 반환합니다. 이 클래스는 LLM 호출, JSON 직렬화, DB 저장 또는
 * RabbitMQ 이벤트 발행을 수행하지 않습니다.</p>
 */
@Component
public class DailyFeedbackOutputValidator {

    private static final List<String> REQUIRED_HEADINGS = List.of(
            "## 오늘의 환경 요약",
            "## 센서별 통계",
            "## 이탈 및 제어",
            "## Vision 분석",
            "## 내일의 관리 포인트"
    );

    private static final Pattern ATX_HEADING_PATTERN = Pattern.compile(
            "^ {0,3}#{1,6}(?!#)(?:[\\t ]+.*)?$");

    private static final Pattern CODE_FENCE_PATTERN = Pattern.compile(
            "^ {0,3}(?:`{3,}|~{3,}).*$", Pattern.MULTILINE);

    private static final Pattern EXTERNAL_RESOURCE_PATTERN = Pattern.compile(
            "(?:https?|s3)://|x-amz-|x-goog-", Pattern.CASE_INSENSITIVE);

    private static final Pattern SIGNED_QUERY_PARAMETER_PATTERN =
            Pattern.compile(
                    "(?<![A-Za-z0-9_-])"
                            + "(?:signature|credential)"
                            + "\\s*(?:=|%3d)",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern INTERNAL_FIELD_PATTERN =
            Pattern.compile(
                    "(?<![A-Za-z0-9])(?:"
                            + "cultivation[_-]?(?:photo[_-]?)?id"
                            + "|mushroom[_-]?id"
                            + "|threshold[_-]?id"
                            + "|sensor[_-]?type[_-]?id"
                            + "|growth[_-]?record[_-]?id"
                            + "|photo[_-]?id"
                            + "|(?:owner[_-]?)?user[_-]?id"
                            + "|presigned[_-]?url"
                            + "|object[_-]?key"
                            + ")(?![A-Za-z0-9])",
                    Pattern.CASE_INSENSITIVE
            );

    /**
     * 일일 피드백의 저장 가능 여부를 검사하고 문자열을 정규화합니다.
     *
     * <p>CRLF와 단독 CR을 LF로 통일하며, 검증을 통과한 경우
     * 전체 문자열 앞뒤 공백을 제거하여 반환합니다. 프롬프트 지시와
     * 무관하게 제목 구조와 민감정보 노출 여부를 코드에서 다시
     * 확인합니다.</p>
     *
     * @param output LLM이 생성한 원본 일일 피드백 문자열
     * @return 줄바꿈과 전체 앞뒤 공백이 정규화된 일일 피드백
     * @throws AiAnalysisFailedException 출력이 저장 가능한 계약을 위반한 경우
     */
    public String validateAndNormalize(String output) {
        if (output == null || output.isBlank()) {
            throw new AiAnalysisFailedException("일일 피드백 응답이 비어 있습니다.");
        }

        String lineEndingNormalized = output
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        String[] linesBeforeStrip = lineEndingNormalized.split("\n", -1);

        if (!REQUIRED_HEADINGS.get(0).equals(linesBeforeStrip[0])) {
            throw new AiAnalysisFailedException("일일 피드백 응답의 첫 번째 제목이 올바르지 않습니다.");
        }

        String normalized = lineEndingNormalized.strip();

        validateForbiddenContent(normalized);

        String[] lines = normalized.split("\n", -1);
        List<Integer> headingLineIndexes = collectAndValidateHeadings(lines);

        validateSectionBodies(lines, headingLineIndexes);

        return normalized;
    }

    private void validateForbiddenContent(String output) {
        if (CODE_FENCE_PATTERN.matcher(output).find()) {
            throw new AiAnalysisFailedException("일일 피드백 응답에 코드 펜스가 포함되어 있습니다.");
        }

        if (EXTERNAL_RESOURCE_PATTERN.matcher(output).find()
                || SIGNED_QUERY_PARAMETER_PATTERN.matcher(output).find()) {
            throw new AiAnalysisFailedException("일일 피드백 응답에 외부 주소 또는 서명정보가 포함되어 있습니다.");
        }

        if (INTERNAL_FIELD_PATTERN.matcher(output).find()) {
            throw new AiAnalysisFailedException("일일 피드백 응답에 노출할 수 없는 내부 필드명이 포함되어 있습니다.");
        }
    }

    private List<Integer> collectAndValidateHeadings(String[] lines) {
        List<String> actualHeadings = new ArrayList<>();
        List<Integer> headingLineIndexes = new ArrayList<>();

        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];

            if (ATX_HEADING_PATTERN.matcher(line).matches()) {
                actualHeadings.add(line);
                headingLineIndexes.add(index);
            }
        }

        if (!actualHeadings.equals(REQUIRED_HEADINGS)) {
            throw new AiAnalysisFailedException("일일 피드백 응답의 Markdown 제목 구조가 올바르지 않습니다.");
        }

        return headingLineIndexes;
    }

    private void validateSectionBodies(String[] lines, List<Integer> headingLineIndexes) {
        for (int sectionIndex = 0; sectionIndex < headingLineIndexes.size(); sectionIndex++) {
            int bodyStart = headingLineIndexes.get(sectionIndex) + 1;
            int bodyEnd = sectionIndex + 1 < headingLineIndexes.size() ? headingLineIndexes.get(sectionIndex + 1) : lines.length;

            boolean hasContent = false;

            for (int lineIndex = bodyStart; lineIndex < bodyEnd; lineIndex++) {
                if (!lines[lineIndex].isBlank()) {
                    hasContent = true;
                    break;
                }
            }

            if (!hasContent) {
                throw new AiAnalysisFailedException("일일 피드백 응답에 본문이 없는 섹션이 있습니다.");
            }
        }
    }
}
