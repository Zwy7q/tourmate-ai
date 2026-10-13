package com.tourmate.itinerary;

import java.util.List;
import java.util.stream.IntStream;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!deepseek | !agent")
public class UnavailableItineraryWeather implements ItineraryWeather {
    public List<Forecast> fetch(TripRequirements r) {
        return IntStream.range(0, r.days()).mapToObj(i -> Forecast.unavailable(r.startDate().plusDays(i), "未启用MCP天气工具")).toList();
    }
}
