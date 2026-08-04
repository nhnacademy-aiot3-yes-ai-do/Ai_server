package site.yesaido.ai_server.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;
import site.yesaido.ai_server.dto.MushroomCsvDto;
import site.yesaido.ai_server.exception.VectorDbException;
import site.yesaido.ai_server.reader.MushCsvReader;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MushVectorServiceTest {
    @Mock
    private VectorStore vectorStore;

    @Mock
    private MushCsvReader mushCsvReader;

    @InjectMocks
    private MushVectorService mushVectorService;

    @Test
    @DisplayName("CSV 데이터 읽어서 Vector DB 적재 성공 검증")
    void loadCsvSuccessTest() {
        // given
        List<MushroomCsvDto> mockCsvData = List.of(
                new MushroomCsvDto(1L, "느타리버섯", "외형", "갓이 넓다."),
                new MushroomCsvDto(2L, "표고버섯", "특징", "향이 강하다.")
        );
        given(mushCsvReader.readMushroomCsv()).willReturn(mockCsvData);

        // when
        String result = mushVectorService.loadCsv();

        // then
        assertThat(result).contains("Vector DB 적재 성공! 데이터 수: 2");
        verify(vectorStore).add(anyList()); // vectorStore.add() 호출 여부 확인
    }

    @Test
    @DisplayName("CSV 읽기 실패 시 VectorDbException 예외 발생 검증")
    void loadCsvFailureTest() {
        // given
        given(mushCsvReader.readMushroomCsv()).willThrow(new RuntimeException("CSV 오류"));

        // when & then
        assertThatThrownBy(() -> mushVectorService.loadCsv())
                .isInstanceOf(VectorDbException.class)
                .hasMessageContaining("vector DB 적재에 실패했습니다");
    }
}
