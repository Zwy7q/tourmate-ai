package com.tourmate.tool;

public interface WeatherTool {
    WeatherResult query(String city, String date);

    record WeatherResult(String city, String date, String condition, int temperatureCelsius) {
    }
}
