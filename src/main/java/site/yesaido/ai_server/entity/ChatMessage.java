package site.yesaido.ai_server.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "chat_message")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_conversation_id", nullable = false)
    private Long chatConversationId;

    // JPA EnumType.STRING을 통해 DB에는 VARCHAR로 안전하게 저장
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private MessageRole role; // "USER" or "ASSISTANT"

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "sequence_number", nullable = false)
    private Long sequenceNumber; // 대화 내 메시지 순서 (1, 2, 3...)

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public ChatMessage(Long chatConversationId, MessageRole role, String content, Long sequenceNumber) {
        this.chatConversationId = chatConversationId;
        this.role = role;
        this.content = content;
        this.sequenceNumber = sequenceNumber;
        this.createdAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }
}
