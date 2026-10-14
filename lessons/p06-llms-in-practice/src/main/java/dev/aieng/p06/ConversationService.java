package dev.aieng.p06;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;

/**
 * Conversation memory. The model is stateless (Phase 0, lesson 3); "memory" is an advisor that stores each
 * exchange per conversation id and prepends the recent history to the next prompt.
 */
public class ConversationService {

    // region memory
    private final ChatMemory memory = MessageWindowChatMemory.builder()
            .maxMessages(20) // a window: older messages fall out, bounding cost and context length
            .build();
    private final ChatClient chat;

    public ConversationService(ChatModel model) {
        this.chat = ChatClient.builder(model)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
                .build();
    }

    public String send(String conversationId, String message) {
        return chat.prompt()
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
    }
    // endregion

    public int storedMessages(String conversationId) {
        return memory.get(conversationId).size();
    }
}
