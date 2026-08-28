package site.yesaido.ai_server.dto.ai.chat;

import java.time.LocalDateTime;

public record ChatMessageDto( // 대화창 열었을 때 과거 대화 복원에 사용
        Long id, // 메시지 고유 DB 식별자
        String role, // USER(사용자), ASSISTANT(Ai)
        String content, // 텍스트 내용
        Long sequenceNumber, // 대화방 내 순서 번호
        LocalDateTime createdAt // 작성 시간
) {}
