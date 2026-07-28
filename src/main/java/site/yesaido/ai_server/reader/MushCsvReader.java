package site.yesaido.ai_server.reader;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import site.yesaido.ai_server.dto.MushroomCsvDto;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class MushCsvReader {
    // csv 읽어서 DTO 리스트로 변환시키는 공통 메서드
    public List<MushroomCsvDto> readMushroomCsv() throws Exception {
        List<MushroomCsvDto> dtoList = new ArrayList<>();

        try(Reader reader = new InputStreamReader( // csv 파일 열기(UTF-8 설정해서 한글 안깨지게)
                new ClassPathResource("mushroom_embeddingdata.csv").getInputStream(), StandardCharsets.UTF_8)){

            Iterable<CSVRecord> records = CSVFormat.DEFAULT.builder() // csv 읽기
                    .setHeader() // CSV 첫줄 헤더 이름으로 인식하도록 설정
                    .setSkipHeaderRecord(true) // 첫줄 건너뛰기
                    .build()
                    .parse(reader);
            // csvR 하나가 엑셀 한줄
            for(CSVRecord csvR : records){ // 데이터 포장하기 Document(문서 객체 <- AI가 이해할 수 있음)
                // 숫자 인덱스로 꺼내오기보다는 헤더명으로 꺼내오게 수정
                Long mushroomId = Long.parseLong(csvR.get("mushroom_id").trim());
                String mushroomName = csvR.get("mushroom_name_ko").trim(); // 버섯 이름
                String title = csvR.get("title").trim(); // 제목(예 : 느타리버섯의 외형적 특징)
                String content = csvR.get("content").trim(); // 본문 내용

                dtoList.add(new MushroomCsvDto(mushroomId, mushroomName, title, content)); // Document 생성 시 고유 ID 부여
            }
        }
        return dtoList;
    }
}
