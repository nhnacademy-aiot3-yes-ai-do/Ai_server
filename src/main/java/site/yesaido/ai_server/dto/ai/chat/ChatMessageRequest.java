package site.yesaido.ai_server.dto.ai.chat;

public record ChatMessageRequest(
        Long cultivationId, // 현재 보고 있는 재배지 Id
        String message, // 사용자 질문
        Long channelId // 웹, 디스코드, 텔레그램
) {}
