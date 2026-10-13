package com.tourmate.itinerary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourmate.knowledge.KnowledgeRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import org.springframework.stereotype.Component;

@Component
public class AttractionCatalog {
    private final KnowledgeRepository knowledge;
    private final ObjectMapper json = new ObjectMapper();
    public AttractionCatalog(KnowledgeRepository knowledge) { this.knowledge = knowledge; }

    public List<Attraction> search(TripRequirements requirements) {
        var candidates = new LinkedHashMap<String, Attraction>();
        for (var result : knowledge.searchByCategory(requirements.city() + " 景点 行程 " +
                (requirements.preferences() == null ? "" : requirements.preferences()), 20, "itinerary-attraction")) {
            var doc = result.document();
            if (!"itinerary-attraction".equals(doc.category())) continue;
            try {
                if (doc.effectiveDate() != null && LocalDate.parse(doc.effectiveDate()).isAfter(requirements.startDate())) continue;
                var node = json.readTree(doc.content());
                if (!node.path("city").asText().equals(requirements.city()) || !node.path("indoor").isBoolean()
                        || !node.path("demo").isBoolean() || !node.path("ticketPrice").isNumber()
                        || !node.path("visitHours").isNumber() || node.path("name").asText().isBlank()
                        || node.path("source").asText().isBlank() || !node.path("closedWeekdays").isArray()) continue;
                var price = node.path("ticketPrice").decimalValue();
                var hours = node.path("visitHours").decimalValue();
                if (price.signum() < 0 || price.compareTo(new BigDecimal("100000")) > 0 || hours.signum() <= 0
                        || hours.compareTo(new BigDecimal("6")) > 0) continue;
                var closed = new ArrayList<Integer>();
                for (var day : node.path("closedWeekdays")) {
                    if (!day.isInt() || day.asInt() < 1 || day.asInt() > 7) throw new IllegalArgumentException("闭馆日无效");
                    closed.add(day.asInt());
                }
                candidates.putIfAbsent(doc.id(), new Attraction(doc.id(), node.path("name").asText(), requirements.city(),
                        node.path("indoor").asBoolean(), price, hours, node.path("source").asText(), node.path("demo").asBoolean(), closed));
            } catch (Exception ignored) {
                // 普通文本、被切块的 JSON 或不完整元数据不能成为可校验景点。
            }
        }
        return List.copyOf(candidates.values());
    }

    public record Attraction(String documentId, String name, String city, boolean indoor, BigDecimal ticketPrice,
                             BigDecimal visitHours, String source, boolean demo, List<Integer> closedWeekdays) {
        public Attraction { closedWeekdays = List.copyOf(closedWeekdays); }
        public boolean open(LocalDate date) { return !closedWeekdays.contains(date.getDayOfWeek().getValue()); }
    }
}
