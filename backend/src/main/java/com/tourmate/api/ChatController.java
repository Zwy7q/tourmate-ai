package com.tourmate.api;

import com.tourmate.agent.AgentResponse;
import com.tourmate.agent.TourmateAgent;
import com.tourmate.refund.RefundService;
import com.tourmate.conversation.ConversationMessage;
import com.tourmate.conversation.ConversationStore;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
public class ChatController {
    private final TourmateAgent agent;
    private final RefundService refundService;
    private final ConversationStore conversationStore;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ChatController(TourmateAgent agent, RefundService refundService, ConversationStore conversationStore) {
        this.agent = agent;
        this.refundService = refundService;
        this.conversationStore = conversationStore;
    }

    @PostMapping("/chat")
    public AgentResponse chat(@Valid @RequestBody ChatRequest request) {
        var response = agent.answer(request.message());
        saveConversation(request, response);
        return response;
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatRequest request) {
        var emitter = new SseEmitter(30_000L);
        executor.submit(() -> {
            try {
                var response = agent.answer(request.message());
                saveConversation(request, response);
                emitter.send(SseEmitter.event().name("meta").data(Map.of("intent", response.intent())));
                var answer = response.answer();
                for (var part : answer.split("(?<=。)|(?<=！)|(?<=？)")) {
                    if (!part.isBlank()) emitter.send(SseEmitter.event().name("delta").data(part));
                }
                emitter.send(SseEmitter.event().name("done").data(response));
                emitter.complete();
            } catch (Exception exception) {
                emitter.completeWithError(exception);
            }
        });
        return emitter;
    }

    private void saveConversation(ChatRequest request, AgentResponse response) {
        if (request.conversationId() != null && !request.conversationId().isBlank()) {
            conversationStore.append(request.conversationId(), new ConversationMessage("user", request.message()));
            conversationStore.append(request.conversationId(), new ConversationMessage("assistant", response.answer()));
        }
    }

    @PostMapping("/refunds/confirm")
    public Map<String, Object> confirm(@RequestBody Map<String, String> request) {
        var success = refundService.confirm(request.get("token"));
        return Map.of("success", success, "status", success ? "REFUND_REQUESTED" : "INVALID_OR_EXPIRED_TOKEN");
    }
}
