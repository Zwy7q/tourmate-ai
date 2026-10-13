package com.tourmate.itinerary;

import java.util.List;

public interface ItineraryModel {
    TripRequirements extract(String message, TripRequirements current);
    List<List<String>> schedule(TripRequirements requirements, List<AttractionCatalog.Attraction> attractions,
                                List<ItineraryWeather.Forecast> forecasts);
    default List<List<String>> schedule(TripRequirements requirements, List<AttractionCatalog.Attraction> attractions,
                                        List<ItineraryWeather.Forecast> forecasts, ItineraryPlan previous, List<List<String>> fixedDays) {
        return schedule(requirements, attractions, forecasts);
    }
}
