package site.yesaido.ai_server.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.yesaido.ai_server.dto.ai.insight.InsightSearchCondition;
import site.yesaido.ai_server.entity.Insight;
import java.util.List;
import java.util.Optional;

public interface InsightRepository extends JpaRepository<Insight,Long> {
    Optional<Insight> findByCultivationId(Long cultivationId); // 중복 적제 방지

    @Query("""
        select i from Insight i
        where i.mushroomId = :#{#cond.mushroomId}
            and i.avgTemperature between :#{#cond.minTemp} and :#{#cond.maxTemp}                                                                                                                    \s
            and i.avgHumidity between :#{#cond.minHum} and :#{#cond.maxHum}                                                                                                                         \s
            and i.avgCo2 between :#{#cond.minCo2} and :#{#cond.maxCo2}                                                                                                                              \s
            and i.avgLight between :#{#cond.minLight} and :#{#cond.maxLight}                                                                                                                        \s
            and i.cultivationId not in :#{#cond.myCultivationIds}
        order by i.createdAt desc
    """)
    List<Insight> findSimilarCandidates( // 들어가는 매개변수가 너무 많아 DTO로 포장해서 넘기느 방식으로 수정
            @Param("cond") InsightSearchCondition condition,
            Pageable pageable
    );

}
