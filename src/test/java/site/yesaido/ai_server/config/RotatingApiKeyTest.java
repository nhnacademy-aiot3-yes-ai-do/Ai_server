package site.yesaido.ai_server.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import site.yesaido.ai_server.exception.GeminiAllKeysExhaustedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class RotatingApiKeyTest {

    @Test
    @DisplayName("생성자: 빈 키 목록 전달 시 IllegalArgumentException")
    void constructor_emptyKeys() {
        GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder().build();

        assertThatThrownBy(() -> new RotatingApiKey("", options))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new RotatingApiKey("  ,  ", options))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("call: 정상 호출 시 성공 응답 반환")
    void call_success() {
        GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder().build();
        RotatingApiKey rotating = new RotatingApiKey("key1, key2", options);

        GoogleGenAiChatModel mockModel1 = mock(GoogleGenAiChatModel.class);
        GoogleGenAiChatModel mockModel2 = mock(GoogleGenAiChatModel.class);
        ChatResponse mockResponse = mock(ChatResponse.class);

        ReflectionTestUtils.setField(rotating, "models", List.of(mockModel1, mockModel2));
        given(mockModel1.call(any(Prompt.class))).willReturn(mockResponse);

        ChatResponse response = rotating.call(new Prompt("안녕"));

        assertThat(response).isEqualTo(mockResponse);
    }

    @Test
    @DisplayName("call: 1번 키 429 Quota Exceeded 발생 시 2번 키로 자동 로테이션 재시도 성공")
    void call_quotaExceeded_retrySuccess() {
        GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder().build();
        RotatingApiKey rotating = new RotatingApiKey("key1, key2", options);

        GoogleGenAiChatModel mockModel1 = mock(GoogleGenAiChatModel.class);
        GoogleGenAiChatModel mockModel2 = mock(GoogleGenAiChatModel.class);
        ChatResponse mockResponse = mock(ChatResponse.class);

        ReflectionTestUtils.setField(rotating, "models", List.of(mockModel1, mockModel2));

        // 1번 키는 429 일일 한도 초과 에러, 2번 키는 정상 성공
        given(mockModel1.call(any(Prompt.class)))
                .willThrow(new RuntimeException("429 Quota exceeded for quota metric 'Queries' and limit 'RESOURCE_EXHAUSTED' daily limit: 20"));
        given(mockModel2.call(any(Prompt.class)))
                .willReturn(mockResponse);

        ChatResponse response = rotating.call(new Prompt("안녕"));

        assertThat(response).isEqualTo(mockResponse);
    }

    @Test
    @DisplayName("call: 모든 키의 일일 할당량이 소진되면 GeminiAllKeysExhaustedException 발생")
    void call_allKeysExhausted() {
        GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder().build();
        RotatingApiKey rotating = new RotatingApiKey("key1, key2", options);

        GoogleGenAiChatModel mockModel1 = mock(GoogleGenAiChatModel.class);
        GoogleGenAiChatModel mockModel2 = mock(GoogleGenAiChatModel.class);

        ReflectionTestUtils.setField(rotating, "models", List.of(mockModel1, mockModel2));

        given(mockModel1.call(any(Prompt.class)))
                .willThrow(new RuntimeException("429 Quota exceeded daily"));
        given(mockModel2.call(any(Prompt.class)))
                .willThrow(new RuntimeException("429 RESOURCE_EXHAUSTED"));

        Prompt prompt = new Prompt("안녕");
        assertThatThrownBy(() -> rotating.call(prompt))
                .isInstanceOf(GeminiAllKeysExhaustedException.class);
    }

    @Test
    @DisplayName("call: 429가 아닌 일반 런타임 예외 발생 시 재시도 없이 즉시 전파")
    void call_nonQuotaException_throwsImmediately() {
        GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder().build();
        RotatingApiKey rotating = new RotatingApiKey("key1, key2", options);

        GoogleGenAiChatModel mockModel1 = mock(GoogleGenAiChatModel.class);
        GoogleGenAiChatModel mockModel2 = mock(GoogleGenAiChatModel.class);

        ReflectionTestUtils.setField(rotating, "models", List.of(mockModel1, mockModel2));

        given(mockModel1.call(any(Prompt.class)))
                .willThrow(new IllegalArgumentException("잘못된 프롬프트 파라미터"));

        Prompt prompt = new Prompt("안녕");
        assertThatThrownBy(() -> rotating.call(prompt))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("stream: 정상 스트리밍 응답 반환")
    void stream_success() {
        GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder().build();
        RotatingApiKey rotating = new RotatingApiKey("key1, key2", options);

        GoogleGenAiChatModel mockModel1 = mock(GoogleGenAiChatModel.class);
        GoogleGenAiChatModel mockModel2 = mock(GoogleGenAiChatModel.class);
        ChatResponse mockResponse = mock(ChatResponse.class);

        ReflectionTestUtils.setField(rotating, "models", List.of(mockModel1, mockModel2));
        given(mockModel1.stream(any(Prompt.class))).willReturn(Flux.just(mockResponse));

        List<ChatResponse> responses = rotating.stream(new Prompt("안녕")).collectList().block();

        assertThat(responses).containsExactly(mockResponse);
    }

    @Test
    @DisplayName("stream: 1번 키 429 발생 시 2번 키로 스트림 전환 재시도 성공")
    void stream_quotaExceeded_retrySuccess() {
        GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder().build();
        RotatingApiKey rotating = new RotatingApiKey("key1, key2", options);

        GoogleGenAiChatModel mockModel1 = mock(GoogleGenAiChatModel.class);
        GoogleGenAiChatModel mockModel2 = mock(GoogleGenAiChatModel.class);
        ChatResponse mockResponse = mock(ChatResponse.class);

        ReflectionTestUtils.setField(rotating, "models", List.of(mockModel1, mockModel2));

        given(mockModel1.stream(any(Prompt.class)))
                .willReturn(Flux.error(new RuntimeException("429 Quota exceeded")));
        given(mockModel2.stream(any(Prompt.class)))
                .willReturn(Flux.just(mockResponse));

        List<ChatResponse> responses = rotating.stream(new Prompt("안녕")).collectList().block();

        assertThat(responses).containsExactly(mockResponse);
    }
}
