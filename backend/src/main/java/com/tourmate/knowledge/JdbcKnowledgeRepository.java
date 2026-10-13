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

    public JdbcKnowledgeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public KnowledgeDocument save(KnowledgeDocument document) {
        var id = document.id() == null || document.id().isBlank() ? UUID.randomUUID().toString() : document.id();
        jdbc.update("""
                INSERT INTO knowledge_documents(id, title, category, source_name, effective_date)
                VALUES (?::uuid, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, category = EXCLUDED.category,
                    source_name = EXCLUDED.source_name, effective_date = EXCLUDED.effective_date
                """, id, document.title(), document.category(), document.title(),
                document.effectiveDate() == null ? null : Date.valueOf(document.effectiveDate()));
        jdbc.update("DELETE FROM knowledge_chunks WHERE document_id = ?::uuid", id);
        jdbc.update("INSERT INTO knowledge_chunks(id, document_id, chunk_index, content) VALUES (?::uuid, ?::uuid, 0, ?)",
                UUID.randomUUID(), id, document.content());
        return new KnowledgeDocument(id, document.title(), document.content(), document.category(), document.effectiveDate());
    }

    @Override
    public List<KnowledgeSearchResult> search(String query, int limit) {
        return jdbc.query("""
                SELECT d.id, d.title, c.content, d.category, d.effective_date,
                       ts_rank(c.search_text, plainto_tsquery('simple', ?)) AS score
                FROM knowledge_chunks c JOIN knowledge_documents d ON d.id = c.document_id
                WHERE c.search_text @@ plainto_tsquery('simple', ?)
                ORDER BY score DESC LIMIT ?
                """, (rs, row) -> new KnowledgeSearchResult(
                        new KnowledgeDocument(rs.getString("id"), rs.getString("title"), rs.getString("content"),
                                rs.getString("category"), rs.getDate("effective_date") == null ? null : rs.getDate("effective_date").toString()),
                        rs.getDouble("score")), query, query, Math.min(Math.max(limit, 1), 20));
    }
}
