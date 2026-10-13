package com.tourmate;

import com.sun.net.httpserver.HttpServer;
import com.tourmate.itinerary.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import static org.assertj.core.api.Assertions.*;

class ItineraryModelTest {
    @Test void sendsContextAndParsesOnlyStructuredRequirementsAndDocumentIds() throws Exception {
        var request = new AtomicReference<String>();
        var content = new AtomicReference<>("{\"city\":\"杭州\",\"startDate\":\"2026-10-17\",\"days\":2,\"budget\":1000,\"preferences\":\"慢节奏\",\"indoorDays\":null}");
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var json = new com.fasterxml.jackson.databind.ObjectMapper();
            var body = "{\"id\":\"test\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"deepseek-chat\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":" + json.writeValueAsString(content.get()) + "},\"finish_reason\":\"stop\"}]}";
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try {
            var chat = OpenAiChatModel.builder().openAiApi(OpenAiApi.builder().baseUrl("http://127.0.0.1:" + server.getAddress().getPort()).apiKey("test-key").build()).build();
            var model = new DeepSeekItineraryModel(chat, Clock.fixed(Instant.parse("2026-10-13T02:00:00Z"), ZoneId.of("Asia/Shanghai")));
            var requirements = model.extract("周末去杭州两天，预算1000", TripRequirements.empty());
            assertThat(requirements.startDate()).isEqualTo(LocalDate.parse("2026-10-17"));
            assertThat(request.get()).contains("2026-10-13", "Asia/Shanghai", "预算1000");
            content.set("{\"days\":[[\"doc-1\"],[\"doc-2\"]]}");
            assertThat(model.schedule(requirements, List.of(), List.of())).isEqualTo(List.of(List.of("doc-1"), List.of("doc-2")));
            assertThat(request.get()).contains("2026-10-17");
            content.set("{\"days\":1.5}");
            assertThatThrownBy(() -> model.extract("乱数", TripRequirements.empty())).isInstanceOf(IllegalStateException.class);
            content.set("{\"preferences\":[\"自然风景\",\"慢节奏\"]}");
            assertThat(model.extract("偏好", TripRequirements.empty()).preferences()).isEqualTo("自然风景，慢节奏");
            content.set("不是JSON");
            assertThatThrownBy(() -> model.extract("失败", TripRequirements.empty())).isInstanceOf(IllegalStateException.class);
        } finally { server.stop(0); }
    }

    @Test void weatherLimitsCallsAndMarksFailuresAndOutOfRangeDates() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var clock = Clock.fixed(Instant.parse("2026-10-13T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
        var weather = new McpItineraryWeather((city, date) -> {
            calls.incrementAndGet(); throw new IllegalStateException("upstream failure");
        }, clock);
        var requirements = new TripRequirements("杭州", LocalDate.parse("2026-10-14"), 7, new BigDecimal("1000"), null, null);
        var forecasts = weather.fetch(requirements);
        assertThat(calls.get()).isEqualTo(4);
        assertThat(forecasts).hasSize(7).allMatch(f -> !f.available());
        assertThat(forecasts.getLast().message()).contains("预报范围");
    }
}
