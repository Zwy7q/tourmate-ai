package com.tourmate.conversation;

import java.time.Instant;

public record ConversationMessage(String role, String content, Instant createdAt) {
    public ConversationMessage(String role, String content) {
        this(role, content, Instant.now());
    }
}
