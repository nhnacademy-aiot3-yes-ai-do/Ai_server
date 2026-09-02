package site.yesaido.ai_server.config;

import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import reactor.core.publisher.Flux;
import site.yesaido.ai_server.exception.GeminiAllKeysExhaustedException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

@Slf4j
@NullMarked // ChatModel null 허용 x라서 추가
public class RotatingApiKey implements ChatModel{
    private final List<GoogleGenAiChatModel> models; // api 키 읽어서 키마다 각각의 AI 클라이언트 만들어서 리스트에 대기
    private final GoogleGenAiChatOptions defaultOptions;
    private final AtomicInteger requestCounter = new AtomicInteger(0); // 동시 요청 들어올 때마다 번호 1씩 올려서 키에 트래픽 분배함(라운드로빈)

    // 429 발생한 키의 인덱스와 쿨다운 만료 시각 기록
    private final Map<Integer, Instant> exhaustedUntil = new ConcurrentHashMap<>();

    // 키별 성공 호출 횟수 트래커
    private final Map<Integer, LongAdder> successCounters = new ConcurrentHashMap<>();

    // Google API Quota 리셋 기준 타임존
    private static final ZoneId PACIFIC_ZONE = ZoneId.of("America/Los_Angeles");

    public RotatingApiKey(String apiKeysString, GoogleGenAiChatOptions defaultOptions) {
        this.defaultOptions = defaultOptions;
        List<String> apiKeys = Arrays.stream(apiKeysString.split(","))
                .map(String::trim)
                .filter(key -> !key.isEmpty())
                .distinct() // 중복 키 자동 제거
                .toList();
        if (apiKeys.isEmpty()) {
            throw new IllegalArgumentException("GEMINI_API_KEY가 비어있습니다.");
        }

        log.info("Gemini API Key 로테이션 풀 초기화 완료: 총 {}개의 API Key 등록", apiKeys.size());

        this.models = apiKeys.stream()
                .map(key -> GoogleGenAiChatModel.builder()
                        .genAiClient(Client.builder().apiKey(key).build())
                        .options(defaultOptions)
                        .build())
                .toList();

        for (int i = 0; i < this.models.size(); i++) {
            this.successCounters.put(i, new LongAdder());
        }
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        Prompt safePrompt = ensureGoogleOptions(prompt);
        int maxAttempts = models.size();
        int attempts = 0;

        while (attempts < maxAttempts) {
            int keyIndex = pickNextHealthyKeyIndex();
            GoogleGenAiChatModel model = models.get(keyIndex);

            try {
                ChatResponse response = model.call(safePrompt);
                incrementSuccess(keyIndex);
                return response;
            } catch (RuntimeException e) {
                // 429 할당량 초과인지 확인
                if (isQuotaExceededException(e)) {
                    markKeyAsExhausted(keyIndex, e);
                    attempts++;
                    log.warn("[API Key #{}] 429 일일 할당량 초과 감지! 다음 구글 쿼터 리셋(PT 자정)까지 쿨다운 처리하고 다음 키로 재시도합니다. (시도 {}/{})", keyIndex + 1, attempts, maxAttempts);
                } else {
                    // 429가 아닌 다른 오류 발생시 재시도 없이 즉시 전파
                    log.error("[API Key #{}] 429 이외의 일반 AI 오류 발생: {}", keyIndex + 1, e.getMessage());
                    throw e;
                }
            }
        }
        log.error("등록된 모든 Gemini API Key({}개)의 일일 할당량이 소진되었습니다!", maxAttempts);
        throw new GeminiAllKeysExhaustedException(maxAttempts, calculateNextDailyResetTime());
    }

    @Override
    public GoogleGenAiChatOptions getOptions() {
        return this.defaultOptions;
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        Prompt safePrompt = ensureGoogleOptions(prompt);
        return streamWithRetry(safePrompt, 0, models.size());
    }

    private Prompt ensureGoogleOptions(Prompt prompt) {
        ChatOptions options = prompt.getOptions();
        log.info("[RotatingApiKey.ensureGoogleOptions] 원본 options 클래스: {}, 내용: {}",
                options != null ? options.getClass().getName() : "null", options);

        if (options instanceof GoogleGenAiChatOptions googleGenAiChatOptions) {
            log.info("[RotatingApiKey.ensureGoogleOptions] 이미 GoogleGenAiChatOptions입니다. 도구(toolCallbacks) 수: {}",
                    googleGenAiChatOptions.getToolCallbacks() != null ? googleGenAiChatOptions.getToolCallbacks().size() : 0);
            return prompt;
        }

        GoogleGenAiChatOptions.Builder builder = this.defaultOptions.mutate();
        if (options != null) {
            builder.combineWith(options.mutate());
        }

        GoogleGenAiChatOptions builtOptions = builder.build();
        log.info("[RotatingApiKey.ensureGoogleOptions] 변환 완료된 GoogleGenAiChatOptions 내 도구(toolCallbacks) 수: {}",
                builtOptions.getToolCallbacks() != null ? builtOptions.getToolCallbacks().size() : 0);

        return new Prompt(prompt.getInstructions(), builtOptions);
    }

