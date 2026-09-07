package site.yesaido.ai_server.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import site.yesaido.ai_server.dto.ai.chat.ChatMessageDto;
import site.yesaido.ai_server.dto.ai.chat.ChatMessageRequest;
import site.yesaido.ai_server.dto.ai.chat.ChatMessageResponse;
import site.yesaido.ai_server.dto.common.ApiResponse;

import java.util.List;

/**
 * {@code ChatController}의 OpenAPI 문서 정의.
 */
@Tag(name = "AI 챗봇", description = "재배 상담 챗봇(RAG) 질문 전송 및 대화 이력 조회")
public interface ChatControllerDocs {

    @Operation(summary = "챗봇 질문 전송", description = "질문을 보내 AI 답변을 받습니다. `cultivationId` 로 대화 맥락을 지정할 수 있습니다.")
    ApiResponse<ChatMessageResponse> chat(Long userId, ChatMessageRequest request);

    @Operation(summary = "대화 이력 조회",
            description = "특정 대화방의 과거 전체 대화 이력을 반환합니다(화면 복원용). "
                    + "`conversationId` 또는 `cultivationId` 중 하나로 대화방을 지정합니다.")
    ApiResponse<List<ChatMessageDto>> getConversationHistory(
            Long userId,
            @Parameter(description = "대화방 ID") Long conversationId,
            @Parameter(description = "재배 ID (대화방 대체 식별자)") Long cultivationId);
}
