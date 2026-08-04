package site.yesaido.ai_server.reader;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.yesaido.ai_server.dto.MushroomCsvDto;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class MushCsvReaderTest {
    @Test
    @DisplayName("CSV 파일 인메모리 파싱 및 로딩 테스트")
    void readCsvTest(){
        MushCsvReader reader = new MushCsvReader();
        reader.initCache();
        List<MushroomCsvDto> dtoList = reader.readMushroomCsv();

        assertThat(dtoList).isNotNull().isNotEmpty();
        assertThat(dtoList.getFirst().mushroomName()).isNotBlank();


    }
}