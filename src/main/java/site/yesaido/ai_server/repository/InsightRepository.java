package site.yesaido.ai_server.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.yesaido.ai_server.entity.Insight;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface InsightRepository extends JpaRepository<Insight,Long> {
    Optional<Insight> findByCultivationId(Long cultivationId); // 중복 적제 방지

    @Query("""
        select i from Insight i
        where i.mushroomId = :mushroomId
            and i.avgTemperature between :minTemp and :maxTemp
            and i.avgHumidity between :minHum and :maxHum
            and i.avgCo2 between :minCo2 and :maxCo2
            and i.avgLight between :minLight and :maxLight
            and i.cultivationId not in :myCultivationIds
        order by i.createdAt desc
    """)
    List<Insight> findSimilarCandidates(
            @Param("mushroomId") Long mushroomId,
            @Param("minTemp") BigDecimal minTemp, @Param("maxTemp") BigDecimal maxTemp,
            @Param("minHum") BigDecimal minHum, @Param("maxHum") BigDecimal maxHum,
            @Param("minCo2") BigDecimal minCo2, @Param("maxCo2") BigDecimal maxCo2,
            @Param("minLight") BigDecimal minLight, @Param("maxLight") BigDecimal maxLight,
            @Param("myCultivationIds") List<Long> myCultivationIds,
            Pageable pageable
    );

}
