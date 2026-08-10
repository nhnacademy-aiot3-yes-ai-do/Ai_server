package site.yesaido.ai_server.config;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import site.yesaido.ai_server.service.MushService;

import java.time.Duration;
/*
변경 사항
[기존 방식]
서버 A 켜짐 ──> 1번 실패시 ──> (종료! 2~5번 시도조차 못함)
서버 A, B 동시에 켜짐 ──> 둘 다 동시에 Gemini API 5번 호출! (중복/비용 폭발)

[개선 방식]
서버 A 켜짐 ──> 1번 실패시 ──> 에러 로그만 남기고 2~5번 계속 진행!
서버 A, B 동시에 켜짐 ──> Redis SETNX 락 선점한 1대만 AI 호출, 나머지는 Skip!
 */

/**
 * [force = true 역할]
 * 컴파일 에러 방지 : @RequiredArgsConstructor로 final 필드가 초기화되지 않아 발생하는 문제 방지
 * 강제로 null이나 0 같은 기본값을 채워 넣어, 스프링이 프록시(가짜) 객체를 무사히 만들 수 있게 도와줍니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = {@Autowired}) // 스프링에게 이 생성자로 의존성 주입하라고 명시
@NoArgsConstructor(access = AccessLevel.PROTECTED, force = true)
public class MushCacheWarmer {
    private final MushService mushService;
    // MushService 파라미터로 mushroomId 하나만 받고 스스로 CSV를 읽을 수 있게 변경되어 워머에서 CSV 읽고 HashMap 묶고 데이터 합치던 과정 필요 없어짐
    private final CacheManager cacheManager; // Redis 창고 관리자 주입
    private final StringRedisTemplate stringRedisTemplate; // 분산 락 적용을 위해 추가

    @Async("taskExecutor") // 서버 켜질 때 실행되는데 기본 설정이 동기로 동작이라 AI 요약 작성하는 워머 작업 끝날 때까지(10초) 사용자 접속을 못받아 서버 켜지는데 걸리는 시간이 길어짐
    // K8s 환경에서는 서버 켜지는데 오래걸리면 무한 재시작 시켜버리는 문제가 있어 백그라운드로 작업할 수 있게 비동기로 처리로 변경
    @EventListener(ApplicationReadyEvent.class) // 스프링부트 서버가 완전히 켜지고 나면 외부 요청이 없어이 자동으로 이 메서드를 1회 실행
    public void warming() {
        // @RequiredArgsConstructor(onConstructor_ = {@Autowired})가 있어서 실제로는 의존성 주입이 잘 되지만
        // @NoArgsConstructor(force = true)가 있어서 final 필드 값이 null 들어갈 수 있다는 경고 메시지가 떠서  null 방어 코드 추가
        if (cacheManager == null || stringRedisTemplate == null || mushService == null) {
            log.warn("캐시 워밍 실패: 의존성 주입 객체가 null입니다.");
            return;
        }

        log.info("[Cache Warming Started] 버섯 데이터 Redis 적재를 시작합니다...");
        long startTime = System.currentTimeMillis();
        Cache guideCache = cacheManager.getCache("ai:mushroom");

        // 뭉쳐진 데이터를 하나씩 꺼내서 확인 및 AI 호출
        for (long mushroomId = 1L; mushroomId <= 5L; mushroomId++) { // 버섯 5종류 고정이니 명시적인 반복문 사용
            try{
                // Redis에서 찾을 Key를 명세서 규격('3:guide")으로 조립
                String cacheKey = mushroomId + ":guide";

                if (guideCache != null && guideCache.get(cacheKey) != null) {
                    log.info("이미 Redis에 'mushroomId: {}' 가이드라인이 존재합니다.", mushroomId);
                    continue;
                }
                /*
                수정사항 : [분산 락 적용]
                버섯 가이드는 모든 사용자가 공통으로 조회하는 도감(참조) 데이터이므로,
                다중 서버(K8s 파드) 환경에서 동시에 API를 중복 호출하는 비용 낭비를 막기 위해
                Redis SETNX(SET if Not eXists = 키가 존재하지 않을 때만 저장해라)를 이용해 선착순 1대의 인스턴스만 AI 요약을 생성하도록 제어
                 */
                String lockKey = "ai:mushroom:lock:" + mushroomId;
                Boolean isLocked = stringRedisTemplate.opsForValue()
                        .setIfAbsent(lockKey, "LOCKED", Duration.ofSeconds(120)); // AI 요약 시키면 90초 정도 걸린걸로 기억해서 넉넉하게 2분 설정

                if (Boolean.TRUE.equals(isLocked)) {
                    try {
                        // 캐시가 없다면 서비스에게 ID 던져서 만들게 시킴
                        log.info("캐시 없음. 락 획득 (ID: {}) AI 가이드라인 생성 시작...", mushroomId);
                        mushService.generateRealDataGuide(mushroomId);
                    } finally {
                        stringRedisTemplate.delete(lockKey); // 작업 완료 후 락 해제
                    }
                } else {
                    log.info("다른 서버 인스턴스가 ID: {} 생성 작업을 선점했습니다. (Skip)", mushroomId);
                }

                apiDelay();
            } catch (Exception e) {
                log.error("캐시 워밍 중 오류 발생: {}", e.getMessage(), e);
            }
        }
        long endTime = System.currentTimeMillis();
        double totalTime = (endTime - startTime) / 1000.0;

        log.info("5종 버섯 데이터 Redis 검증 및 적재 완료! 총 소요 시간: {} 초", totalTime);
    }
    private void apiDelay(){ // 초당 요청 제한 걸려 서버 뻗는거 방지하기 위해 추가
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
