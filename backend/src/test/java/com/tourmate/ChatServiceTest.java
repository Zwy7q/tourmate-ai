package com.tourmate;

import com.tourmate.chat.*;
import com.tourmate.agent.*;
import com.tourmate.api.ChatRequest;
import com.tourmate.conversation.*;
import com.tourmate.knowledge.*;
import com.tourmate.refund.RefundService;
import com.tourmate.tool.DeterministicWeatherTool;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;

class ChatServiceTest {
    private final InMemoryConversationStore store = new InMemoryConversationStore();
    private final TourmateAgent agent = new TourmateAgent(new IntentRouter(), new InMemoryKnowledgeRepository(),
            new DeterministicWeatherTool(), new RefundService());

    @Test void providesHistoryAndKnowledgeToGeneratorAndSavesActualAnswer() {
        store.append("c1", new ConversationMessage("user", "故宫门票多少钱？"));
        var seen = new AtomicReference<GenerationContext>();
        AnswerGenerator generator = context -> { seen.set(context); return Flux.just("学生票", "以官方公告为准。"); };
        var service = new ChatService(agent, store, generator);
        var answer = service.answer(new ChatRequest("那学生票呢？", "c1"));
        assertThat(seen.get().history()).hasSize(1);
        assertThat(seen.get().question()).contains("故宫");
        assertThat(seen.get().baseline().citations()).isNotEmpty();
        assertThat(answer.answer()).isEqualTo("学生票以官方公告为准。");
        assertThat(store.recent("c1").getLast().content()).isEqualTo(answer.answer());
    }

    @Test void emitsActualChunksBeforeFinalAnswer() {
        var service = new ChatService(agent, store, context -> Flux.just("你好", "游客"));
        var events = service.stream(new ChatRequest("你好", "c2")).collectList().block();
        assertThat(events).extracting(event -> event.type()).containsExactly("meta", "delta", "delta", "done");
        assertThat(events.get(1).data()).isEqualTo("你好");
        assertThat(((AgentResponse) events.getLast().data()).answer()).isEqualTo("你好游客");
    }

    @Test void failedGenerationDoesNotSaveFabricatedAssistantReply() {
        var service = new ChatService(agent, store, context -> Flux.error(new IllegalStateException("upstream failed")));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.answer(new ChatRequest("你好", "failure")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(store.recent("failure")).isEmpty();
    }
}
