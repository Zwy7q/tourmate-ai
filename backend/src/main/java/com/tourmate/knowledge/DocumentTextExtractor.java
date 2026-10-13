package com.tourmate.knowledge;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Component;

@Component
public class DocumentTextExtractor {
    private final Tika tika = new Tika();

    public ExtractedText extract(InputStream input, String filename) throws IOException {
        var bytes = input.readAllBytes();
        var mediaType = tika.detect(bytes, filename);
        final String text;
        try {
            text = tika.parseToString(new ByteArrayInputStream(bytes)).trim();
        } catch (TikaException exception) {
            throw new IOException("无法解析文档: " + filename, exception);
        }
        var resolvedText = text.isBlank() && mediaType.startsWith("text/")
                ? new String(bytes, StandardCharsets.UTF_8).trim() : text;
        return new ExtractedText(filename, mediaType, resolvedText);
    }

    public record ExtractedText(String filename, String mediaType, String text) {}
}
