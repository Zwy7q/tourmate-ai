package com.tourmate.knowledge;

import java.util.List;
import java.util.ArrayList;
import org.springframework.stereotype.Component;

@Component
public class TextChunker {
    public List<String> split(String text) {
        if (text == null || text.isBlank()) return List.of();
        var chunks = new ArrayList<String>();
        for (var paragraph : text.replace("\r\n", "\n").split("\\n\\s*\\n")) {
            var normalized = paragraph.trim();
            if (normalized.isEmpty()) continue;
            for (int start = 0; start < normalized.length(); start += 700) {
                int end = Math.min(start + 800, normalized.length());
                chunks.add(normalized.substring(start, end));
                if (end == normalized.length()) break;
            }
        }
        return List.copyOf(chunks);
    }
}
