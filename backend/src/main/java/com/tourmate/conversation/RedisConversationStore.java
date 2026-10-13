package com.tourmate.conversation;

import java.time.Duration;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("redis")
public class RedisConversationStore implements ConversationStore {
    private static final Duration TTL = Duration.ofHours(24);
    private final StringRedisTemplate redis;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final DefaultRedisScript<Long> appendScript = new DefaultRedisScript<>("""
        redis.call('RPUSH', KEYS[1], ARGV[1])
        redis.call('LTRIM', KEYS[1], -20, -1)
        redis.call('EXPIRE', KEYS[1], ARGV[2])
        return 1
        """, Long.class);

    public RedisConversationStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void append(String conversationId, ConversationMessage message) {
        var key = "tourmate:conversation:" + conversationId;
        try {
            redis.execute(appendScript, List.of(key), json.writeValueAsString(message), Long.toString(TTL.toSeconds()));
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("会话消息序列化失败", exception);
        }
    }

    @Override
    public List<ConversationMessage> recent(String conversationId) {
        var values = redis.opsForList().range("tourmate:conversation:" + conversationId, 0, -1);
        if (values == null) return List.of();
        return values.stream().map(value -> {
            try {
                return json.readValue(value, ConversationMessage.class);
            } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
                throw new IllegalStateException("会话消息读取失败", exception);
            }
        }).toList();
    }
}
