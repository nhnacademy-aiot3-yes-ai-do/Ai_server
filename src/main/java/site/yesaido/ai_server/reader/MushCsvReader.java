package site.yesaido.ai_server.reader;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import site.yesaido.ai_server.dto.MushroomCsvDto;
import site.yesaido.ai_server.exception.CsvLoadException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class MushCsvReader {
    // csv 읽어서 DTO 리스트로 변환시키는 공통 메서드
    public List<MushroomCsvDto> readMushroomCsv(){
        List<MushroomCsvDto> dtoList = new ArrayList<>();

        try(Reader reader = new InputStreamReader( // csv 파일 열기(UTF-8 설정해서 한글 안깨지게)
                new ClassPathResource("mushroom_embeddingdata.csv").getInputStream(), StandardCharsets.UTF_8)){ // ClassPathResource : resources의  csv 파일 찾아 열어주는 기능
            // 제공된 csv 파일 내부에 눈에 안보이는 특수문자(BOM)이 들어있나 헤더 이름을 못찾는 문제가 발생하여 첫줄 무시하고 우리가 직접 입력한 헤더 이름을 덮어 씌움
            Iterable<CSVRecord> records = CSVFormat.DEFAULT.builder()
                    .setHeader("mushroom_id", "mushroom_name_ko", "mushroom_name_en", "mushroom_scientific_name", "title", "content", "embedding")
                    .setSkipHeaderRecord(true) // 첫줄 스킵
                    .build().parse(reader);

            for(CSVRecord csvR : records){ // records에 담긴 csv 데이터 한줄씩 꺼냄
                // 숫자 인덱스로 꺼내오기보다는 헤더명으로 꺼내오게 수정
                Long mushroomId = Long.parseLong(csvR.get("mushroom_id").trim());
                String mushroomName = csvR.get("mushroom_name_ko").trim(); // 버섯 이름
                String title = csvR.get("title").trim(); // 제목(예 : 느타리버섯의 외형적 특징)
                String content = csvR.get("content").trim(); // 본문 내용

                dtoList.add(new MushroomCsvDto(mushroomId, mushroomName, title, content)); // 데이터 모아서 작성한 dto 객체로 포장
            }
        } catch (IOException e) {
            throw new CsvLoadException(e);
        }
        return dtoList;
    }
}
