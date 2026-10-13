package com.tourmate.chat;

import com.tourmate.agent.AgentResponse;
import com.tourmate.agent.TourmateAgent;
import com.tourmate.api.ChatRequest;
import com.tourmate.api.StreamEvent;
import com.tourmate.conversation.ConversationMessage;
import com.tourmate.conversation.ConversationStore;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class ChatService {
    private final TourmateAgent agent;
    private final ConversationStore store;
    private final AnswerGenerator generator;

    public ChatService(TourmateAgent agent, ConversationStore store, AnswerGenerator generator) {
        this.agent = agent; this.store = store; this.generator = generator;
    }

    public AgentResponse answer(ChatRequest request) {
        var events = stream(request).collectList().block(java.time.Duration.ofSeconds(120));
        return (AgentResponse) events.getLast().data();
    }

    public Flux<StreamEvent> stream(ChatRequest request) {
        return Flux.defer(() -> {
            var history = request.conversationId() == null || request.conversationId().isBlank()
                    ? List.<ConversationMessage>of() : store.recent(request.conversationId());
            var question = resolveQuestion(request.message(), history);
            var baseline = agent.answer(question);
            var context = new GenerationContext(question, history, baseline);
            var answer = new StringBuilder();
            var deltas = Flux.defer(() -> generator.generate(context)).map(part -> {
                answer.append(part);
                return new StreamEvent("delta", part);
            }).onErrorMap(error -> new IllegalStateException("生成服务暂时不可用", error));
            var done = Mono.fromSupplier(() -> {
                if (answer.isEmpty()) throw new IllegalStateException("模型未返回回答");
                var result = new AgentResponse(baseline.intent(), answer.toString(), baseline.citations(),
                        baseline.confirmationRequired(), baseline.confirmationToken());
                if (request.conversationId() != null && !request.conversationId().isBlank()) {
                    store.append(request.conversationId(), new ConversationMessage("user", request.message()));
                    store.append(request.conversationId(), new ConversationMessage("assistant", result.answer()));
                }
                return new StreamEvent("done", result);
            });
            return Flux.concat(Flux.just(new StreamEvent("meta", Map.of("intent", baseline.intent()))), deltas, done);
        });
    }

    private String resolveQuestion(String message, List<ConversationMessage> history) {
        if (!(message.startsWith("那") || message.startsWith("它") || message.startsWith("如果"))) return message;
        for (int i = history.size() - 1; i >= 0; i--) {
            var previous = history.get(i);
            if (previous.role().equals("user")) return previous.content() + "；追问：" + message;
        }
        return message;
    }
}
