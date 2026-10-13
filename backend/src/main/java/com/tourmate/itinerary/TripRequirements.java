package com.tourmate.itinerary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** null 表示本轮没有提供该字段；预算按单人人民币解释。 */
public record TripRequirements(String city, LocalDate startDate, Integer days, BigDecimal budget,
                               String preferences, List<Integer> indoorDays) {
    public TripRequirements {
        if (city != null) city = city.trim().replaceFirst("市$", "");
        if (indoorDays != null) indoorDays = List.copyOf(indoorDays);
    }
    public static TripRequirements empty() { return new TripRequirements(null, null, null, null, null, null); }
    public TripRequirements merge(TripRequirements patch) {
        return new TripRequirements(patch.city == null ? city : patch.city,
                patch.startDate == null ? startDate : patch.startDate,
                patch.days == null ? days : patch.days, patch.budget == null ? budget : patch.budget,
                patch.preferences == null ? preferences : patch.preferences,
                patch.indoorDays == null ? indoorDays : patch.indoorDays);
    }
}
