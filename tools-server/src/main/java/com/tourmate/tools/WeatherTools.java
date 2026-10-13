package com.tourmate.tools;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

public class WeatherTools {
    private static final Map<String, double[]> CITIES = Map.ofEntries(
            Map.entry("北京", new double[]{39.9042, 116.4074}),
            Map.entry("上海", new double[]{31.2304, 121.4737}),
            Map.entry("杭州", new double[]{30.2741, 120.1551}),
            Map.entry("南京", new double[]{32.0603, 118.7969}),
            Map.entry("苏州", new double[]{31.2989, 120.5853}),
            Map.entry("成都", new double[]{30.5728, 104.0668}),
            Map.entry("西安", new double[]{34.3416, 108.9398}),
            Map.entry("广州", new double[]{23.1291, 113.2644}),
            Map.entry("深圳", new double[]{22.5431, 114.0579}),
            Map.entry("武汉", new double[]{30.5928, 114.3055}),
            Map.entry("桂林", new double[]{25.2742, 110.2900}),
            Map.entry("厦门", new double[]{24.4798, 118.0894}));
    private final String endpoint;
    private final Clock clock;
    private final RestClient client;

    public WeatherTools(String endpoint, Clock clock) {
        this.endpoint = endpoint;
        this.clock = clock;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    @Tool(name = "get_weather", description = "查询城市的每日天气预报。支持北京、上海、杭州、南京、苏州、成都、西安、广州、深圳、武汉、桂林、厦门；date 必须为今天至未来第6天的 YYYY-MM-DD，时区 Asia/Shanghai。不要猜测用户未指定的城市。")
    public WeatherResponse getWeather(@ToolParam(description = "用户指定的中文城市名，例如杭州") String city,
                                      @ToolParam(description = "ISO 日期 YYYY-MM-DD") String date) {
        var name = city == null ? "" : city.trim().replaceFirst("市$", "");
        var coordinates = CITIES.get(name);
        if (coordinates == null) return failure(name, date, "城市未指定或暂不支持，请选择工具说明中的城市");
        LocalDate day;
        try { day = LocalDate.parse(date == null ? "" : date); }
        catch (DateTimeParseException exception) { return failure(name, date, "日期必须为 YYYY-MM-DD"); }
        var today = LocalDate.now(clock);
        if (day.isBefore(today) || day.isAfter(today.plusDays(6))) return failure(name, date, "只支持今天至未来第6天的天气预报");
        try {
            var response = client.get().uri(java.net.URI.create(endpoint + "?latitude=" + coordinates[0] + "&longitude=" + coordinates[1]
                    + "&daily=temperature_2m_max,temperature_2m_min,weather_code&timezone=Asia%2FShanghai"
                    + "&start_date=" + day + "&end_date=" + day)).retrieve().body(JsonNode.class);
            var daily = response == null ? null : response.path("daily");
            if (daily == null || !day.toString().equals(daily.path("time").path(0).asText())
                    || !daily.path("temperature_2m_max").path(0).isNumber()
                    || !daily.path("temperature_2m_min").path(0).isNumber()
                    || !daily.path("weather_code").path(0).isInt()) return failure(name, date, "天气服务返回的数据不完整");
            double high = daily.path("temperature_2m_max").path(0).asDouble();
            double low = daily.path("temperature_2m_min").path(0).asDouble();
            if (!Double.isFinite(high) || !Double.isFinite(low)) return failure(name, date, "天气服务返回的数据无效");
            return new WeatherResponse(true, name, day.toString(), condition(daily.path("weather_code").path(0).asInt()),
                    high, low, "Open-Meteo", clock.instant().toString(), "天气预报可能变化，请出行前再次确认");
        } catch (RuntimeException exception) {
            return failure(name, date, "天气服务暂时不可用，请稍后重试");
        }
    }

    private WeatherResponse failure(String city, String date, String message) {
        return new WeatherResponse(false, city, date, null, null, null, "Open-Meteo", clock.instant().toString(), message);
    }

    private String condition(int code) {
        return switch (code) {
            case 0 -> "晴"; case 1 -> "大致晴朗"; case 2 -> "局部多云"; case 3 -> "阴天";
            case 45, 48 -> "雾"; case 51, 53, 55 -> "毛毛雨"; case 56, 57 -> "冻毛毛雨";
            case 61 -> "小雨"; case 63 -> "中雨"; case 65 -> "大雨"; case 66, 67 -> "冻雨";
            case 71, 73, 75, 77 -> "降雪"; case 80, 81, 82 -> "阵雨";
            case 85, 86 -> "阵雪"; case 95, 96, 99 -> "雷暴";
            default -> "未知天气代码（" + code + "）";
        };
    }
    public record WeatherResponse(boolean success, String city, String date, String condition,
                                  Double maxTemperatureCelsius, Double minTemperatureCelsius,
                                  String source, String fetchedAt, String message) {}
}
