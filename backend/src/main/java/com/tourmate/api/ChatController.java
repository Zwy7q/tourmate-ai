package com.tourmate.api;

import com.tourmate.agent.AgentResponse;
import com.tourmate.chat.ChatService;
import com.tourmate.refund.RefundService;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
public class ChatController {
    private final ChatService chatService;
    private final RefundService refundService;

    public ChatController(ChatService chatService, RefundService refundService) {
        this.chatService = chatService;
        this.refundService = refundService;
    }

    @PostMapping("/chat")
    public AgentResponse chat(@Valid @RequestBody ChatRequest request) {
        return chatService.answer(request);
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatRequest request) {
        var emitter = new SseEmitter(120_000L);
        var subscription = new AtomicReference<Disposable>();
        var closed = new AtomicBoolean();
        Runnable cancel = () -> {
            closed.set(true);
            var current = subscription.get();
            if (current != null) current.dispose();
        };
        emitter.onTimeout(cancel);
        emitter.onCompletion(cancel);
        emitter.onError(error -> cancel.run());
        var current = chatService.stream(request).subscribeOn(Schedulers.boundedElastic()).subscribe(event -> {
            try {
                emitter.send(SseEmitter.event().name(event.type()).data(event.data()));
            } catch (java.io.IOException error) {
                throw new IllegalStateException("客户端已断开", error);
            }
        }, error -> {
            try {
                emitter.send(SseEmitter.event().name("error").data(Map.of("message", "生成服务暂时不可用，请稍后重试")));
            } catch (Exception ignored) {
                // 连接已经结束时无需再发送错误。
            }
            emitter.complete();
        }, emitter::complete);
        subscription.set(current);
        if (closed.get()) current.dispose();
        return emitter;
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> unavailable(IllegalStateException exception) {
        return ResponseEntity.status(502).body(Map.of("message", "生成服务暂时不可用，请稍后重试"));
    }

    @PostMapping("/refunds/confirm")
    public Map<String, Object> confirm(@RequestBody Map<String, String> request) {
        var success = refundService.confirm(request.get("token"));
        return Map.of("success", success, "status", success ? "REFUND_REQUESTED" : "INVALID_OR_EXPIRED_TOKEN");
    }
}
