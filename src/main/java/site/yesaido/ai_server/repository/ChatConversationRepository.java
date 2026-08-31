package site.yesaido.ai_server.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.yesaido.ai_server.entity.ChatConversation;

import java.util.Optional;

@Repository
public interface ChatConversationRepository extends JpaRepository<ChatConversation, Long> {
    // 사용자 ID로 1:1 전담 대화방 조회
    // 웹, 디스코드, 텔레그램 어디서 접속하든 이전 대화 맥락 이어갈 수 있게 하기 위해 사용
    Optional<ChatConversation> findByIdAndUserId(Long id, Long userId);

    //  사용자 ID와 접속 채널(1:웹, 2:디스코드, 3:텔레그램)로 특정 채널 대화방 조회
    // 채널 간 대화방 독립적으로 분리하는데 사용
    Optional<ChatConversation> findByUserIdAndChannelId(Long userId, Long channelId);

    // 프론트엔드 세션 식별자(UUID)로 기존 대화방 찾을 떄 사용
    Optional<ChatConversation> findByExternalConversationId(String externalConversationId);

    // 사용자와 특정 채널 및 경작지 기준 최근 대화방 조회(채널 간 대화 뒤섞이는거 방지)
    @Query("SELECT c FROM ChatConversation c WHERE c.userId = :userId AND c.cultivationId = :cultivationId AND c.channelId = :channelId ORDER BY c.updatedAt DESC LIMIT 1")
    Optional<ChatConversation> findLatestByCultivation(
            @Param("userId") Long userId,
            @Param("cultivationId") Long cultivationId,
            @Param("channelId") Long channelId
    );

    // 해당 사용자의 대화방이 이미 존재하는지 여부 확인
    boolean existsByUserId(Long userId);
}
