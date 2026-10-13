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
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles({"postgres", "redis"})
@EnabledIfEnvironmentVariable(named = "TOURMATE_INTEGRATION", matches = "true")
class StorageIntegrationTest {
    @Autowired com.tourmate.itinerary.ItineraryStore itineraries;
    @Autowired KnowledgeRepository knowledge;
    @Autowired ConversationStore conversations;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @Autowired KnowledgeReindexService reindex;

    @Test void storesItineraryInRedisWithAtomicVersionAndExpiry() {
        var id = UUID.randomUUID().toString();
        try {
            var requirements = new com.tourmate.itinerary.TripRequirements("杭州", java.time.LocalDate.parse("2026-10-14"), 2,
                    new java.math.BigDecimal("1000"), "慢节奏", java.util.List.of(2));
            var state = new com.tourmate.itinerary.TripState(1, requirements, null, "NEEDS_KNOWLEDGE", "导入资料", java.util.List.of());
            assertThat(itineraries.save(id, 0, state)).isTrue();
            assertThat(itineraries.save(id, 0, state)).isFalse();
            assertThat(itineraries.find(id).orElseThrow()).isEqualTo(state);
            assertThat(redis.getExpire("tourmate:itinerary:" + id)).isBetween(86300L, 86400L);
            assertThat(itineraries.save(id, 1, new com.tourmate.itinerary.TripState(2, requirements, null, "NEEDS_KNOWLEDGE", "更新", java.util.List.of()))).isTrue();
            assertThat(itineraries.find(id).orElseThrow().version()).isEqualTo(2);
        } finally { redis.delete("tourmate:itinerary:" + id); }
    }

