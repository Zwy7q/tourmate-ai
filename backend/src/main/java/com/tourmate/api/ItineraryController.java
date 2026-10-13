package com.tourmate.api;

import com.tourmate.itinerary.ItineraryService;
import com.tourmate.itinerary.TripState;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.ConcurrentModificationException;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/itineraries")
public class ItineraryController {
    private final ItineraryService service;
    public ItineraryController(ItineraryService service) { this.service = service; }

    @PostMapping("/plan")
    public TripState plan(@Valid @RequestBody PlanRequest request) {
        return service.plan(request.conversationId(), request.message());
    }
    @GetMapping("/{conversationId}")
    public ResponseEntity<TripState> find(@PathVariable String conversationId) {
        return service.find(conversationId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalid(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }
    @ExceptionHandler(ConcurrentModificationException.class)
    public ResponseEntity<Map<String, String>> conflict() {
        return ResponseEntity.status(409).body(Map.of("error", "同一会话的行程已更新，请读取最新行程后重试"));
    }
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> unavailable() {
        return ResponseEntity.status(502).body(Map.of("error", "行程生成暂时不可用，请检查模型配置或调整要求后重试"));
    }
    @ExceptionHandler(com.tourmate.itinerary.ItineraryDisabledException.class)
    public ResponseEntity<Map<String, String>> disabled() {
        return ResponseEntity.status(503).body(Map.of("error", "行程规划未启用，请配置DeepSeek并启用deepseek profile"));
    }
    public record PlanRequest(@NotBlank @Size(max = 100) String conversationId,
                              @NotBlank @Size(max = 2000) String message) {}
}
