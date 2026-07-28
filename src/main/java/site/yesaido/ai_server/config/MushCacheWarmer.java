package site.yesaido.ai_server.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import site.yesaido.ai_server.dto.MushroomCsvDto;
import site.yesaido.ai_server.reader.MushCsvReader;
import site.yesaido.ai_server.service.MushService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class MushCacheWarmer {
    private final MushService mushService;
    private final MushCsvReader mushCsvReader;
    private final CacheManager cacheManager; // Redis 창고 관리자 주입
    // 버섯 이름과 데이터 함께 묶을 record 생성
    private record MushD(String name, StringBuilder data){}


    @EventListener(ApplicationReadyEvent.class) // 스프링부트 서버가 완전히 켜지고 나면 외부 요청이 없어이 자동으로 이 메서드를 1회 실행
    public void warming() {
        log.info("[Cache Warming Started] CSV 데이터를 읽어 Redis 적재를 시작합니다...");

        try{
            List<MushroomCsvDto> csvDataList = mushCsvReader.readMushroomCsv(); // 데이터 가져오기

            Map<Long, MushD> mushroomDataMap = new HashMap<>();
            for (MushroomCsvDto dto : csvDataList) { // 버섯 이름별로 데이터 뭉치기
                mushroomDataMap.putIfAbsent(dto.mushroomId(), new MushD(dto.mushroomName(), new StringBuilder()));
                mushroomDataMap.get(dto.mushroomId()).data
                        .append("[").append(dto.title()).append("] ")
                        .append(dto.content()).append("\n");
            }
            long startTime = System.currentTimeMillis();
            Cache guideCache = cacheManager.getCache("ai:mushroom");

            // 뭉쳐진 데이터를 하나씩 꺼내서 확인 및 AI 호출
            for (Map.Entry<Long, MushD> entry : mushroomDataMap.entrySet()) {
                Long mushroomId = entry.getKey();
                String mushroomName = entry.getValue().name();
                String combinedData = entry.getValue().data().toString();
                // Redis에서 찾을 Key를 명세서 규격('3:guide")으로 조립
                String cacheKey = mushroomId + ":guide";

                // 조립한 key('3:guide')로 캐시 뒤짐
                if (guideCache != null && guideCache.get(cacheKey) != null) {
                    log.info("이미 Redis에 'mushroomId: {} ({})' 가이드라인이 존재합니다.", mushroomId, mushroomName);
                    continue;
                }

                // 데이터가 없다면 AI에게 생성 요청
                log.info("캐시 없음. '{}' (ID: {}) AI 가이드라인 생성 시작...", mushroomName,  mushroomId);
                mushService.generateRealDataGuide(mushroomId, mushroomName, combinedData);
                // 초당 요청 제한 걸려 서버 뻗는거 방지하기 위해 추가
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            long endTime = System.currentTimeMillis();
            double totalTime = (endTime - startTime) / 1000.0;

            log.info("5종 버섯 데이터 Redis 검증 및 적재 완료!");
            log.info("총 소요 시간: {} 초", totalTime);
        } catch (Exception e) {
            log.error("CSV 파일 읽기 또는 캐시 워밍 중 오류 발생: {}", e.getMessage(), e);
        }
    }
}
