package com.tourmate.api;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(@NotBlank String message, String conversationId) {
    public ChatRequest(String message) {
        this(message, null);
    }
}