    // steam 호출 중 429 발생 시 다른 키로 바꿔치기
    private Flux<ChatResponse> streamWithRetry(Prompt prompt, int attempts, int maxAttempts) {
        if (attempts >= maxAttempts) {
            return Flux.error(new GeminiAllKeysExhaustedException(maxAttempts, calculateNextDailyResetTime()));
        }

        int keyIndex = pickNextHealthyKeyIndex();
        GoogleGenAiChatModel model = models.get(keyIndex);

        return model.stream(prompt)
                .doOnComplete(() -> incrementSuccess(keyIndex))
                .onErrorResume(e -> {
                    if (isQuotaExceededException(e)) {
                        markKeyAsExhausted(keyIndex, e);
                        log.warn("[stream API Key #{}] 429 감지 -> 다음 키로 스트림 전환 재시도 (시도 {}/{})",
                                keyIndex + 1, attempts + 1, maxAttempts);
                        return streamWithRetry(prompt, attempts + 1, maxAttempts);
                    }
                    return Flux.error(e);
                });
    }

    // 동작하는 키 라운드로빈으로 탐색 후 모든 키가 다 사용 불가면 복구 시간 띄워주고 에러 발생시킴
    private int pickNextHealthyKeyIndex() {
        cleanupExpiredCooldowns();
        int totalKeys = models.size();
        int start = Math.floorMod(requestCounter.getAndIncrement(), totalKeys);

        // 쿨다운에 걸리지 않은 정상 키 탐색
        for (int i = 0; i < totalKeys; i++) {
            int candidate = (start + i) % totalKeys;
            if (!exhaustedUntil.containsKey(candidate)) {
                return candidate;
            }
        }

        // 모든 키가 쿨다운 상태인 경우: 가장 빠른 복구 시각(PT 자정)과 함께 즉시 예외 발생
        Instant earliest = exhaustedUntil.values().stream()
                .min(Instant::compareTo)
                .orElse(calculateNextDailyResetTime());
        throw new GeminiAllKeysExhaustedException(totalKeys, earliest);
    }

    private void markKeyAsExhausted(int keyIndex, Throwable throwable) {
        if (isDailyLimitExceeded(throwable)) {
            exhaustedUntil.put(keyIndex, calculateNextDailyResetTime());
            log.warn("[API Key #{}] 일일 쿼터 소진 -> PT 자정까지 쿨다운", keyIndex + 1);
            }
        else {
            exhaustedUntil.put(keyIndex, Instant.now().plusSeconds(60));
            log.warn("[API Key #{}] 일시적 속도 제한(RPM) 감지 -> 1분간 일시 쿨다운", keyIndex + 1);
        }
    }
    private boolean isDailyLimitExceeded(Throwable throwable) {
        String msg = throwable.getMessage();
        if (msg == null) {
            return false;
        }
        String lowerMsg = msg.toLowerCase();
        return lowerMsg.contains("perday") || lowerMsg.contains("per day") || lowerMsg.contains("daily") || lowerMsg.contains("limit: 20");
    }

    // api key 리셋 시간 계산
    private Instant calculateNextDailyResetTime() {
        ZonedDateTime nowPt = ZonedDateTime.now(PACIFIC_ZONE);
        ZonedDateTime nextMidnightPt = nowPt.toLocalDate().plusDays(1).atStartOfDay(PACIFIC_ZONE);
        return nextMidnightPt.toInstant();
    }

    private void cleanupExpiredCooldowns() {
        Instant now = Instant.now();
        exhaustedUntil.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
    }

    private void incrementSuccess(int keyIndex) {
        successCounters.computeIfAbsent(keyIndex, k -> new LongAdder()).increment();
    }

    // 예외가 429 Quota Exceeded 또는 RESOURCE_EXHAUSTED인지 정밀 판별
    private boolean isQuotaExceededException(Throwable throwable) {
        Throwable curr = throwable;
        while (curr != null) {
            if (curr instanceof ApiException apiEx && (apiEx.code() == 429 || "RESOURCE_EXHAUSTED".equalsIgnoreCase(apiEx.status()))) {
                return true;
            }
            String msg = curr.getMessage();
            if (msg != null && (msg.contains("429") || msg.contains("Quota exceeded") || msg.contains("RESOURCE_EXHAUSTED") || msg.contains("limit: 20"))) {
            return true;
        }
        curr = curr.getCause();
    }
            return false;
    }
}
