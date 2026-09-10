package site.yesaido.ai_server.repository;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.yesaido.ai_server.dto.ai.insight.InsightSearchCondition;
import site.yesaido.ai_server.entity.Insight;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface InsightRepository extends JpaRepository<Insight,Long> {
    Optional<Insight> findByCultivationId(Long cultivationId); // 중복 적제 방지

    // 특정 버섯의 최고 수확량 상위 N개 조회(Pageable 지원)
    @Query("SELECT i FROM Insight i WHERE i.mushroomId = :mushroomId ORDER BY i.harvestWeightGrams DESC")
    List<Insight> findTopHarvests(@Param("mushroomId") Long mushroomId, Pageable pageable);

    // 챗봇 및 기존 코드를 위한 default 메서드 (기존 findTopHarvests(mushroomId) 완벽 호환)
    default List<Insight> findTopHarvests(Long mushroomId) {
        return findTopHarvests(mushroomId, PageRequest.of(0, 3));
    }

    @Query("""
        select i from Insight i
        where i.mushroomId = :#{#cond.mushroomId}
            and (:#{#cond.minTemp} is null or i.avgTemperature is null or i.avgTemperature between :#{#cond.minTemp} and :#{#cond.maxTemp})
            and (:#{#cond.minHum} is null or i.avgHumidity is null or i.avgHumidity between :#{#cond.minHum} and :#{#cond.maxHum})
            and (:#{#cond.minCo2} is null or i.avgCo2 is null or i.avgCo2 between :#{#cond.minCo2} and :#{#cond.maxCo2})
            and (:#{#cond.minLight} is null or i.avgLight is null or i.avgLight between :#{#cond.minLight} and :#{#cond.maxLight})
            and (
                (:#{#cond.minTemp} is not null and i.avgTemperature between :#{#cond.minTemp} and :#{#cond.maxTemp})
                or (:#{#cond.minHum} is not null and i.avgHumidity between :#{#cond.minHum} and :#{#cond.maxHum})
                or (:#{#cond.minCo2} is not null and i.avgCo2 between :#{#cond.minCo2} and :#{#cond.maxCo2})
                or (:#{#cond.minLight} is not null and i.avgLight between :#{#cond.minLight} and :#{#cond.maxLight})
            )
            and i.cultivationId not in :#{#cond.myCultivationIds}
        order by i.createdAt desc
    """)
    List<Insight> findSimilarCandidates( // 들어가는 매개변수가 너무 많아 DTO로 포장해서 넘기느 방식으로 수정
            @Param("cond") InsightSearchCondition condition,
            Pageable pageable
    );
    // 특정 버섯의 정상 수확량 목록 오름차순 조회(병해 과락 30점 이하는 제외해서) -평균 수확량 업데이트 하는데 사용
    @Query("SELECT i.harvestWeightGrams FROM Insight i WHERE i.mushroomId = :mushroomId AND i.growthScore > 30 ORDER BY i.harvestWeightGrams ASC")
    List<BigDecimal> findValidHarvestWeightsByMushroomId(@Param("mushroomId") Long mushroomId);

}
