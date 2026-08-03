package site.yesaido.ai_server.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.yesaido.ai_server.dto.MushroomCsvDto;
import site.yesaido.ai_server.exception.MushDataNotFoundException;
import site.yesaido.ai_server.reader.MushCsvReader;
import static org.assertj.core.api.Assertions.*;
import java.util.List;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MushServiceTest {
    @Mock
    private MushCsvReader mushCsvReader;

    @InjectMocks
    private MushService mushService;

    @Test
    @DisplayName("존재하지 않는 mushroomId로 조회 시 MushDataNotFoundException 발생")
    void generateRealDataGuideNotFoundTest() {
        // given
        Long notExistId = 999L;
        List<MushroomCsvDto> mockCsvData = List.of(
                new MushroomCsvDto(1L, "느타리버섯", "특징", "내용")
        );
        given(mushCsvReader.readMushroomCsv()).willReturn(mockCsvData);

        // when & then
        assertThatThrownBy(() -> mushService.generateRealDataGuide(notExistId))
                .isInstanceOf(MushDataNotFoundException.class)
                .hasMessageContaining("999");
    }
}
