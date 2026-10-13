package com.tourmate.chat;

import java.time.Duration;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
@Profile("deepseek & !agent")
public class DeepSeekAnswerGenerator implements AnswerGenerator {
    private final ChatClient client;
    public DeepSeekAnswerGenerator(ChatModel model) {
        this.client = ChatClient.builder(model).build();
    }

    @Override public Flux<String> generate(GenerationContext context) {
        return client.prompt().system(GenerationPrompt.system(false)).user(GenerationPrompt.user(context))
                .stream().content().timeout(Duration.ofSeconds(60));
    }
}
