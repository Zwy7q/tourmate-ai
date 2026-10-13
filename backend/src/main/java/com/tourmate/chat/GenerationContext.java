package com.tourmate.chat;

import com.tourmate.agent.AgentResponse;
import com.tourmate.conversation.ConversationMessage;
import java.util.List;

public record GenerationContext(String question, List<ConversationMessage> history, AgentResponse baseline) {}
