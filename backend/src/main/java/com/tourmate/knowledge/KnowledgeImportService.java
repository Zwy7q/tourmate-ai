package com.tourmate.knowledge;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KnowledgeImportService {
    private final KnowledgeRepository repository;
    private final DocumentTextExtractor textExtractor;

    public KnowledgeImportService(KnowledgeRepository repository, DocumentTextExtractor textExtractor) {
        this.repository = repository;
        this.textExtractor = textExtractor;
    }

    public ImportResult importFile(MultipartFile file, String category) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        var filename = file.getOriginalFilename() == null ? "未命名文档" : file.getOriginalFilename();
        var extracted = textExtractor.extract(file.getInputStream(), filename);
        var text = extracted.text().replace("\r\n", "\n").trim();
        if (text.isBlank()) {
            throw new IllegalArgumentException("文档内容不能为空");
        }
        var chunks = List.of(text.split("\\n\\s*\\n"));
        for (int index = 0; index < chunks.size(); index++) {
            repository.save(new KnowledgeDocument(
                    UUID.randomUUID().toString(),
                    filename + "#" + (index + 1),
                    chunks.get(index).trim(),
                    category == null || category.isBlank() ? "general" : category,
                    null));
        }
        return new ImportResult(filename, chunks.size());
    }

    public record ImportResult(String filename, int chunks) {}
}
