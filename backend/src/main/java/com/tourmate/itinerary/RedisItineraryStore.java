package com.tourmate.itinerary;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@Profile("redis")
public class RedisItineraryStore implements ItineraryStore {
    private final StringRedisTemplate redis;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final DefaultRedisScript<Long> compareAndSet = new DefaultRedisScript<>("""
        local old = redis.call('GET', KEYS[1])
        local version = 0
        if old then version = cjson.decode(old).version end
        if version ~= tonumber(ARGV[1]) then return 0 end
        if cjson.decode(ARGV[2]).version ~= version + 1 then return 0 end
        redis.call('SET', KEYS[1], ARGV[2], 'EX', 86400)
        return 1
        """, Long.class);

    public RedisItineraryStore(StringRedisTemplate redis) { this.redis = redis; }
    public Optional<TripState> find(String id) {
        var value = redis.opsForValue().get("tourmate:itinerary:" + id);
        if (value == null) return Optional.empty();
        try { return Optional.of(json.readValue(value, TripState.class)); }
        catch (Exception exception) { throw new IllegalStateException("行程状态读取失败"); }
    }
    public boolean save(String id, long expectedVersion, TripState state) {
        try {
            return Long.valueOf(1).equals(redis.execute(compareAndSet, List.of("tourmate:itinerary:" + id),
                    Long.toString(expectedVersion), json.writeValueAsString(state)));
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) { throw new IllegalStateException("行程状态保存失败"); }
    }
}
