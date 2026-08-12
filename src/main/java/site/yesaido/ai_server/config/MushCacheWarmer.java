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
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import site.yesaido.ai_server.service.MushService;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
/*
서버 A, B 동시에 켜짐
-> Redis 락을 선점한 인스턴스가 우선 AI 호출
-> 다른 인스턴스의 중복 생성을 줄임
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
    private final CacheManager cacheManager; // Redis 창고 관리자 주입
    private final StringRedisTemplate stringRedisTemplate; // 분산 락 적용을 위해 추가
    /*
    if 대신 Redis Lua 스크립트 사용 이유
    Lua를 쓰면 Redis 서버가 스크립트를 실행하는 동안 다른 어떠 명령도 중간에 끼어들지 못하게 하여 원자성이 보장됨
    */
    // 내 UUID일 때만 삭제
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT =
            new DefaultRedisScript<>(
                    "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                            "return redis.call('del', KEYS[1]) " +
                            "else return 0 end",
                    Long.class
            );
    // 내 UUID일 때만 TTL 갱신
    private static final DefaultRedisScript<Long> RENEW_LOCK_SCRIPT =
            new DefaultRedisScript<>(
                    "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                            "return redis.call('expire', KEYS[1], ARGV[2]) " +
                            "else return 0 end",
                    Long.class
            );

    @Async("taskExecutor") // K8s 환경에서는 서버 켜지는데 오래걸리면 무한 재시작 시켜버리는 문제가 있어 백그라운드로 작업할 수 있게 비동기로 처리로 변경
    @EventListener(ApplicationReadyEvent.class) // 스프링부트 서버가 완전히 켜지고 나면 외부 요청이 없어이 자동으로 이 메서드를 1회 실행
    public void warming() {
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
            final long currentMushroomId = mushroomId;
            try{
                // Redis에서 찾을 Key를 명세서 규격('3:guide")으로 조립
                String cacheKey = currentMushroomId + ":guide";

                if (guideCache != null && guideCache.get(cacheKey) != null) {
                    log.info("이미 Redis에 'mushroomId: {}' 가이드라인이 존재합니다.", currentMushroomId);
                    continue;
                }
                /*
                수정사항 : [분산 락 적용]
                다중 서버(K8s 파드) 환경에서 동시에 AI API를 중복 호출하는 비용 낭비를 줄이기 위해
                Redis SETNX와 소유자 토큰을 이용해 동시에 실행되는 인스턴스의 중복 AI 생성을 줄이고 중복 요청 발생을 완화하도록 제어

                문제 : 모든 인스턴스가 락 값으로 "LOCKED"를 사용하여 소유자를 구분 못하는 문제가 있어 타인이 새 락까지 삭제해버리는 문제 발생
                [해결책] "LOCKED" 대신 인스턴스마다 다른 UUID 생성
                 */
                String lockKey = "ai:mushroom:lock:" + currentMushroomId;
                String uuid = UUID.randomUUID().toString();
                Duration lockTimeout = Duration.ofSeconds(90);
                Boolean isLocked = stringRedisTemplate.opsForValue()
                        .setIfAbsent(lockKey, uuid, lockTimeout);

                if (Boolean.TRUE.equals(isLocked)) {
                    generateWithLock(currentMushroomId, lockKey, uuid, lockTimeout);
                } else {
                    log.info("다른 서버 인스턴스가 ID: {} 생성 작업을 선점했습니다. (Skip)", currentMushroomId);
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

    private void generateWithLock(long mushroomId, String lockKey, String uuid, Duration lockTimeout) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        try { // AI 작업이 오래 걸릴 수 있으므로 30초마다 락 TTL을 갱신
            scheduler.scheduleAtFixedRate(() -> renewLock(
                            mushroomId, lockKey, uuid, lockTimeout), 30, 30, TimeUnit.SECONDS);

            log.info("캐시 없음. 락 획득 (ID: {}) AI 가이드라인 생성 시작...",mushroomId);

            mushService.generateRealDataGuide(mushroomId);

        } finally { // Lua 사용하여 Redis에서 작업 끊기지 않고 처리
            //AI 작업이 끝나면 TTL 갱신을 중단
            scheduler.shutdownNow();
            // 내 UUID와 일치할 때만 락을 삭제
            stringRedisTemplate.execute(RELEASE_LOCK_SCRIPT, Collections.singletonList(lockKey), uuid);
        }
    }

    private void renewLock(long mushroomId, String lockKey, String uuid, Duration lockTimeout) {
        try {
            Long renewed = stringRedisTemplate.execute(RENEW_LOCK_SCRIPT, Collections.singletonList(lockKey),
                    uuid, String.valueOf(lockTimeout.getSeconds())
            );

            if (Long.valueOf(1L).equals(renewed)) {
                log.debug("ID {} 락 TTL 갱신 완료", mushroomId);
            } else {
                log.warn("ID {} 락 갱신 실패 또는 소유권 상실", mushroomId);
            }

        } catch (Exception e) {
            log.warn("락 갱신 실패 (ID: {})", mushroomId, e);
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
