package com.tourmate;

import com.tourmate.itinerary.*;
import com.tourmate.knowledge.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ItineraryServiceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-13T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private final InMemoryItineraryStore store = new InMemoryItineraryStore();
    private final InMemoryKnowledgeRepository knowledge = new InMemoryKnowledgeRepository();
    private final TestModel model = new TestModel();
    private final ItineraryService service = new ItineraryService(model, new AttractionCatalog(knowledge),
            requirements -> List.of(), store, clock);

    @Test void asksForMissingFieldsWithoutInventingAPlan() {
        model.patch = new TripRequirements("杭州", null, null, null, null, null);
        var state = service.plan("missing", "去杭州玩");
        assertThat(state.status()).isEqualTo("NEEDS_INFORMATION");
        assertThat(state.clarification()).contains("出发日期", "天数", "预算");
        assertThat(state.plan()).isNull();
        assertThat(model.scheduleCalls).isZero();
    }

    @Test void savesAndRevisesIndoorDayWithoutLosingRequirements() {
        seed(); model.patch = complete("100");
        var first = service.plan("trip", "杭州两日游");
        assertThat(first.status()).isEqualTo("READY");
        assertThat(first.plan().ticketSubtotal()).isEqualByComparingTo("50");
        assertThat(first.plan().days()).hasSize(2);
        assertThat(first.plan().days().getFirst().stops().getFirst().documentId()).isEqualTo("outdoor");
        model.patch = new TripRequirements(null, null, null, null, null, List.of(2));
        model.selection = List.of(List.of("outdoor"), List.of("indoor"));
        var revised = service.plan("trip", "第二天全换室内");
        assertThat(revised.version()).isEqualTo(2);
        assertThat(revised.requirements().city()).isEqualTo("杭州");
        assertThat(revised.requirements().budget()).isEqualByComparingTo("100");
        assertThat(revised.plan().days().get(1).stops().getFirst().name()).isEqualTo("演示展馆");
        assertThat(store.find("other")).isEmpty();
    }

    @Test void failedRevisionDoesNotOverwriteSavedPlan() {
        seed(); model.patch = complete("100"); service.plan("trip", "两日游");
        model.patch = new TripRequirements(null, null, null, null, null, List.of(2));
        model.selection = List.of(List.of("outdoor"), List.of("unknown"));
        assertThatThrownBy(() -> service.plan("trip", "换行程")).isInstanceOf(IllegalStateException.class);
        assertThat(store.find("trip").orElseThrow().version()).isEqualTo(1);
    }

    @Test void changingOneIndoorDayPreservesTheOtherDay() {
        seed(); model.patch = complete("100"); service.plan("partial", "两日游");
        knowledge.save(new KnowledgeDocument("alternative", "杭州演示替代公园", "{\"name\":\"演示替代公园\",\"city\":\"杭州\",\"indoor\":false,\"ticketPrice\":5,\"visitHours\":2,\"source\":\"演示资料\",\"demo\":true,\"closedWeekdays\":[]}", "itinerary-attraction", null));
        model.patch = new TripRequirements(null, null, null, null, null, List.of(2));
        model.selection = List.of(List.of("alternative"), List.of("indoor"));
        var revised = service.plan("partial", "第二天改室内");
        assertThat(revised.plan().days().getFirst().stops().getFirst().documentId()).isEqualTo("outdoor");
    }

    @Test void suppliesFixedDaysToTheModelBeforeItSelectsReplacementAttractions() {
        seed(); model.patch = complete("100"); service.plan("fixed-model", "两日游");
        model.patch = new TripRequirements(null, null, null, null, null, List.of(2));
        service.plan("fixed-model", "第二天室内");
        assertThat(model.fixedDays).isEqualTo(List.of(List.of("outdoor"), List.of()));
    }

    @Test void addingAnIndoorDayDoesNotReplanAnAlreadyIndoorDay() {
        seed();
        knowledge.save(new KnowledgeDocument("indoor2", "杭州演示展馆二", "{\"name\":\"演示展馆二\",\"city\":\"杭州\",\"indoor\":true,\"ticketPrice\":5,\"visitHours\":2,\"source\":\"演示资料\",\"demo\":true,\"closedWeekdays\":[]}", "itinerary-attraction", null));
        model.patch = new TripRequirements("杭州", LocalDate.parse("2026-10-14"), 2, new BigDecimal("100"), null, List.of(1));
        model.selection = List.of(List.of("indoor"), List.of("outdoor")); service.plan("indoor-add", "第一天室内");
        model.patch = new TripRequirements(null, null, null, null, null, List.of(1, 2));
        model.selection = List.of(List.of("indoor2"), List.of("indoor"));
        // Preserve day one's indoor. The model must use indoor2 on day two to avoid duplication.
        assertThatThrownBy(() -> service.plan("indoor-add", "第二天也室内")).isInstanceOf(IllegalStateException.class);
        assertThat(store.find("indoor-add").orElseThrow().version()).isEqualTo(1);
        model.selection = List.of(List.of("indoor2"), List.of("indoor2"));
        var revised = service.plan("indoor-add", "第二天也室内");
        assertThat(revised.plan().days().getFirst().stops().getFirst().documentId()).isEqualTo("indoor");
    }

    @Test void ordinaryChatDocumentsDoNotCrowdOutAttractionCandidates() {
        seed(); model.patch = complete("100");
        for (int i = 0; i < 25; i++) knowledge.save(new KnowledgeDocument("chat-" + i, "杭州景点行程慢节奏资料", "杭州景点行程慢节奏，普通客服文档", "general", null));
        assertThat(service.plan("crowded", "杭州两日游").status()).isEqualTo("READY");
    }

    @Test void flagsTicketBudgetAndDoesNotClaimTotalTravelBudgetIsMet() {
        seed(); model.patch = complete("10");
        var state = service.plan("budget", "预算10元");
        assertThat(state.status()).isEqualTo("TICKET_BUDGET_EXCEEDED");
        assertThat(state.plan().budgetNote()).contains("交通", "住宿", "餐饮", "未计");
    }

    @Test void insufficientKnowledgeReturnsAnActionableQuestion() {
        model.patch = complete("100");
        var state = service.plan("empty", "两日游");
        assertThat(state.status()).isEqualTo("NEEDS_KNOWLEDGE");
        assertThat(state.plan()).isNull();
        assertThat(model.scheduleCalls).isZero();
    }

    @Test void rejectsOutdoorSelectionsOnAnIndoorDay() {
        seed(); model.patch = new TripRequirements("杭州", LocalDate.parse("2026-10-14"), 2, new BigDecimal("100"), null, List.of(1));
        assertThatThrownBy(() -> service.plan("indoor", "第一天室内")).isInstanceOf(IllegalStateException.class);
        assertThat(store.find("indoor")).isEmpty();
    }

    @Test void rejectsRepeatedAttractionsAndClosedDays() {
        seed(); model.patch = complete("100");
        model.selection = List.of(List.of("outdoor"), List.of("outdoor"));
        assertThatThrownBy(() -> service.plan("repeat", "两日游")).isInstanceOf(IllegalStateException.class);
        knowledge.save(new KnowledgeDocument("outdoor", "杭州演示公园", "{\"name\":\"演示公园\",\"city\":\"杭州\",\"indoor\":false,\"ticketPrice\":20,\"visitHours\":2,\"source\":\"演示资料\",\"demo\":true,\"closedWeekdays\":[3]}", "itinerary-attraction", null));
        model.selection = List.of(List.of("outdoor"), List.of("indoor"));
        assertThatThrownBy(() -> service.plan("closed", "两日游")).isInstanceOf(IllegalStateException.class);
    }

    @Test void validatesDatesBeforeCallingSchedule() {
        model.patch = new TripRequirements("杭州", LocalDate.parse("2026-10-12"), 2, new BigDecimal("100"), null, null);
        assertThatThrownBy(() -> service.plan("past", "昨天出发")).isInstanceOf(IllegalArgumentException.class);
        assertThat(model.scheduleCalls).isZero();
    }

    @Test void detectsNotEnoughDistinctIndoorAttractionsBeforeModelPlanning() {
        seed(); model.patch = new TripRequirements("杭州", LocalDate.parse("2026-10-14"), 2, new BigDecimal("100"), null, List.of(1, 2));
        var state = service.plan("insufficient-indoor", "两天都室内");
        assertThat(state.status()).isEqualTo("NEEDS_KNOWLEDGE");
        assertThat(model.scheduleCalls).isZero();
    }

    @Test void refusesTooManyVisitHoursEvenWhenEveryAttractionHasEvidence() {
        seed(); model.patch = complete("100");
        knowledge.save(new KnowledgeDocument("long", "杭州演示长展", "{\"name\":\"演示长展\",\"city\":\"杭州\",\"indoor\":true,\"ticketPrice\":5,\"visitHours\":5,\"source\":\"演示资料\",\"demo\":true,\"closedWeekdays\":[]}", "itinerary-attraction", null));
        model.selection = List.of(List.of("outdoor", "long"), List.of("indoor"));
        assertThatThrownBy(() -> service.plan("long", "两日游")).isInstanceOf(IllegalStateException.class);
        assertThat(store.find("long")).isEmpty();
    }

    @Test void concurrentPlanningRequestsCannotOverwriteEachOther() throws Exception {
        seed();
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        var concurrentModel = new ItineraryModel() {
            public TripRequirements extract(String message, TripRequirements current) { return complete("100"); }
            public List<List<String>> schedule(TripRequirements r, List<AttractionCatalog.Attraction> a, List<ItineraryWeather.Forecast> w) {
                try { barrier.await(5, java.util.concurrent.TimeUnit.SECONDS); } catch (Exception e) { throw new IllegalStateException(e); }
                return List.of(List.of("outdoor"), List.of("indoor"));
            }
        };
        var concurrentService = new ItineraryService(concurrentModel, new AttractionCatalog(knowledge), r -> List.of(), store, clock);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Boolean> task = () -> {
                try { concurrentService.plan("race", "两日游"); return true; }
                catch (java.util.ConcurrentModificationException expected) { return false; }
            };
            var first = executor.submit(task); var second = executor.submit(task);
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
            assertThat(store.find("race").orElseThrow().version()).isEqualTo(1);
        }
    }

    @Test void versionCheckPreventsLostUpdates() {
        var state = new TripState(1, complete("100"), null, "NEEDS_KNOWLEDGE", "导入资料", List.of());
        assertThat(store.save("version", 0, state)).isTrue();
        assertThat(store.save("version", 0, state)).isFalse();
        assertThat(store.find("version").orElseThrow().version()).isEqualTo(1);
    }

    private TripRequirements complete(String budget) {
        return new TripRequirements("杭州", LocalDate.parse("2026-10-14"), 2, new BigDecimal(budget), "慢节奏", null);
    }
    private void seed() {
        for (var entry : List.of(new Object[]{"outdoor", "演示公园", false, 20}, new Object[]{"indoor", "演示展馆", true, 30})) {
            knowledge.save(new KnowledgeDocument((String)entry[0], "杭州" + entry[1],
                    "{\"name\":\"" + entry[1] + "\",\"city\":\"杭州\",\"indoor\":" + entry[2] + ",\"ticketPrice\":" + entry[3]
                            + ",\"visitHours\":2,\"source\":\"演示资料\",\"demo\":true,\"closedWeekdays\":[]}", "itinerary-attraction", null));
        }
    }
    private static class TestModel implements ItineraryModel {
        TripRequirements patch; int scheduleCalls;
        List<List<String>> fixedDays;
        List<List<String>> selection = List.of(List.of("outdoor"), List.of("indoor"));
        public TripRequirements extract(String message, TripRequirements current) { return patch; }
        public List<List<String>> schedule(TripRequirements requirements, List<AttractionCatalog.Attraction> attractions,
                                           List<ItineraryWeather.Forecast> forecasts) { scheduleCalls++; return selection; }
        public List<List<String>> schedule(TripRequirements requirements, List<AttractionCatalog.Attraction> attractions,
                                           List<ItineraryWeather.Forecast> forecasts, ItineraryPlan previous, List<List<String>> fixed) {
            fixedDays = fixed; return schedule(requirements, attractions, forecasts);
        }
    }
}
