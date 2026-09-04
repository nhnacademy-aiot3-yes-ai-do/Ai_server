package site.yesaido.ai_server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatbotService {
    private static final Pattern PSEUDO_TOOL_TAG_PATTERN = Pattern.compile("!?\\[[^]]*]\\([^)]*\\)");

    private final PromptProperties promptProperties;
    private final ChatClient geminiChatClient; // Gemini 2.5 Flash Lite

    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;

    private final MushroomKnowledgeTool mushroomKnowledgeTool;
    private final EnvironmentTool environmentTool;
    private final PastHarvestInsightTool pastHarvestInsightTool;


    // 사용자 질문 처리, AI 답변 생성
    @Transactional
    public ChatMessageResponse chat(Long userId, ChatMessageRequest request) {
        log.info("챗봇 대화 요청 수신 - userId: {}, conversationId: {}, cultivationId: {}, channelId: {}",
                userId, request.conversationId(), request.cultivationId(), request.channelId());
        UserContextHolder.setUserId(userId);
        try{
            // 대화방 세션 확보 (요청에 conversationId가 있으면 기존 방 사용, 없으면 신규/조회)
            ChatConversation conversation = getOrCreateConversation(
                    userId,
                    request.cultivationId(),
                    request.channelId(),
                    request.conversationId()
            );
            //  이전 대화 이력 먼저 조회 (현재 질문 제외한 순수 과거 대화 복원)
            List<Message> historyMessages = buildRecentConversationHistory(conversation.getId());

            // 사용자 질문 DB 저장
            Long userSeq = messageRepository.findMaxSequenceNumber(conversation.getId()) + 1;
            ChatMessage userMessageEntity = ChatMessage.builder()
                    .chatConversationId(conversation.getId())
                    .role(MessageRole.USER)
                    .content(request.message())
                    .sequenceNumber(userSeq)
                    .build();
            messageRepository.save(userMessageEntity);

            PromptTemplate promptTemplate = new PromptTemplate(promptProperties.getChatSystemPrompt());
            Map<String, Object> contextMap = new HashMap<>();
            contextMap.put("userId", userId);
            contextMap.put("cultivationId", request.cultivationId() != null ? request.cultivationId() : "선택 안 됨 (일반 질문 모드)");
            Message systemMessage = promptTemplate.createMessage(contextMap);

            // 프롬프트 메시지 조립 (시스템 지침 + 최근 10개 대화 + 현재 사용자 질문)
            List<Message> fullMessages = new ArrayList<>();
            fullMessages.add(systemMessage);
            fullMessages.addAll(historyMessages);
            fullMessages.add(new UserMessage(request.message()));

            Prompt prompt = new Prompt(fullMessages);

            Optional<String> replyOpt = callAi(prompt);
            String replyText = replyOpt.orElse("죄송합니다. 현재 일시적인 AI 서비스 점검 중이라 답변을 생성하지 못했습니다. 잠시 후 다시 질문해 주세요.");
            replyText = PSEUDO_TOOL_TAG_PATTERN.matcher(replyText).replaceAll("").trim();

            // AI 답변 DB 저장
            Long aiSeq = messageRepository.findMaxSequenceNumber(conversation.getId()) + 1;
            ChatMessage aiMessageEntity = ChatMessage.builder()
                    .chatConversationId(conversation.getId())
                    .role(MessageRole.ASSISTANT)
                    .content(replyText)
                    .sequenceNumber(aiSeq)
                    .build();
            ChatMessage savedAiMessage = messageRepository.save(aiMessageEntity);

            conversation.updateTimestamp();

            return new ChatMessageResponse(
                    conversation.getId(),
                    savedAiMessage.getContent(),
                    savedAiMessage.getRole(),
                    savedAiMessage.getSequenceNumber(),
                    savedAiMessage.getCreatedAt()
            );
        } finally {
            // 메모리 누수 방지 및 컨텍스트 정리
            UserContextHolder.clear();
        }
    }

    // 특정 대화방의 과거 전체 대화 이력 조회 (화면 복원하는데 사용)
    @Transactional(readOnly = true)
    public List<ChatMessageDto> getConversationHistory(Long userId, Long conversationId, Long cultivationId) {
        Optional<ChatConversation> convOpt;
        if (conversationId != null) {
            convOpt = conversationRepository.findByIdAndUserId(conversationId, userId);
        } else if (cultivationId != null) {
            convOpt = conversationRepository.findLatestByCultivation(userId, cultivationId, 1L);
        } else {
            convOpt = conversationRepository.findByUserIdAndChannelId(userId, 1L);
        }
        return convOpt.map(conv -> messageRepository.findAllMessages(conv.getId()).stream()
                .map(ChatMessageDto::from).toList()).orElseGet(List::of);
    }
    // 대화방 세션 조회 또는 신규 생성 로직
    private ChatConversation getOrCreateConversation(Long userId, Long cultivationId, Long channelId, Long conversationId) {
        Long targetChannel = channelId != null ? channelId : 1L; // 기본값: 웹(1L)

        // 요청에 명시적인 conversationId가 전달된 경우 해당 방을 즉시 재사용
        if (conversationId != null) {
            return conversationRepository.findByIdAndUserId(conversationId, userId)
                    .orElseThrow(() -> new IllegalArgumentException("해당 대화방에 접근할 수 없거나 존재하지 않습니다. ID: " + conversationId));
        }

        // 특정 경작지 대화방이면 해당 채널/경작지 기준 가장 최근 대화방 조회
        if (cultivationId != null) {
            return conversationRepository.findLatestByCultivation(userId, cultivationId, targetChannel)
                    .orElseGet(() -> createNewConversation(userId, cultivationId, targetChannel));
        }

        // 일반 질문이면 사용자 ID와 채널 기준 대화방 조회
        return conversationRepository.findByUserIdAndChannelId(userId, targetChannel)
                .orElseGet(() -> createNewConversation(userId, null, targetChannel));
    }

    private ChatConversation createNewConversation(Long userId, Long cultivationId, Long channelId) {
        String sessionUuid = UUID.randomUUID().toString();
        ChatConversation conversation = ChatConversation.builder()
                .userId(userId)
                .cultivationId(cultivationId)
                .channelId(channelId)
                .externalConversationId(sessionUuid)
                .build();
        return conversationRepository.save(conversation);
    }

    // 최근 10개 대화를 Spring AI Message 객체로 변환
    private List<Message> buildRecentConversationHistory(Long conversationId) {
        List<ChatMessage> recentList = messageRepository.findTop10ByChatConversationIdOrderBySequenceNumberDesc(conversationId);
        List<ChatMessage> chronologicalList = recentList.reversed();
        List<Message> messages = new ArrayList<>();
        for (ChatMessage msg : chronologicalList) {
            String content = msg.getContent();

            // 과거 실패 메시지나 도구 호출 흉내 텍스트는 AI 프롬프트 주입에서 제외
            if (isPollutedOrErrorMessage(content)) {
                continue;
            }

            if (msg.getRole() == MessageRole.USER) {
                messages.add(new UserMessage(content));
            } else if (msg.getRole() == MessageRole.ASSISTANT) {
                messages.add(new AssistantMessage(content));
            }
        }
        return messages;
    }

    private boolean isPollutedOrErrorMessage(String content) {
        return content == null
                || content.contains("일시적인 AI 서비스 점검 중")
                || content.contains("잠시만 기다려 주세요")
                || content.contains("EnvironmentTool")
                || content.contains("MushroomKnowledgeTool")
                || content.contains("PastHarvestInsightTool");
    }

    // Gemini 3.5 Flash Lite 호출 (3대 Tools 포함)
    private Optional<String> callAi(Prompt prompt) {
        try {
            log.info("Gemini 2.5 Flash Lite 호출 시작 (3대 도구 연동) - 프롬프트 메시지 수: {}", prompt.getInstructions().size());
            String response = geminiChatClient.prompt()
                    .messages(prompt.getInstructions())
                    .tools(mushroomKnowledgeTool, environmentTool, pastHarvestInsightTool)
                    .call()
                    .content();

            if (response != null && !response.isBlank()) {
                return Optional.of(response);
            }
        } catch (Exception e) {
            log.error("Gemini 2.5 Flash Lite 호출 실패: {}", e.getMessage(), e);
        }
        return Optional.empty();
    }
}
