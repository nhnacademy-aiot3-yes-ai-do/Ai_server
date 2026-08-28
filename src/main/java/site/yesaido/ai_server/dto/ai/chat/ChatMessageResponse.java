package site.yesaido.ai_server.dto.ai.chat;

import site.yesaido.ai_server.entity.MessageRole;

import java.time.LocalDateTime;

public record ChatMessageResponse( // 질문 직후 Ai 답변 응답에 사용
        Long conversationId, // 소속된 대화방 Id (세션 식별)
        String reply, // Ai가 생성한 최종 답변 텍스트
        MessageRole role, // Ai 답변이라 role은 항상 ASSISTANT
        Long sequenceNumber, // 말풍선 순서 번호
        LocalDateTime createdAt // 답변 생성 시간
) {}
