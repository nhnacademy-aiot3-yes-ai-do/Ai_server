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

        MushroomCsvDto expectedFirstDto = new MushroomCsvDto(
                1L,
                "느타리버섯",
                "느타리버섯의 외형적 특징",
                "느타리버섯의 갓은 일반적으로 5~15cm 크기이다. 처음에는 반구형이지만 성장하면서 콩팥형, 조개형 또는 깔때기형으로 변한다. 갓의 색은 초기에는 흑색 또는 회청색을 띠며, 이후 흑색, 회색, 회갈색, 회백색 또는 백색 등으로 다양하게 나타난다. 조직은 두껍고 탄력성이 있다."
            );

        List<MushroomCsvDto> dtoList = reader.readMushroomCsv();

        assertThat(dtoList).isNotNull().isNotEmpty();
        assertThat(dtoList.getFirst())
                .usingRecursiveComparison()
                .isEqualTo(expectedFirstDto);


    }
}