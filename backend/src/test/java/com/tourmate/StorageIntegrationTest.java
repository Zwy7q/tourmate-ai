package com.tourmate;

import com.tourmate.knowledge.*;
import com.tourmate.conversation.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.UUID;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles({"postgres", "redis"})
@EnabledIfEnvironmentVariable(named = "TOURMATE_INTEGRATION", matches = "true")
class StorageIntegrationTest {
    @Autowired KnowledgeRepository knowledge;
    @Autowired ConversationStore conversations;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;

    @Test void savesRealPgvectorAndRetrievesChinesePolicy() {
        var id = UUID.randomUUID().toString();
        try {
            knowledge.save(new KnowledgeDocument(id, "测试海洋馆", "测试海洋馆潜水票提前两日退订", "refund-policy", "2026-10-13"));
            assertThat(knowledge.search("测试海洋馆潜水票", 10)).anyMatch(result -> result.document().id().equals(id));
            assertThat(jdbc.queryForObject("SELECT vector_dims(embedding) FROM knowledge_chunks WHERE document_id = ?::uuid", Integer.class, id))
                    .isEqualTo(1536);
        } finally {
            jdbc.update("DELETE FROM knowledge_documents WHERE id = ?::uuid", id);
        }
    }

    @Test void preservesTimestampAndWindowInRealRedis() {
        var id = UUID.randomUUID().toString();
        var key = "tourmate:conversation:" + id;
        var timestamp = Instant.parse("2026-10-13T06:00:00Z");
        try {
            for (int i = 0; i < 25; i++) conversations.append(id, new ConversationMessage("user", "消息\t" + i, timestamp));
            var messages = conversations.recent(id);
            assertThat(messages).hasSize(20);
            assertThat(messages.getFirst().content()).isEqualTo("消息\t5");
            assertThat(messages.getFirst().createdAt()).isEqualTo(timestamp);
            assertThat(redis.getExpire(key)).isPositive();
        } finally {
            redis.delete(key);
        }
    }
}
