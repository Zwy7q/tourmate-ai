package com.tourmate.tool;

import org.springframework.stereotype.Component;

@Component
public class DeterministicWeatherTool implements WeatherTool {
    @Override
    public WeatherResult query(String city, String date) {
        return new WeatherResult(city == null || city.isBlank() ? "北京" : city,
                date == null || date.isBlank() ? "明天" : date, "多云", 22);
    }
}
