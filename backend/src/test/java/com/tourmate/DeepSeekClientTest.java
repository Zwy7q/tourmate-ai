package com.tourmate;

import com.sun.net.httpserver.HttpServer;
import com.tourmate.chat.DeepSeekAnswerGenerator;
import com.tourmate.chat.GenerationContext;
import com.tourmate.agent.AgentResponse;
import com.tourmate.agent.Intent;
import com.tourmate.knowledge.*;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;

class DeepSeekClientTest {
    @Test void springAiSendsContextAndConsumesCompatibleStreamingResponse() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var requestBody = new AtomicReference<String>();
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            var response = """
                data: {"id":"test","object":"chat.completion.chunk","created":1,"model":"deepseek-chat","choices":[{"index":0,"delta":{"role":"assistant","content":"依据规则"},"finish_reason":null}]}

                data: {"id":"test","object":"chat.completion.chunk","created":1,"model":"deepseek-chat","choices":[{"index":0,"delta":{"content":" [1]"},"finish_reason":null}]}

                data: {"id":"test","object":"chat.completion.chunk","created":1,"model":"deepseek-chat","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

                data: [DONE]

                """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var out = exchange.getResponseBody()) { out.write(response); }
        });
        server.start();
        try {
            var api = OpenAiApi.builder().baseUrl("http://127.0.0.1:" + server.getAddress().getPort()).apiKey("test-only-key").build();
            var model = OpenAiChatModel.builder().openAiApi(api)
                    .defaultOptions(OpenAiChatOptions.builder().model("deepseek-chat").build()).build();
            var source = new KnowledgeDocument("policy", "退票说明", "参观前可以申请退票", "refund-policy", "2026-10-13");
            var baseline = new AgentResponse(Intent.REFUND_POLICY, "需要查阅政策", List.of(new KnowledgeSearchResult(source, 1)), false, null);
            var generator = new DeepSeekAnswerGenerator(model);
            var chunks = generator.generate(new GenerationContext("可以退票吗", List.of(), baseline)).collectList().block();
            assertThat(String.join("", chunks)).isEqualTo("依据规则 [1]");
            assertThat(requestBody.get()).contains("deepseek-chat", "参观前可以申请退票", "可以退票吗", "\"stream\":true");
        } finally { server.stop(0); }
    }
}