    @Test void skipsConcurrentEditsInsteadOfStoringAStaleVector() throws Exception {
        var id = UUID.randomUUID().toString();
        var once = new AtomicBoolean();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/embed", exchange -> {
            exchange.getRequestBody().readAllBytes();
            if (once.compareAndSet(false, true)) jdbc.update("UPDATE knowledge_chunks SET content='新的票务政策' WHERE document_id=?::uuid", id);
            var bytes = ("{\"embeddings\":[[1" + ",0".repeat(1023) + "]]}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            knowledge.save(new KnowledgeDocument(id, "并发重建测试", "旧的票务政策", "ticket-price", null));
            var local = new EmbeddingClient("http://127.0.0.1:" + server.getAddress().getPort() + "/api/embed", "", "qwen3-embedding:0.6b", "ollama");
            var result = new KnowledgeReindexService(jdbc, local).rebuild(20, id);
            assertThat(result.updated()).isZero();
            assertThat(result.skipped()).isEqualTo(1);
            assertThat(result.remaining()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT content FROM knowledge_chunks WHERE document_id=?::uuid", String.class, id)).isEqualTo("新的票务政策");
            assertThat(jdbc.queryForObject("SELECT embedding_local IS NULL FROM knowledge_chunks WHERE document_id=?::uuid", Boolean.class, id)).isTrue();
        } finally {
            server.stop(0);
            jdbc.update("DELETE FROM knowledge_documents WHERE id=?::uuid", id);
        }
    }

    @Test void migratesToNativeLocalDimensionsAndPreservesOldVectorsWhenServiceFails() throws Exception {
        var id = UUID.randomUUID().toString();
        var status = new AtomicInteger(503);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/embed", exchange -> {
            exchange.getRequestBody().readAllBytes();
            var bytes = ("{\"embeddings\":[[1" + ",0".repeat(1023) + "]]}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status.get(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            knowledge.save(new KnowledgeDocument(id, "本地模型隔离", "本地模型隔离测试学生票18元", "ticket-price", null));
            var old = jdbc.queryForObject("SELECT embedding::text FROM knowledge_chunks WHERE document_id=?::uuid", String.class, id);
            var local = new EmbeddingClient("http://127.0.0.1:" + server.getAddress().getPort() + "/api/embed", "", "qwen3-embedding:0.6b", "ollama");
            var maintenance = new KnowledgeReindexService(jdbc, local);
            var failed = maintenance.rebuild(20, id);
            assertThat(failed.updated()).isZero();
            assertThat(failed.failedChunkIds()).hasSize(1);
            assertThat(failed.remaining()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT embedding::text FROM knowledge_chunks WHERE document_id=?::uuid", String.class, id)).isEqualTo(old);
            status.set(200);
            assertThat(maintenance.rebuild(20, id).updated()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT vector_dims(embedding_local) FROM knowledge_chunks WHERE document_id=?::uuid", Integer.class, id)).isEqualTo(1024);
            assertThat(new JdbcKnowledgeRepository(jdbc, local).search("本地模型隔离测试学生票", 20)).anyMatch(result -> result.document().id().equals(id));
            assertThat(knowledge.search("本地模型隔离测试学生票", 20)).noneMatch(result -> result.document().id().equals(id));
        } finally {
            server.stop(0);
            jdbc.update("DELETE FROM knowledge_documents WHERE id=?::uuid", id);
        }
    }

    @Test void rebuildsLegacyVectorsWithoutLosingTextAndCanResume() {
        var id = UUID.randomUUID().toString();
        var content = "重建测试学生优惠".repeat(150);
        try {
            knowledge.save(new KnowledgeDocument(id, "重建测试", content, "ticket-price", null));
            var original = jdbc.queryForList("SELECT content FROM knowledge_chunks WHERE document_id=?::uuid ORDER BY chunk_index", String.class, id);
            jdbc.update("UPDATE knowledge_chunks SET embedding_identity=NULL WHERE document_id=?::uuid", id);
            var first = reindex.rebuild(1, id);
            assertThat(first.updated()).isEqualTo(1);
            assertThat(first.remaining()).isEqualTo(original.size() - 1);
            var next = reindex.rebuild(20, id);
            assertThat(next.updated()).isEqualTo(original.size() - 1);
            assertThat(next.remaining()).isZero();
            assertThat(reindex.rebuild(20, id).updated()).isZero();
            assertThat(jdbc.queryForList("SELECT content FROM knowledge_chunks WHERE document_id=?::uuid ORDER BY chunk_index", String.class, id)).containsExactlyElementsOf(original);
            assertThat(knowledge.search("重建测试学生优惠", 20)).anyMatch(result -> result.document().id().equals(id));
        } finally { jdbc.update("DELETE FROM knowledge_documents WHERE id=?::uuid", id); }
    }

    @Test void excludesVectorsFromOtherModelsAndSplitsLongDocuments() {
        var id = UUID.randomUUID().toString();
        try {
            knowledge.save(new KnowledgeDocument(id, "模型隔离测试", "模型隔离测试学生优惠".repeat(150), "ticket-price", null));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM knowledge_chunks WHERE document_id=?::uuid", Integer.class, id)).isGreaterThan(1);
            assertThat(jdbc.queryForObject("SELECT max(length(content)) FROM knowledge_chunks WHERE document_id=?::uuid", Integer.class, id)).isLessThanOrEqualTo(800);
            assertThat(knowledge.search("模型隔离测试学生优惠", 20)).anyMatch(result -> result.document().id().equals(id));
            jdbc.update("UPDATE knowledge_chunks SET embedding_identity='other-model' WHERE document_id=?::uuid", id);
            assertThat(knowledge.search("模型隔离测试学生优惠", 20)).noneMatch(result -> result.document().id().equals(id));
        } finally {
            jdbc.update("DELETE FROM knowledge_documents WHERE id=?::uuid", id);
        }
    }

    @Test void savesRealPgvectorAndRetrievesChinesePolicy() {
        var id = UUID.randomUUID().toString();
        try {
            knowledge.save(new KnowledgeDocument(id, "测试海洋馆", "测试海洋馆潜水票提前两日退订", "refund-policy", "2026-10-13"));
            assertThat(knowledge.search("测试海洋馆潜水票", 10)).anyMatch(result -> result.document().id().equals(id));
            assertThat(knowledge.searchByCategory("测试海洋馆潜水票", 10, "refund-policy")).anyMatch(result -> result.document().id().equals(id));
            assertThat(knowledge.searchByCategory("测试海洋馆潜水票", 10, "itinerary-attraction")).noneMatch(result -> result.document().id().equals(id));
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
