package site.yesaido.ai_server.reader;

import jakarta.annotation.PostConstruct;
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
import java.util.Collections;
import java.util.List;

/**
 * 개선 사항
 * readMushroomCsv() 호출될 때마다 ClassPathResource를 통해 csv 열고 파싱해서
 * 캐시 워밍이나 AI 요약 요청 올 때마다 파일 읽기 반복으로 불필요한 I/O 비용이 발생
 * Spring 싱글톤 빈이므로 빈 생성 직후(@PostConstruct) 1번만 CSV 읽어 List에 캐싱해두고 이후에는 파일 I/O 없이 저장된 데이터만 반환하게 개선
 */
@Slf4j
@Component
public class MushCsvReader {
    // 메모리에 캐싱해둘 불변 리스트
    private List<MushroomCsvDto> cachedDtoList = Collections.emptyList();
    @PostConstruct // 서버 켜질 때 1번만 실행되어 CSV 메모리에 로딩
    public void initCache(){
        log.info("Mushroom CSV 데이터 최초 인메모리 로딩 시작...");
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
            // 불변(Unmodifiable) 리스트로 캐시 저장 (반복문 완료 후 1회 수행)
            this.cachedDtoList = List.copyOf(dtoList);
            log.info("Mushroom CSV 데이터 {}건 성공적으로 메모리에 캐싱되었습니다.", cachedDtoList.size());
        } catch (IOException e) {
            log.error("CSV 파일 로딩 실패", e);
            throw new CsvLoadException(e);
        }
    }
    public List<MushroomCsvDto> readMushroomCsv(){ // 파일 I/O 없이 메모리에 캐싱된 리스트 즉시 반환
        return cachedDtoList;
    }
}
