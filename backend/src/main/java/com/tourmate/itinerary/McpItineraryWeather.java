package com.tourmate.itinerary;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("deepseek & agent")
public class McpItineraryWeather implements ItineraryWeather {
    private final BiFunction<String, LocalDate, Forecast> lookup;
    private final Clock clock;
    @Autowired
    public McpItineraryWeather(McpSyncClient client) {
        this((city, date) -> query(client, city, date), Clock.system(java.time.ZoneId.of("Asia/Shanghai")));
    }
    public McpItineraryWeather(BiFunction<String, LocalDate, Forecast> lookup, Clock clock) { this.lookup = lookup; this.clock = clock; }

    public List<Forecast> fetch(TripRequirements r) {
        var forecasts = new ArrayList<Forecast>(); int calls = 0;
        for (int i = 0; i < r.days(); i++) {
            var date = r.startDate().plusDays(i);
            if (date.isBefore(LocalDate.now(clock)) || date.isAfter(LocalDate.now(clock).plusDays(6))) {
                forecasts.add(Forecast.unavailable(date, "超出天气预报范围")); continue;
            }
            if (calls >= 4) { forecasts.add(Forecast.unavailable(date, "达到本次天气查询上限")); continue; }
            calls++;
            try {
                var forecast = lookup.apply(r.city(), date);
                if (forecast == null || !date.equals(forecast.date())) throw new IllegalStateException("日期不匹配");
                forecasts.add(forecast);
            } catch (RuntimeException exception) { forecasts.add(Forecast.unavailable(date, "天气查询失败，请出发前再次确认")); }
        }
        return List.copyOf(forecasts);
    }
    private static Forecast query(McpSyncClient client, String city, LocalDate date) {
        org.slf4j.LoggerFactory.getLogger(McpItineraryWeather.class).info("MCP_ITINERARY_TOOL_CALL get_weather");
        var result = client.callTool(new McpSchema.CallToolRequest("get_weather", Map.of("city", city, "date", date.toString())));
        if (Boolean.TRUE.equals(result.isError())) throw new IllegalStateException("工具返回错误");
        var text = result.content().stream().filter(McpSchema.TextContent.class::isInstance)
                .map(McpSchema.TextContent.class::cast).map(McpSchema.TextContent::text).findFirst().orElseThrow();
        try {
            var node = new ObjectMapper().readTree(text);
            if (!node.path("success").asBoolean() || !date.toString().equals(node.path("date").asText())
                    || !city.equals(node.path("city").asText()) || !node.path("minTemperatureCelsius").isNumber()
                    || !node.path("maxTemperatureCelsius").isNumber() || !"Open-Meteo".equals(node.path("source").asText())) {
                return Forecast.unavailable(date, "天气服务未返回有效预报");
            }
            return new Forecast(date, true, node.path("condition").asText(), node.path("minTemperatureCelsius").decimalValue(),
                    node.path("maxTemperatureCelsius").decimalValue(), "Open-Meteo", "预报可能变化，请出行前确认");
        } catch (Exception exception) { throw new IllegalStateException("天气数据无效"); }
    }
}
