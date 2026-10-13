package com.tourmate.tools;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

@SpringBootApplication
public class ToolsServerApplication {
    public static void main(String[] args) { SpringApplication.run(ToolsServerApplication.class, args); }

    @Bean WeatherTools weatherTools() {
        return new WeatherTools("https://api.open-meteo.com/v1/forecast", Clock.system(ZoneId.of("Asia/Shanghai")));
    }
    @Bean DemoOrderTools demoOrderTools() { return new DemoOrderTools(); }

    @Bean ToolCallbackProvider travelTools(WeatherTools weather, DemoOrderTools orders) {
        return MethodToolCallbackProvider.builder().toolObjects(weather, orders).build();
    }
}
