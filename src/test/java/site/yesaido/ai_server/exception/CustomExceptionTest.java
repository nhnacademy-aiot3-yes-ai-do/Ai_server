package site.yesaido.ai_server.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CustomExceptionTest {
    @Test
    @DisplayName("UnauthorizedAccessException 발생 메시지 검증")
    void unauthorizedAccessExceptionTest(){
        UnauthorizedAccessException e = new UnauthorizedAccessException();
        assertThat(e.getMessage()).isEqualTo("접근 권한이 없습니다.");
    }

    @Test
    @DisplayName("MushDataNotFoundException 발생 시 파라미터가 포함된 메시지 검증")
    void mushDataNotFoundExceptionTest() {
        Long mushroomId = 99L;
        MushDataNotFoundException exception = new MushDataNotFoundException(mushroomId);

        assertThat(exception.getMessage()).contains("99");
        assertThat(exception.getMessage()).contains("해당 ID의 버섯 데이터가 존재하지 않습니다.");
    }

    @Test
    @DisplayName("CsvLoadException 발생 시 Cause 예외 전달 검증")
    void csvLoadExceptionTest() {
        RuntimeException cause = new RuntimeException("파일 읽기 에러 원인");
        CsvLoadException exception = new CsvLoadException(cause);

        assertThat(exception.getMessage()).isEqualTo("CSV 데이터를 읽어오는 중 문제가 발생했습니다.");
        assertThat(exception.getCause()).isEqualTo(cause);
    }
}
