package com.tourmate.knowledge;

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@Profile("postgres")
public class KnowledgeReindexService {
    private final JdbcTemplate jdbc;
    private final EmbeddingClient embeddings;

    public KnowledgeReindexService(JdbcTemplate jdbc, EmbeddingClient embeddings) {
        this.jdbc = jdbc;
        this.embeddings = embeddings;
    }

    public ReindexResult rebuild(int limit, String documentId) {
        if (documentId != null) UUID.fromString(documentId);
        if (limit < 1 || limit > 20) throw new IllegalArgumentException("重建数量必须在 1 到 20 之间");
        var column = embeddings.vectorColumn();
        var pending = "(embedding_identity IS DISTINCT FROM ? OR " + column + " IS NULL)"
                + " AND (?::uuid IS NULL OR document_id=?::uuid)";
        var chunks = jdbc.query("SELECT id, content, embedding_identity FROM knowledge_chunks WHERE "
                        + pending + " ORDER BY id LIMIT ?",
                (rs, row) -> new PendingChunk(rs.getString("id"), rs.getString("content"), rs.getString("embedding_identity")),
                embeddings.identity(), documentId, documentId, limit);
        int updated = 0;
        int skipped = 0;
        var failed = new ArrayList<String>();
        String message = "";
        for (var chunk : chunks) {
            if (chunk.content().length() > 800) {
                failed.add(chunk.id());
                message = "存在超过 800 字符的旧块，请重新导入对应文档以应用分块策略";
                continue;
            }
            String vector;
            try {
                vector = EmbeddingClient.literal(embeddings.embed(chunk.content()));
            } catch (IllegalStateException exception) {
                failed.add(chunk.id());
                message = "向量服务不可用或响应无效，已保留旧向量，可在服务恢复后重试";
                break;
            }
            // 单条 UPDATE 原子提交；不在模型调用期间持有行锁。
            int count = jdbc.update("UPDATE knowledge_chunks SET " + column + "=?::vector, embedding_identity=?"
                            + " WHERE id=?::uuid AND content=? AND embedding_identity IS NOT DISTINCT FROM ?",
                    vector, embeddings.identity(), chunk.id(), chunk.content(), chunk.identity());
            updated += count;
            if (count == 0) skipped++;
        }
        var remaining = jdbc.queryForObject("SELECT count(*) FROM knowledge_chunks WHERE " + pending,
                Long.class, embeddings.identity(), documentId, documentId);
        return new ReindexResult(updated, skipped, List.copyOf(failed), remaining, message);
    }

    private record PendingChunk(String id, String content, String identity) {}

    public record ReindexResult(int updated, int skipped, List<String> failedChunkIds, long remaining, String message) {}
}
