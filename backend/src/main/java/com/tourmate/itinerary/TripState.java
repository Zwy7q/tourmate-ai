package com.tourmate.itinerary;

import java.util.List;

public record TripState(long version, TripRequirements requirements, ItineraryPlan plan,
                        String status, String clarification, List<String> warnings) {
    public TripState { warnings = List.copyOf(warnings); }
}
