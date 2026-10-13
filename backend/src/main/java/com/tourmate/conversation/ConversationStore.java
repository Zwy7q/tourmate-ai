package com.tourmate.conversation;

import java.util.List;

public interface ConversationStore {
    void append(String conversationId, ConversationMessage message);
    List<ConversationMessage> recent(String conversationId);
}
