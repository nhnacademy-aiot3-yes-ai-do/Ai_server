package site.yesaido.ai_server.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;
import site.yesaido.ai_server.config.PromptProperties;
import site.yesaido.ai_server.context.UserContextHolder;
import site.yesaido.ai_server.dto.ai.chat.ChatMessageDto;
import site.yesaido.ai_server.dto.ai.chat.ChatMessageRequest;
import site.yesaido.ai_server.dto.ai.chat.ChatMessageResponse;
import site.yesaido.ai_server.entity.ChatConversation;
import site.yesaido.ai_server.entity.ChatMessage;
import site.yesaido.ai_server.entity.MessageRole;
import site.yesaido.ai_server.repository.ChatConversationRepository;
import site.yesaido.ai_server.repository.ChatMessageRepository;
import site.yesaido.ai_server.tool.EnvironmentTool;
import site.yesaido.ai_server.tool.MushroomKnowledgeTool;
import site.yesaido.ai_server.tool.PastHarvestInsightTool;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatbotServiceTest {
    @Mock
    private PromptProperties promptProperties;

    @Mock
    private ChatClient geminiChatClient;

    @Mock
    private ChatConversationRepository conversationRepository;

    @Mock
    private ChatMessageRepository messageRepository;

    @Mock
    private MushroomKnowledgeTool mushroomKnowledgeTool;

    @Mock
    private EnvironmentTool environmentTool;

    @Mock
    private PastHarvestInsightTool pastHarvestInsightTool;

    @InjectMocks
    private ChatbotService chatbotService;

    @BeforeEach
    void setUp() {
        chatbotService = new ChatbotService(
                promptProperties,
                geminiChatClient,
                conversationRepository,
                messageRepository,
                mushroomKnowledgeTool,
                environmentTool,
                pastHarvestInsightTool
        );

        Resource mockSystemPrompt = new ByteArrayResource("System Prompt Template {cultivationId}".getBytes(StandardCharsets.UTF_8));
        lenient().when(promptProperties.getChatSystemPrompt()).thenReturn(mockSystemPrompt);
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    private void mockGeminiSuccess(String reply) {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class, org.mockito.Mockito.RETURNS_SELF);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);

        when(geminiChatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(reply);
    }

    @Test
    @DisplayName("chat - Gemini 호출 성공 및 답변 정상 반환 검증")
    void chat_geminiSuccess() {
        ChatMessageRequest request = new ChatMessageRequest(null, 7L, "안녕하세요", 1L);
        ChatConversation mockConv = ChatConversation.builder()
                .userId(22L)
                .cultivationId(7L)
                .channelId(1L)
                .externalConversationId("session-1")
                .build();
        ReflectionTestUtils.setField(mockConv, "id", 1L);

        when(conversationRepository.findLatestByCultivation(22L, 7L, 1L)).thenReturn(Optional.of(mockConv));
        when(messageRepository.findMaxSequenceNumber(1L)).thenReturn(0L, 1L);

        ChatMessage savedUserMsg = ChatMessage.builder().chatConversationId(1L).role(MessageRole.USER).content("안녕하세요").sequenceNumber(1L).build();
        ChatMessage savedAiMsg = ChatMessage.builder().chatConversationId(1L).role(MessageRole.ASSISTANT).content("반갑습니다!").sequenceNumber(2L).build();

        when(messageRepository.save(any(ChatMessage.class))).thenReturn(savedUserMsg, savedAiMsg);
        when(messageRepository.findTop10ByChatConversationIdOrderBySequenceNumberDesc(1L)).thenReturn(List.of());

        mockGeminiSuccess("반갑습니다!");

        ChatMessageResponse response = chatbotService.chat(22L, request);

        assertThat(response.conversationId()).isEqualTo(1L);
        assertThat(response.reply()).isEqualTo("반갑습니다!");
        assertThat(response.role()).isEqualTo(MessageRole.ASSISTANT);
    }

    @Test
    @DisplayName("chat - conversationId 전달 시 본인 대화방이 아니면 예외 발생")
    void chat_invalidConversationId_throwsException() {
        ChatMessageRequest request = new ChatMessageRequest(999L, 7L, "질문", 1L);
        when(conversationRepository.findByIdAndUserId(999L, 22L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatbotService.chat(22L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("해당 대화방에 접근할 수 없거나 존재하지 않습니다.");
    }

    @Test
    @DisplayName("chat - cultivationId가 null일 때 일반 사용자 대화방 신규 생성 검증")
    void chat_noCultivationId_createsGeneralConversation() {
        ChatMessageRequest request = new ChatMessageRequest(null, null, "일반 질문", 1L);
        ChatConversation newConv = ChatConversation.builder().userId(22L).channelId(1L).externalConversationId("uuid").build();
        ReflectionTestUtils.setField(newConv, "id", 2L);

        when(conversationRepository.findByUserIdAndChannelId(22L, 1L)).thenReturn(Optional.empty());
        when(conversationRepository.save(any(ChatConversation.class))).thenReturn(newConv);
        when(messageRepository.findMaxSequenceNumber(2L)).thenReturn(0L, 1L);

        ChatMessage savedUser = ChatMessage.builder().chatConversationId(2L).role(MessageRole.USER).content("일반 질문").sequenceNumber(1L).build();
        ChatMessage savedAi = ChatMessage.builder().chatConversationId(2L).role(MessageRole.ASSISTANT).content("일반 답변").sequenceNumber(2L).build();
        when(messageRepository.save(any(ChatMessage.class))).thenReturn(savedUser, savedAi);
        when(messageRepository.findTop10ByChatConversationIdOrderBySequenceNumberDesc(2L)).thenReturn(List.of());

        mockGeminiSuccess("일반 답변");

        ChatMessageResponse response = chatbotService.chat(22L, request);

        assertThat(response.conversationId()).isEqualTo(2L);
        assertThat(response.reply()).isEqualTo("일반 답변");
    }

    @Test
    @DisplayName("getConversationHistory - 대화방 ID 기준 조회 성공")
    void getConversationHistory_byConversationId() {
        ChatConversation conv = ChatConversation.builder().userId(22L).build();
        ReflectionTestUtils.setField(conv, "id", 1L);
        ChatMessage msg1 = ChatMessage.builder().chatConversationId(1L).role(MessageRole.USER).content("안녕").sequenceNumber(1L).build();

        when(conversationRepository.findByIdAndUserId(1L, 22L)).thenReturn(Optional.of(conv));
        when(messageRepository.findAllMessages(1L)).thenReturn(List.of(msg1));

        List<ChatMessageDto> result = chatbotService.getConversationHistory(22L, 1L, null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().content()).isEqualTo("안녕");
        assertThat(result.getFirst().role()).isEqualTo(MessageRole.USER);
    }

    @Test
    @DisplayName("getConversationHistory - 재배지 ID 기준 조회 성공")
    void getConversationHistory_byCultivationId() {
        ChatConversation conv = ChatConversation.builder().userId(22L).cultivationId(7L).build();
        ReflectionTestUtils.setField(conv, "id", 1L);
        ChatMessage msg1 = ChatMessage.builder().chatConversationId(1L).role(MessageRole.USER).content("질문").sequenceNumber(1L).build();

        when(conversationRepository.findLatestByCultivation(22L, 7L, 1L)).thenReturn(Optional.of(conv));
        when(messageRepository.findAllMessages(1L)).thenReturn(List.of(msg1));

        List<ChatMessageDto> result = chatbotService.getConversationHistory(22L, null, 7L);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().content()).isEqualTo("질문");
    }

    @Test
    @DisplayName("getConversationHistory - 대화방이 없을 때 빈 리스트 반환")
    void getConversationHistory_empty() {
        when(conversationRepository.findByUserIdAndChannelId(22L, 1L)).thenReturn(Optional.empty());

        List<ChatMessageDto> result = chatbotService.getConversationHistory(22L, null, null);

        assertThat(result).isEmpty();
    }
}
