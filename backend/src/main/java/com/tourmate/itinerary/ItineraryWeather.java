package com.tourmate.itinerary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ItineraryWeather {
    List<Forecast> fetch(TripRequirements requirements);
    record Forecast(LocalDate date, boolean available, String condition, BigDecimal low, BigDecimal high,
                    String source, String message) {
        public static Forecast unavailable(LocalDate date, String message) {
            return new Forecast(date, false, null, null, null, null, message);
        }
    }
}
