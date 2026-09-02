package site.yesaido.ai_server.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.yesaido.ai_server.exception.DailyFeedbackProcessingException.Reason;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AdditionalExceptionsTest {

    @Test
    @DisplayName("GeminiAllKeysExhaustedException 생성 및 메시지 검증")
    void geminiAllKeysExhaustedException() {
        Instant resetTime = Instant.now();
        GeminiAllKeysExhaustedException ex = new GeminiAllKeysExhaustedException(5, resetTime);

        assertThat(ex.getMessage()).contains("5개");
    }

    @Test
    @DisplayName("DailyFeedbackProcessingException 생성 검증")
    void dailyFeedbackProcessingException() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        DailyFeedbackProcessingException ex = new DailyFeedbackProcessingException(
                1L, date, Reason.CONTEXT_SNAPSHOT_SERIALIZATION_FAILED
        );

        assertThat(ex.getMessage()).isEqualTo("일일 피드백을 처리하지 못했습니다.");
    }
}
