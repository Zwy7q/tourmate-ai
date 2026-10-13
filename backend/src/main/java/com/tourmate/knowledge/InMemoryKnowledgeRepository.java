package com.tourmate.knowledge;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Repository;

@Repository
@org.springframework.context.annotation.Profile("!postgres")
public class InMemoryKnowledgeRepository implements KnowledgeRepository {
    private final List<KnowledgeDocument> documents = new CopyOnWriteArrayList<>();
    private final AtomicInteger sequence = new AtomicInteger(3);

    public InMemoryKnowledgeRepository() {
        save(new KnowledgeDocument("hours", "故宫开放时间", "故宫博物院旺季开放时间为 8:30-16:10，周一闭馆（法定节假日除外）。", "opening-hours", "2026-01-01"));
        save(new KnowledgeDocument("ticket", "故宫票价说明", "故宫成人票旺季 60 元，淡季 40 元；未成年人和学生票按照官方公告执行。", "ticket-price", "2026-01-01"));
        save(new KnowledgeDocument("refund", "故宫退改规则", "未使用的门票可在参观日前通过原购票渠道申请退票，已检票或超过参观日不可退。", "refund-policy", "2026-01-01"));
    }

    @Override
    public KnowledgeDocument save(KnowledgeDocument document) {
        var normalized = document.id() == null || document.id().isBlank()
                ? new KnowledgeDocument("doc-" + sequence.incrementAndGet(), document.title(), document.content(), document.category(), document.effectiveDate())
                : document;
        documents.removeIf(existing -> existing.id().equals(normalized.id()));
        documents.add(normalized);
        return normalized;
    }

    @Override
    public List<KnowledgeSearchResult> search(String query, int limit) {
        var terms = tokens(query);
        return documents.stream()
                .map(document -> new KnowledgeSearchResult(document, score(document, terms)))
                .filter(result -> result.score() > 0)
                .sorted(Comparator.comparingDouble(KnowledgeSearchResult::score).reversed())
                .limit(limit)
                .toList();
    }

    private double score(KnowledgeDocument document, List<String> terms) {
        var haystack = normalize(document.title() + " " + document.content() + " " + document.category());
        return terms.stream().filter(haystack::contains).distinct().count();
    }

    private List<String> tokens(String query) {
        var normalized = normalize(query);
        var terms = new ArrayList<String>();
        for (int i = 0; i < normalized.length(); i++) {
            terms.add(normalized.substring(i, i + 1));
        }
        return terms;
    }

    private String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}
