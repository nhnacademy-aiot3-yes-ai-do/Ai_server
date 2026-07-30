package site.yesaido.ai_server.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import site.yesaido.ai_server.service.MushService;

@Slf4j
@Component
@RequiredArgsConstructor
public class MushCacheWarmer {
    private final MushService mushService;
    // MushService 파라미터로 mushroomId 하나만 받고 스스로 CSV를 읽을 수 있게 변경되어 워머에서 CSV 읽고 HashMap 묶고 데이터 합치던 과정 필요 없어짐
    private final CacheManager cacheManager; // Redis 창고 관리자 주입
    @Async // 서버 켜질 때 실행되는데 기본 설정이 동기로 동작이라 AI 요약 작성하는 워머 작업 끝날 때까지(10초) 사용자 접속을 못받아 서버 켜지는데 걸리는 시간이 길어짐
    // K8s 환경에서는 서버 켜지는데 오래걸리면 무한 재시작 시켜버리는 문제가 있어 백그라운드로 작업할 수 있게 비동기로 처리로 변경
    @EventListener(ApplicationReadyEvent.class) // 스프링부트 서버가 완전히 켜지고 나면 외부 요청이 없어이 자동으로 이 메서드를 1회 실행
    public void warming() {
        log.info("[Cache Warming Started] 버섯 데이터 Redis 적재를 시작합니다...");
        long startTime = System.currentTimeMillis();
        Cache guideCache = cacheManager.getCache("ai:mushroom");

        try{
            // 뭉쳐진 데이터를 하나씩 꺼내서 확인 및 AI 호출
            for (long mushroomId = 1L; mushroomId <= 5L; mushroomId++) { // 버섯 5종류 고정이니 명시적인 반복문 사용
                // Redis에서 찾을 Key를 명세서 규격('3:guide")으로 조립
                String cacheKey = mushroomId + ":guide";

                // 캐시가 이미 존재하면 패스
                if (guideCache != null && guideCache.get(cacheKey) != null) {
                    log.info("이미 Redis에 'mushroomId: {}' 가이드라인이 존재합니다.", mushroomId);
                    continue;
                }

                // 캐시가 없다면 서비스에게 ID 던져서 만들게 시킴
                log.info("캐시 없음. (ID: {}) AI 가이드라인 생성 시작...",  mushroomId);
                mushService.generateRealDataGuide(mushroomId);

                apiDelay();

            }

            long endTime = System.currentTimeMillis();
            double totalTime = (endTime - startTime) / 1000.0;

            log.info("5종 버섯 데이터 Redis 검증 및 적재 완료! 총 소요 시간: {} 초", totalTime);
        } catch (Exception e) {
            log.error("캐시 워밍 중 오류 발생: {}", e.getMessage(), e);
        }
    }
    private void apiDelay(){ // 초당 요청 제한 걸려 서버 뻗는거 방지하기 위해 추가
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
