package site.yesaido.ai_server.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import site.yesaido.ai_server.dto.ai.chat.ChatMessageDto;
import site.yesaido.ai_server.dto.ai.chat.ChatMessageRequest;
import site.yesaido.ai_server.dto.ai.chat.ChatMessageResponse;
import site.yesaido.ai_server.dto.common.ApiResponse;
import site.yesaido.ai_server.service.ChatbotService;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/ai/chat")
@RequiredArgsConstructor
public class ChatController {
    private final ChatbotService chatbotService;

    // 챗봇 질문 전송 및 실시간 AI 답변 수신
    @PostMapping
    public ApiResponse<ChatMessageResponse> chat(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody ChatMessageRequest request
    ) {
        log.info("챗봇 대화 요청 API 호출 - userId: {}, cultivationId: {}", userId, request.cultivationId());
        ChatMessageResponse response = chatbotService.chat(userId, request);
        return ApiResponse.success(response);
    }

    // 특정 대화방의 과거 전체 대화 이력 조회(화면 복원에 사용)
    @GetMapping("/history")
    public ApiResponse<List<ChatMessageDto>> getConversationHistory(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam("conversationId") Long conversationId
    ) {
        log.info("챗봇 대화 이력 조회 API 호출 - userId: {}, conversationId: {}", userId, conversationId);
        List<ChatMessageDto> history = chatbotService.getConversationHistory(userId, conversationId);
        return ApiResponse.success(history);
    }
}
