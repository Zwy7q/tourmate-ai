package com.tourmate.tools;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class WeatherToolsTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-13T04:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @Test void queriesRequestedCityAndDateWithSourceAndValidatesInputs() throws Exception {
        var query = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/forecast", exchange -> {
            query.set(exchange.getRequestURI().getQuery());
            var json = """
                    {"daily":{"time":["2026-10-14"],"temperature_2m_max":[23.5],"temperature_2m_min":[16.0],"weather_code":[61]}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, json.length);
            exchange.getResponseBody().write(json);
            exchange.close();
        });
        server.start();
        try {
            var tool = new WeatherTools("http://127.0.0.1:" + server.getAddress().getPort() + "/forecast", CLOCK);
            var result = tool.getWeather("杭州", "2026-10-14");
            assertThat(result.success()).isTrue();
            assertThat(result.city()).isEqualTo("杭州");
            assertThat(result.date()).isEqualTo("2026-10-14");
            assertThat(result.condition()).isEqualTo("小雨");
            assertThat(result.maxTemperatureCelsius()).isEqualTo(23.5);
            assertThat(result.source()).isEqualTo("Open-Meteo");
            assertThat(query.get()).contains("latitude=30.2741", "longitude=120.1551", "start_date=2026-10-14", "timezone=Asia/Shanghai");
            assertThat(tool.getWeather("未知城市", "2026-10-14").success()).isFalse();
            assertThat(tool.getWeather("杭州", "2026-12-01").success()).isFalse();
            assertThat(tool.getWeather("杭州", "不是日期").success()).isFalse();
        } finally { server.stop(0); }
    }

    @Test void unavailableUpstreamDoesNotProduceDemoWeather() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/forecast", exchange -> { exchange.sendResponseHeaders(503, -1); exchange.close(); });
        server.start();
        try {
            var result = new WeatherTools("http://127.0.0.1:" + server.getAddress().getPort() + "/forecast", CLOCK).getWeather("北京", "2026-10-14");
            assertThat(result.success()).isFalse();
            assertThat(result.maxTemperatureCelsius()).isNull();
            assertThat(result.message()).contains("暂时不可用");
        } finally { server.stop(0); }
    }
}
