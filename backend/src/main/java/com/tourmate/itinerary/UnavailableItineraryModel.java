package com.tourmate.itinerary;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!deepseek")
public class UnavailableItineraryModel implements ItineraryModel {
    public TripRequirements extract(String message, TripRequirements current) { throw new ItineraryDisabledException(); }
    public List<List<String>> schedule(TripRequirements r, List<AttractionCatalog.Attraction> a, List<ItineraryWeather.Forecast> w) { throw new ItineraryDisabledException(); }
}
