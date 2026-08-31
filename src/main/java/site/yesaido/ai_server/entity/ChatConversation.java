package site.yesaido.ai_server.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(
        name = "chat_conversation",
        uniqueConstraints = {
                // 외부 통신용 세션 UUID 무결성 보장
                @UniqueConstraint(
                        name = "uk_chat_conversation_external_id",
                        columnNames = "external_conversation_id"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatConversation { // 대화방 entity
    private static final ZoneId KST_ZONE = ZoneId.of("Asia/Seoul");
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "cultivation_id")
    private Long cultivationId; // 특정 경작지 챗봇이면 ID 저장, 일반 질문이면 null

    @Column(name = "channel_id", nullable = false)
    private Long channelId; // 1: 웹 대시보드, 2: 디스코드, 3: 텔레그램

    @Column(name = "external_conversation_id", nullable = false, length = 36)
    private String externalConversationId; // 프론트와 통신할 세션 UUID

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public ChatConversation(Long userId, Long cultivationId, Long channelId, String externalConversationId) {
        this.userId = userId;
        this.cultivationId = cultivationId;
        this.channelId = channelId != null ? channelId : 1L; // 기본값: 웹
        this.externalConversationId = externalConversationId;
        this.createdAt = LocalDateTime.now(KST_ZONE);
        this.updatedAt = LocalDateTime.now(KST_ZONE);
    }

    public void updateTimestamp() {
        this.updatedAt = LocalDateTime.now(KST_ZONE);
    }
}
