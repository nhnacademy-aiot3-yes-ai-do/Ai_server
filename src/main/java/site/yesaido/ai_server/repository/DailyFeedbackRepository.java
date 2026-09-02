package site.yesaido.ai_server.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.yesaido.ai_server.entity.DailyFeedback;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 일일 피드백을 저장하고 멱등성 기준으로 조회하는 Repository입니다.
 *
 * <p>같은 경작지와 같은 날짜에 이미 생성된 피드백을 조회하여
 * 중복 생성을 사전에 방지하고, UNIQUE 충돌 이후 기존 결과를 복구하는 데 사용합니다.</p>
 */
public interface DailyFeedbackRepository extends JpaRepository<DailyFeedback, Long> {

    Optional<DailyFeedback> findByCultivationIdAndFeedbackDate(Long cultivationId, LocalDate feedbackDate);
    // Cultivation 날짜순으로 전체 일일피드백 목록 조회 추가(인사이트 요약에 사용)
    @Query("SELECT df FROM DailyFeedback df WHERE df.cultivationId = :cultivationId ORDER BY  df.feedbackDate ASC")
    List<DailyFeedback> findAllByCultivationId(@Param("cultivationId") Long cultivationId);
}
