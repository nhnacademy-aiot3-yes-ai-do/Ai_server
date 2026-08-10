package site.yesaido.ai_server.service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.ai.document.Document;
import site.yesaido.ai_server.dto.ai.MushroomCsvDto;
import site.yesaido.ai_server.exception.VectorDbException;
import site.yesaido.ai_server.reader.MushCsvReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MushVectorService {
    // Spring AI가 제공하는 벡터 데이터베이스 연결 통로
    // yml에 pgvector 설정해두면 Spring이 알아서 PostgreSQL과 연결된 객체 넣어줌
    private final VectorStore vectorStore;
    private final MushCsvReader mushCsvReader;

    public String loadCsv(){
        log.info("버섯 CSV 데이터 VectorDB에 적재 시작...");

        List<Document> documents = new ArrayList<>();

        try{
            List<MushroomCsvDto> csvDataList = mushCsvReader.readMushroomCsv();

            for(MushroomCsvDto dto : csvDataList){
                String mushroomName = dto.mushroomName();
                String title = dto.title();
                String content = dto.content();

                String embedding = "[" + title + "]" + content;

                // 나중에 AI가 검색할 때 필터링할 수 있도록 꼬리표 설정
                Map<String, Object> data = Map.of(
                        "domain", "mushroom",
                        "mushroomName", mushroomName,
                        "title", title
                );

                // 중복 적재 방지를 위한 고유 ID 생성 (버섯이름 + 제목 기준)
                String uuid = UUID.nameUUIDFromBytes(
                        (mushroomName + title).getBytes(StandardCharsets.UTF_8)).toString();

                documents.add(new Document(uuid, embedding, data));
            }
            int batchSize = 10;
            for(int i = 0; i < documents.size(); i+= batchSize){
                int end = Math.min(i + batchSize, documents.size());
                List<Document> batch = documents.subList(i, end);
                vectorStore.add(batch); // 10개씩 Ollama(BGE-M3)로 보내서 임베딩
                log.info("{} ~ {} 번째 데이터 적재 완료...", i + 1, end);
            }
            log.info("총 {}개의 버섯 지식이 벡터 DB에 성공적으로 저장되었습니다!", documents.size());
            return "Vector DB 적재 성공! 데이터 수: " + documents.size();
        } catch (Exception e) {
            log.error("벡터 DB 적재 중 오류 발생", e);
            throw new VectorDbException("vector DB 적재에 실패했습니다", e);
        }
    }

}
