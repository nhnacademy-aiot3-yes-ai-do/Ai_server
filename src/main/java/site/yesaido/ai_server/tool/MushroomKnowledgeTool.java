package site.yesaido.ai_server.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class MushroomKnowledgeTool {
    private final VectorStore vectorStore;

    @Tool(description = "버섯의 효능, 영양 성분, 병충해(곰팡이/갈변 등) 대처법, 보관법, 요리 레시피 등 농업 전문 지식을 PGVector에서 검색합니다.")
    public String searchMushroomKnowledge(
            @ToolParam(description = "검색할 질문 키워드 (예: '느타리버섯 효능', '갈색 반점 치료법')") String query) {

        log.info("🔍 [Tool 호출] 버섯 지식 RAG 검색 시작: '{}'", query);

        try {
            // PGVector에서 코사인 유사도가 가장 높은 상위 3개 문서 검색
            List<Document> documents = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(3)
                            .similarityThreshold(0.6) // 유사도 0.6 이상만 선별
                            .build()
            );

            if (documents.isEmpty()) {
                return "관련된 버섯 전문 지식 데이터를 찾지 못했습니다.";
            }

            return documents.stream()
                    .map(doc -> String.format("[출처/제목: %s]%n%s",
                            doc.getMetadata().getOrDefault("title", "농업 지식"),
                            doc.getText()))
                    .collect(Collectors.joining("\n\n"));

        } catch (Exception e) {
            log.error("버섯 지식 RAG 검색 중 오류 발생: {}", e.getMessage());
            return "지식 검색 중 일시적인 오류가 발생했습니다: " + e.getMessage();
        }
    }
}
