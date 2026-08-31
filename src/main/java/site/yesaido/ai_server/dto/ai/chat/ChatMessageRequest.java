package site.yesaido.ai_server.dto.ai.chat;

public record ChatMessageRequest(
        Long conversationId, // 연속 대화 시 전달받을 기존 대화방 ID (첫 질문 시 null)
        Long cultivationId, // 현재 보고 있는 재배지 Id
        String message, // 사용자 질문
        Long channelId // 웹, 디스코드, 텔레그램
) {}
