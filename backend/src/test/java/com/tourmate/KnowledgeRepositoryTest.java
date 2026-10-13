package com.tourmate;

import static org.assertj.core.api.Assertions.assertThat;

import com.tourmate.knowledge.InMemoryKnowledgeRepository;
import com.tourmate.knowledge.KnowledgeDocument;
import org.junit.jupiter.api.Test;

class KnowledgeRepositoryTest {
    private final InMemoryKnowledgeRepository repository = new InMemoryKnowledgeRepository();

    @Test
    void prioritizesRefundPolicyForRefundQuery() {
        var results = repository.search("这张票可以退票吗", 3);
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).document().id()).isEqualTo("refund");
    }

    @Test
    void newlySavedDocumentIsSearchable() {
        repository.save(new KnowledgeDocument("weather", "天气须知", "雨天请携带雨具。", "notice", "2026-01-01"));
        assertThat(repository.search("雨天", 3)).anyMatch(result -> result.document().id().equals("weather"));
    }
}
