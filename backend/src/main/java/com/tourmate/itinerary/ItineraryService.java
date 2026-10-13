package com.tourmate.itinerary;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ItineraryService {
    private static final String BUDGET_NOTE = "仅核验单人门票费用；交通、住宿、餐饮未计，不能据此确认总出游预算。雨天备选未计入费用，交通耗时及预约须另行核实。";
    private final ItineraryModel model;
    private final AttractionCatalog catalog;
    private final ItineraryWeather weather;
    private final ItineraryStore store;
    private final Clock clock;

    @Autowired
    public ItineraryService(ItineraryModel model, AttractionCatalog catalog, ItineraryWeather weather, ItineraryStore store) {
        this(model, catalog, weather, store, Clock.system(java.time.ZoneId.of("Asia/Shanghai")));
    }
    public ItineraryService(ItineraryModel model, AttractionCatalog catalog, ItineraryWeather weather, ItineraryStore store, Clock clock) {
        this.model = model; this.catalog = catalog; this.weather = weather; this.store = store; this.clock = clock;
    }
    public Optional<TripState> find(String id) { return store.find(id); }

    public TripState plan(String id, String message) {
        var previous = store.find(id).orElse(null);
        long version = previous == null ? 0 : previous.version();
        var current = previous == null ? TripRequirements.empty() : previous.requirements();
        var patch = model.extract(message, current);
        if (patch == null) throw new IllegalStateException("需求提取失败");
        var requirements = current.merge(patch);
        validateRequirements(requirements);
        var missing = new ArrayList<String>();
        if (requirements.city() == null || requirements.city().isBlank()) missing.add("目的地城市");
        if (requirements.startDate() == null) missing.add("出发日期");
        if (requirements.days() == null) missing.add("天数");
        if (requirements.budget() == null) missing.add("单人预算（元）");
        if (!missing.isEmpty()) return save(id, version, requirements, null, "NEEDS_INFORMATION", "请补充：" + String.join("、", missing), List.of(BUDGET_NOTE));
        var attractions = catalog.search(requirements);
        if (!hasEnoughKnowledge(requirements, attractions)) return save(id, version, requirements, null, "NEEDS_KNOWLEDGE",
                "候选景点资料不足或不满足日期／室内要求，请导入该城市的行程景点资料，或调整日期和天数。", List.of(BUDGET_NOTE));
        var forecasts = weather.fetch(requirements);
        var fixedDays = preserveUnaffectedDays(previous, patch, Collections.<List<String>>nCopies(requirements.days(), List.of()));
        var selection = model.schedule(requirements, attractions, forecasts, previous == null ? null : previous.plan(), fixedDays);
        selection = preserveUnaffectedDays(previous, patch, selection);
        var itinerary = validateAndBuild(requirements, attractions, forecasts, selection);
        var warnings = new ArrayList<String>();
        warnings.add(BUDGET_NOTE);
        if (itinerary.days().stream().anyMatch(day -> !day.weather().available())) warnings.add("部分日期未获取天气，不代表晴天；出发前需再次查询。");
        if (itinerary.days().stream().flatMap(day -> day.stops().stream()).anyMatch(ItineraryPlan.Stop::demo)) warnings.add("包含演示景点资料或演示票价，不作为实际购票依据。");
        boolean exceeds = itinerary.ticketSubtotal().compareTo(requirements.budget()) > 0;
        if (exceeds) warnings.add("已知门票费用超过预算，请提高预算或调整行程。");
        return save(id, version, requirements, itinerary, exceeds ? "TICKET_BUDGET_EXCEEDED" : "READY", null, warnings);
    }

    private void validateRequirements(TripRequirements r) {
        if (r.city() != null && r.city().length() > 50) throw new IllegalArgumentException("城市名过长");
        if (r.startDate() != null && (r.startDate().isBefore(LocalDate.now(clock)) || r.startDate().isAfter(LocalDate.now(clock).plusDays(365)))) throw new IllegalArgumentException("出发日期应为今天至未来365天");
        if (r.days() != null && (r.days() < 1 || r.days() > 7)) throw new IllegalArgumentException("行程天数应为1至7天");
        if (r.budget() != null && (r.budget().signum() <= 0 || r.budget().compareTo(new BigDecimal("1000000")) > 0)) throw new IllegalArgumentException("预算应大于0且不超过100万元");
        if (r.preferences() != null && r.preferences().length() > 500) throw new IllegalArgumentException("偏好描述过长");
        if (r.indoorDays() != null && r.indoorDays().stream().anyMatch(day -> day < 1 || day > (r.days() == null ? 7 : r.days()))) throw new IllegalArgumentException("室内活动天序号超出行程范围");
    }
    private boolean hasEnoughKnowledge(TripRequirements r, List<AttractionCatalog.Attraction> attractions) {
        if (attractions.size() < r.days()) return false;
        // Bipartite matching proves every day can get a distinct eligible attraction.
        var assigned = new int[attractions.size()]; Arrays.fill(assigned, -1);
        for (int i = 0; i < r.days(); i++) {
            if (!assignDay(i, r, attractions, assigned, new boolean[attractions.size()])) return false;
        }
        return true;
    }
    private boolean assignDay(int day, TripRequirements r, List<AttractionCatalog.Attraction> attractions, int[] assigned, boolean[] visited) {
        for (int index = 0; index < attractions.size(); index++) {
            var attraction = attractions.get(index);
            if (visited[index] || !attraction.open(r.startDate().plusDays(day)) || (indoor(r, day + 1) && !attraction.indoor())) continue;
            visited[index] = true;
            if (assigned[index] == -1 || assignDay(assigned[index], r, attractions, assigned, visited)) { assigned[index] = day; return true; }
        }
        return false;
    }
    private boolean indoor(TripRequirements r, int day) { return r.indoorDays() != null && r.indoorDays().contains(day); }

    private List<List<String>> preserveUnaffectedDays(TripState previous, TripRequirements patch, List<List<String>> selection) {
        if (previous == null || previous.plan() == null || patch.indoorDays() == null || patch.city() != null
                || patch.startDate() != null || patch.days() != null || patch.budget() != null || patch.preferences() != null
                || selection == null || selection.size() != previous.plan().days().size()) return selection;
        var changed = new HashSet<Integer>(patch.indoorDays());
        if (previous.requirements().indoorDays() != null) {
            for (var day : previous.requirements().indoorDays()) {
                if (!changed.add(day)) changed.remove(day);
            }
        }
        var preserved = new ArrayList<>(selection);
        for (int i = 0; i < preserved.size(); i++) {
            if (!changed.contains(i + 1)) preserved.set(i, previous.plan().days().get(i).stops().stream().map(ItineraryPlan.Stop::documentId).toList());
        }
        return preserved;
    }

    private ItineraryPlan validateAndBuild(TripRequirements r, List<AttractionCatalog.Attraction> attractions,
                                          List<ItineraryWeather.Forecast> forecasts, List<List<String>> selection) {
        if (selection == null || selection.size() != r.days()) throw new IllegalStateException("规划天数不匹配");
        var byId = new HashMap<String, AttractionCatalog.Attraction>();
        attractions.forEach(a -> byId.put(a.documentId(), a));
        var used = new HashSet<String>();
        var days = new ArrayList<ItineraryPlan.Day>();
        var subtotal = BigDecimal.ZERO;
        for (int i = 0; i < r.days(); i++) {
            var ids = selection.get(i); var date = r.startDate().plusDays(i);
            if (ids == null || ids.isEmpty() || ids.size() > 3) throw new IllegalStateException("每天需要1至3处景点");
            var stops = new ArrayList<ItineraryPlan.Stop>(); var hours = BigDecimal.ZERO;
            for (int j = 0; j < ids.size(); j++) {
                var a = byId.get(ids.get(j));
                if (a == null || !used.add(a.documentId()) || !a.open(date) || (indoor(r, i + 1) && !a.indoor())) throw new IllegalStateException("规划包含无依据、重复、闭馆或不符合室内要求的景点");
                hours = hours.add(a.visitHours()); subtotal = subtotal.add(a.ticketPrice());
                stops.add(stop(a, j == 0 ? "上午" : "下午（顺序建议）"));
            }
            if (hours.compareTo(new BigDecimal("6")) > 0) throw new IllegalStateException("单日游览时长超过6小时");
            var alternatives = attractions.stream().filter(a -> a.indoor() && a.open(date) && !ids.contains(a.documentId()))
                    .limit(2).map(a -> stop(a, "雨天候选，未计入费用")).toList();
            var forecast = forecasts.stream().filter(f -> date.equals(f.date())).findFirst()
                    .orElse(ItineraryWeather.Forecast.unavailable(date, "未获取该日天气"));
            days.add(new ItineraryPlan.Day(date, stops, forecast, alternatives));
        }
        return new ItineraryPlan(days, subtotal, BUDGET_NOTE);
    }
    private ItineraryPlan.Stop stop(AttractionCatalog.Attraction a, String period) {
        return new ItineraryPlan.Stop(a.documentId(), a.name(), period, a.visitHours(), a.ticketPrice(), a.source(), a.demo());
    }
    private TripState save(String id, long version, TripRequirements r, ItineraryPlan plan, String status, String clarification, List<String> warnings) {
        var state = new TripState(version + 1, r, plan, status, clarification, warnings);
        if (!store.save(id, version, state)) throw new ConcurrentModificationException("行程已被其他请求修改，请读取最新状态后重试");
        return state;
    }
}
