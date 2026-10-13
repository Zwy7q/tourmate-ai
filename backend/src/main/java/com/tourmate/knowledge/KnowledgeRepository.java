package com.tourmate.knowledge;

import java.util.List;

public interface KnowledgeRepository {
    KnowledgeDocument save(KnowledgeDocument document);
    List<KnowledgeSearchResult> search(String query, int limit);
}
