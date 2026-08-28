package site.yesaido.ai_server.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.yesaido.ai_server.entity.ChatMessage;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    // 화면 전체 대화 복원
    // 메시지 번호 순서대로 오름차순 조회하여 페이지 새로고침 or 대화창 열었을 때 과거 대화 렌더링 하는데 사용
    List<ChatMessage> findByChatConversationIdOrderBySequenceNumberAsc(Long chatConversationId);

    // AI에게 보낼 최근 10개 대화 조회(대화 맥락 유지하면서 api 호출 토큰 절감)
    List<ChatMessage> findTop10ByChatConversationIdOrderBySequenceNumberDesc(Long chatConversationId);

    // 새 메시지 추가 시 메시지 순서 뒤틀리지 않게 마지막 번호 조회
    @Query("SELECT COALESCE(MAX(m.sequenceNumber), 0) FROM ChatMessage m WHERE m.chatConversationId = :conversationId")
    Long findMaxSequenceNumberByConversationId(@Param("conversationId") Long conversationId);

    // 60일 지난 오래된 대화 일괄 자동 삭제
    @Modifying
    @Query("DELETE FROM ChatMessage m WHERE m.createdAt < :cutoffDate")
    int deleteOldMessages(@Param("cutoffDate") LocalDateTime cutoffDate);
}
