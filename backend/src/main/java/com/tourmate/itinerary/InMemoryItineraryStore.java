package com.tourmate.itinerary;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!redis")
public class InMemoryItineraryStore implements ItineraryStore {
    private final ConcurrentHashMap<String, TripState> states = new ConcurrentHashMap<>();
    public Optional<TripState> find(String id) { return Optional.ofNullable(states.get(id)); }
    public boolean save(String id, long expectedVersion, TripState next) {
        var saved = new AtomicBoolean();
        states.compute(id, (key, current) -> {
            if ((current == null ? 0 : current.version()) != expectedVersion || next.version() != expectedVersion + 1) return current;
            saved.set(true); return next;
        });
        return saved.get();
    }
}
