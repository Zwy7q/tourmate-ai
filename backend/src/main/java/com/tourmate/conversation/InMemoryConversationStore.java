package com.tourmate.conversation;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
@org.springframework.context.annotation.Profile("!redis")
public class InMemoryConversationStore implements ConversationStore {
    private final Map<String, ArrayDeque<ConversationMessage>> conversations = new ConcurrentHashMap<>();
    private final int maxMessages;

    public InMemoryConversationStore() {
        this(20);
    }

    public InMemoryConversationStore(int maxMessages) {
        if (maxMessages < 1) throw new IllegalArgumentException("会话窗口必须大于 0");
        this.maxMessages = maxMessages;
    }

    @Override
    public void append(String conversationId, ConversationMessage message) {
        var messages = conversations.computeIfAbsent(conversationId, ignored -> new ArrayDeque<>());
        synchronized (messages) {
            messages.addLast(message);
            while (messages.size() > maxMessages) messages.removeFirst();
        }
    }

    @Override
    public java.util.List<ConversationMessage> recent(String conversationId) {
        var messages = conversations.get(conversationId);
        if (messages == null) return List.of();
        synchronized (messages) {
            return List.copyOf(messages);
        }
    }
}
