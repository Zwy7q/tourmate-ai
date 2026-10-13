package com.tourmate.knowledge;

import java.sql.Date;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("postgres")
public class JdbcKnowledgeRepository implements KnowledgeRepository {
    private final JdbcTemplate jdbc;
    private final EmbeddingClient embeddings;

    public JdbcKnowledgeRepository(JdbcTemplate jdbc, EmbeddingClient embeddings) {
        this.jdbc = jdbc;
        this.embeddings = embeddings;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public KnowledgeDocument save(KnowledgeDocument document) {
        var id = document.id() == null || document.id().isBlank() ? UUID.randomUUID().toString() : document.id();
        UUID.fromString(id);
        var chunks = new TextChunker().split(document.content());
        if (chunks.isEmpty()) throw new IllegalArgumentException("文档内容不能为空");
        var vectors = chunks.stream().map(content -> EmbeddingClient.literal(embeddings.embed(content))).toList();
        jdbc.update("""
                INSERT INTO knowledge_documents(id, title, category, source_name, effective_date)
                VALUES (?::uuid, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, category = EXCLUDED.category,
                    source_name = EXCLUDED.source_name, effective_date = EXCLUDED.effective_date
                """, id, document.title(), document.category(), document.title(),
                document.effectiveDate() == null ? null : Date.valueOf(document.effectiveDate()));
        jdbc.update("DELETE FROM knowledge_chunks WHERE document_id = ?::uuid", id);
        for (int index = 0; index < chunks.size(); index++) {
            jdbc.update("INSERT INTO knowledge_chunks(id, document_id, chunk_index, content, " + embeddings.vectorColumn()
                            + ", embedding_identity) VALUES (?::uuid, ?::uuid, ?, ?, ?::vector, ?)",
                    UUID.randomUUID(), id, index, chunks.get(index), vectors.get(index), embeddings.identity());
        }
        return new KnowledgeDocument(id, document.title(), document.content(), document.category(), document.effectiveDate());
    }

    @Override
    public List<KnowledgeSearchResult> search(String query, int limit) {
        return searchByCategory(query, limit, null);
    }

    @Override
    public List<KnowledgeSearchResult> searchByCategory(String query, int limit, String category) {
        if (query == null || query.isBlank()) return List.of();
        var vector = EmbeddingClient.literal(embeddings.embed(query));
        var column = embeddings.vectorColumn();
        return jdbc.query("""
                SELECT d.id, d.title, c.content, d.category, d.effective_date,
                       (1 - (c.%s <=> ?::vector)) +
                       ts_rank(c.search_text, plainto_tsquery('simple', ?)) AS score
                FROM knowledge_chunks c JOIN knowledge_documents d ON d.id = c.document_id
                WHERE c.embedding_identity = ? AND c.%s IS NOT NULL AND
                    (?::text IS NULL OR d.category = ?) AND
                    (1 - (c.%s <=> ?::vector) > 0.1 OR c.search_text @@ plainto_tsquery('simple', ?))
                ORDER BY score DESC LIMIT ?
                """.formatted(column, column, column), (rs, row) -> new KnowledgeSearchResult(
                        new KnowledgeDocument(rs.getString("id"), rs.getString("title"), rs.getString("content"),
                                rs.getString("category"), rs.getDate("effective_date") == null ? null : rs.getDate("effective_date").toString()),
                        rs.getDouble("score")), vector, query, embeddings.identity(), category, category, vector, query, Math.min(Math.max(limit, 1), 20));
    }
}
