package com.tourmate.chat;

import reactor.core.publisher.Flux;

public interface AnswerGenerator {
    Flux<String> generate(GenerationContext context);
}
