package com.tourmate;

import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourmate.chat.*;
import com.tourmate.agent.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import static org.assertj.core.api.Assertions.assertThat;

class AlibabaAgentTest {
    @Test void performsModelToolModelLoopAndStreamsOnlyAnswerText() throws Exception {
        var calls = new AtomicInteger();
        var requests = java.util.Collections.synchronizedList(new ArrayList<String>());
        var toolCalls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            boolean first = calls.getAndIncrement() == 0;
            var delta = first ? "{\"role\":\"assistant\",\"tool_calls\":[{\"index\":0,\"id\":\"call_weather\",\"type\":\"function\",\"function\":{\"name\":\"get_weather\",\"arguments\":\"{\\\"city\\\":\\\"杭州\\\",\\\"date\\\":\\\"2026-10-14\\\"}\"}}]}"
                    : "{\"role\":\"assistant\",\"content\":\"杭州明天小雨，建议带伞。\"}";
            var finish = first ? "tool_calls" : "stop";
            var preamble = first ? "data: {\"id\":\"test\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"deepseek-chat\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"我先查询天气。\"},\"finish_reason\":null}]}\n\n" : "";
            var json = preamble + "data: {\"id\":\"test\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"deepseek-chat\",\"choices\":[{\"index\":0,\"delta\":" + delta + ",\"finish_reason\":null}]}\n\n"
                    + "data: {\"id\":\"test\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"deepseek-chat\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"" + finish + "\"}]}\n\ndata: [DONE]\n\n";
            var bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var api = OpenAiApi.builder().baseUrl("http://127.0.0.1:" + server.getAddress().getPort()).apiKey("test-key").build();
            var model = OpenAiChatModel.builder().openAiApi(api).defaultOptions(OpenAiChatOptions.builder().model("deepseek-chat").build()).build();
            var tool = new ToolCallback() {
                public ToolDefinition getToolDefinition() {
                    return ToolDefinition.builder().name("get_weather").description("查询天气")
                            .inputSchema("{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"},\"date\":{\"type\":\"string\"}},\"required\":[\"city\",\"date\"]}").build();
                }
                public String call(String input) {
                    toolCalls.incrementAndGet();
                    assertThat(input).contains("杭州", "2026-10-14");
                    return "{\"success\":true,\"condition\":\"小雨\",\"source\":\"Open-Meteo\"}";
                }
            };
            var generator = new AlibabaAgentAnswerGenerator(model, new ToolCallback[]{tool});
            var baseline = new AgentResponse(Intent.WEATHER, "请查询天气", List.of(), false, null);
            var answer = generator.generate(new GenerationContext("杭州明天天气？", List.of(), baseline)).collectList().block(Duration.ofSeconds(20));
            assertThat(String.join("", answer)).isEqualTo("杭州明天小雨，建议带伞。");
            assertThat(toolCalls.get()).isEqualTo(1);
            assertThat(requests).hasSize(2);
            var second = new ObjectMapper().readTree(requests.get(1));
            assertThat(second.path("messages").toString()).contains("\"role\":\"tool\"", "小雨", "call_weather");
        } finally { server.stop(0); }
    }
}
