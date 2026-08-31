package site.yesaido.ai_server.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.yesaido.ai_server.dto.ai.chat.ChatMessageDto;
import site.yesaido.ai_server.dto.ai.chat.ChatMessageRequest;
import site.yesaido.ai_server.dto.ai.chat.ChatMessageResponse;
import site.yesaido.ai_server.dto.common.ApiResponse;
import site.yesaido.ai_server.entity.MessageRole;
import site.yesaido.ai_server.service.ChatbotService;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {
    @Mock
    private ChatbotService chatbotService;

    @InjectMocks
    private ChatController chatController;

    @Test
    @DisplayName("챗봇 메시지 전송 및 답변 수신 API 성공 검증")
    void chat_success() {
        ChatMessageRequest request = new ChatMessageRequest(1L, 7L, "현재 센서 값 알려줘", 1L);
        ChatMessageResponse expectedResponse = new ChatMessageResponse(1L, "현재 온도 15.8도입니다.", MessageRole.ASSISTANT, 2L, LocalDateTime.now());

        when(chatbotService.chat(22L, request)).thenReturn(expectedResponse);

        ApiResponse<ChatMessageResponse> result = chatController.chat(22L, request);

        assertThat(result).isEqualTo(ApiResponse.success(expectedResponse));

        verify(chatbotService).chat(22L, request);
    }

    @Test
    @DisplayName("대화방 또는 재배지 기준 대화 이력 조회 API 성공 검증")
    void getConversationHistory_success() {
        List<ChatMessageDto> expectedList = List.of(
                new ChatMessageDto(1L, MessageRole.USER, "질문입니다", 1L, LocalDateTime.now()),
                new ChatMessageDto(2L, MessageRole.ASSISTANT, "답변입니다", 2L, LocalDateTime.now())
        );

        when(chatbotService.getConversationHistory(22L, 1L, 7L)).thenReturn(expectedList);

        ApiResponse<List<ChatMessageDto>> result = chatController.getConversationHistory(22L, 1L, 7L);

        assertThat(result).isEqualTo(ApiResponse.success(expectedList));
        verify(chatbotService).getConversationHistory(22L, 1L, 7L);
    }

}
