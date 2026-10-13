package com.tourmate.knowledge;

import java.util.List;

public interface KnowledgeRepository {
    KnowledgeDocument save(KnowledgeDocument document);
    List<KnowledgeSearchResult> search(String query, int limit);
    default List<KnowledgeSearchResult> searchByCategory(String query, int limit, String category) {
        return search(query, 20).stream().filter(result -> category.equals(result.document().category())).limit(limit).toList();
    }
}
