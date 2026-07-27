package site.yesaido.ai_server.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import site.yesaido.ai_server.service.MushService;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class MushCacheWarmer {
    private final MushService mushService;

    @EventListener(ApplicationReadyEvent.class) // 스프링부트 서버가 완전히 켜지고 나면 외부 요청이 없어이 자동으로 이 메서드를 1회 실행
    public void warming() {
        log.info("[Cache Warming Started] CSV 데이터를 읽어 Redis 적재를 시작합니다...");

        Map<String, StringBuilder> mushroomDataMap = new HashMap<>();

        // CSV 파일을 UTF-8 인코딩으로 안전하게 엽니다.
        try (Reader reader = new InputStreamReader(
                new ClassPathResource("mushroom_embeddingdata.csv").getInputStream(), StandardCharsets.UTF_8)) {

            Iterable<CSVRecord> records = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .build()
                    .parse(reader);

            for (CSVRecord record : records) {
                // 💡 CSV 컬럼 인덱스 (0부터 시작)
                // 1번 인덱스: mushroom_name_ko (예: 느타리버섯)
                // 4번 인덱스: title (예: 느타리버섯의 재배 특징)
                // 5번 인덱스: content (예: 느타리버섯은 국내에서...)
                String mushroomName = record.get(1).trim();
                String title = record.get(4).trim();
                String content = record.get(5).trim();

                // 버섯 이름으로 바구니를 만들고, "[소제목] 내용" 형태로 예쁘게 이어 붙입니다.
                mushroomDataMap.putIfAbsent(mushroomName, new StringBuilder());
                mushroomDataMap.get(mushroomName).append("[").append(title).append("] ").append(content).append("\n");
            }

            long startTime = System.currentTimeMillis();

            for (Map.Entry<String, StringBuilder> entry : mushroomDataMap.entrySet()) {
                String mushroomName = entry.getKey();
                String combinedData = entry.getValue().toString();

                log.info("⏳ 데이터 뭉치기 완료: '{}' AI 가이드라인 생성 시작...", mushroomName);
                mushService.generateRealDataGuide(mushroomName, combinedData);
            }

            // 💡 2. 모든 AI 호출이 끝나면 타이머를 종료하고 계산합니다.
            long endTime = System.currentTimeMillis();
            double timeElapsed = (endTime - startTime) / 1000.0; // 밀리초를 초 단위로 변환

            // 💡 3. 총 소요 시간을 예쁘게 출력합니다.
            log.info("🎉 [Cache Warming Success] 5종 버섯 데이터 Redis 적재 완료!");
            log.info("⏱️ [Performance] 총 소요 시간: {} 초", timeElapsed);

        } catch (Exception e) {
            log.error("❌ CSV 파일 읽기 또는 캐시 워밍 중 오류 발생: {}", e.getMessage(), e);
        }
    }
}
