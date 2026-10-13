package com.tourmate.itinerary;

import java.util.Optional;

public interface ItineraryStore {
    Optional<TripState> find(String conversationId);
    boolean save(String conversationId, long expectedVersion, TripState next);
}
