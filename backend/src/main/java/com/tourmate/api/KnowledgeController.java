package com.tourmate.api;

import com.tourmate.knowledge.KnowledgeDocument;
import com.tourmate.knowledge.KnowledgeRepository;
import com.tourmate.knowledge.KnowledgeSearchResult;
import com.tourmate.knowledge.KnowledgeImportService;
import java.io.IOException;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {
    private final KnowledgeRepository repository;
    private final KnowledgeImportService importService;

    public KnowledgeController(KnowledgeRepository repository, KnowledgeImportService importService) {
        this.repository = repository;
        this.importService = importService;
    }

    @PostMapping("/documents")
    public KnowledgeDocument save(@Valid @RequestBody KnowledgeDocument document) {
        return repository.save(document);
    }

    @GetMapping("/search")
    public List<KnowledgeSearchResult> search(@RequestParam String query,
                                               @RequestParam(defaultValue = "3") int limit) {
        return repository.search(query, Math.min(Math.max(limit, 1), 10));
    }

    @PostMapping("/upload")
    public KnowledgeImportService.ImportResult upload(@RequestPart("file") MultipartFile file,
                                                       @RequestParam(required = false) String category) throws IOException {
        return importService.importFile(file, category);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> badRequest(IllegalArgumentException exception) {
        return Map.of("error", exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Map<String, String> unavailable(IllegalStateException exception) {
        return Map.of("error", "知识处理服务暂时不可用，请检查向量模型服务后重试");
    }
}
