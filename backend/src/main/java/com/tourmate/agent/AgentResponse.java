package com.tourmate.agent;

import com.tourmate.knowledge.KnowledgeSearchResult;
import java.util.List;

public record AgentResponse(
        Intent intent,
        String answer,
        List<KnowledgeSearchResult> citations,
        boolean confirmationRequired,
        String confirmationToken) {
}
