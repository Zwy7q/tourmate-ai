package com.tourmate.conversation;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {
    private final ConversationStore store;

    public ConversationController(ConversationStore store) {
        this.store = store;
    }

    @GetMapping("/{conversationId}/messages")
    public List<ConversationMessage> messages(@PathVariable String conversationId) {
        return store.recent(conversationId);
    }
}
