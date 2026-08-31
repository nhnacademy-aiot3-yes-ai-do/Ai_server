package site.yesaido.ai_server.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.dao.DataRetrievalFailureException;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MushroomKnowledgeToolTest {
    @Mock
    private VectorStore vectorStore;

    @InjectMocks
    private MushroomKnowledgeTool mushroomKnowledgeTool;

    @Test
    @DisplayName("searchMushroomKnowledge - 검색 결과가 있을 때 문서 제목과 내용 포맷팅 반환")
    void searchMushroomKnowledge_success() {
        Document doc1 = new Document("느타리버섯은 면역력에 좋습니다.", Map.of("title", "느타리 효능"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc1));

        String result = mushroomKnowledgeTool.searchMushroomKnowledge("느타리 효능");

        assertThat(result).contains("[출처/제목: 느타리 효능]", "느타리버섯은 면역력에 좋습니다.");
    }

    @Test
    @DisplayName("searchMushroomKnowledge - 검색 결과가 없을 때 안내 문구 반환")
    void searchMushroomKnowledge_empty() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(Collections.emptyList());

        String result = mushroomKnowledgeTool.searchMushroomKnowledge("알 수 없는 버섯");

        assertThat(result).contains("관련된 버섯 전문 지식 데이터를 찾지 못했습니다.");
    }

    @Test
    @DisplayName("searchMushroomKnowledge - DataAccessException 발생 시 예외 문구 반환")
    void searchMushroomKnowledge_dataAccessException() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new DataRetrievalFailureException("PGVector Down"));

        String result = mushroomKnowledgeTool.searchMushroomKnowledge("버섯 질병");

        assertThat(result).contains("버섯 지식 데이터베이스(PGVector)를 조회하는 중 오류가 발생했습니다.");
    }

    @Test
    @DisplayName("searchMushroomKnowledge - 일반 Exception 발생 시 예외 문구 반환")
    void searchMushroomKnowledge_genericException() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new RuntimeException("Unknown Error"));

        String result = mushroomKnowledgeTool.searchMushroomKnowledge("버섯 질병");

        assertThat(result).contains("지식 검색 중 일시적인 시스템 오류가 발생했습니다.");
    }
}
