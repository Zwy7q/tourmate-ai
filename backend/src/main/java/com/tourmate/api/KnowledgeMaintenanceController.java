package com.tourmate.api;

import com.tourmate.knowledge.KnowledgeReindexService;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** 本地开发维护接口，公开部署前需要增加管理员鉴权。 */
@RestController
@Profile("postgres")
@RequestMapping("/api/knowledge")
public class KnowledgeMaintenanceController {
    private final KnowledgeReindexService service;

    public KnowledgeMaintenanceController(KnowledgeReindexService service) { this.service = service; }

    @PostMapping("/reindex")
    public KnowledgeReindexService.ReindexResult rebuild(@RequestParam(defaultValue = "20") int limit,
                                                       @RequestParam(required = false) String documentId) {
        return service.rebuild(limit, documentId);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> invalid(IllegalArgumentException exception) {
        return Map.of("message", "参数无效：limit 必须在 1 到 20 之间，documentId 必须为 UUID");
    }
}
