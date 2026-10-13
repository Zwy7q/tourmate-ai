package com.tourmate.chat;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
@Profile("!deepseek")
public class OfflineAnswerGenerator implements AnswerGenerator {
    @Override public Flux<String> generate(GenerationContext context) {
        return Flux.just(context.baseline().answer());
    }
}
