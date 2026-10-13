package com.tourmate.conversation;

import java.time.Duration;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("redis")
public class RedisConversationStore implements ConversationStore {
    private static final Duration TTL = Duration.ofHours(24);
    private final StringRedisTemplate redis;

    public RedisConversationStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void append(String conversationId, ConversationMessage message) {
        var key = "tourmate:conversation:" + conversationId;
        redis.opsForList().rightPush(key, message.role() + "\t" + message.content());
        redis.opsForList().trim(key, -20, -1);
        redis.expire(key, TTL);
    }

    @Override
    public List<ConversationMessage> recent(String conversationId) {
        var values = redis.opsForList().range("tourmate:conversation:" + conversationId, 0, -1);
        if (values == null) return List.of();
        return values.stream().map(value -> {
            var split = value.split("\\t", 2);
            return new ConversationMessage(split[0], split.length == 2 ? split[1] : "");
        }).toList();
    }
}
