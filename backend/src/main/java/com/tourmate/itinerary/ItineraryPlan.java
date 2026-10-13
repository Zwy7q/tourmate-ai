package com.tourmate.itinerary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ItineraryPlan(List<Day> days, BigDecimal ticketSubtotal, String budgetNote) {
    public ItineraryPlan { days = List.copyOf(days); }
    public record Day(LocalDate date, List<Stop> stops, ItineraryWeather.Forecast weather, List<Stop> rainAlternatives) {
        public Day { stops = List.copyOf(stops); rainAlternatives = List.copyOf(rainAlternatives); }
    }
    public record Stop(String documentId, String name, String period, BigDecimal visitHours,
                       BigDecimal ticketPrice, String source, boolean demo) {}
}
