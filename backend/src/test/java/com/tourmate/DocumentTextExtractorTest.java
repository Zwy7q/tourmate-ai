package com.tourmate;

import com.tourmate.knowledge.DocumentTextExtractor;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DocumentTextExtractorTest {
    @Test void extractsPlainTextAndMetadata() throws Exception {
        var input = new ByteArrayInputStream("故宫退票规则".getBytes(StandardCharsets.UTF_8));
        var result = new DocumentTextExtractor().extract(input, "policy.txt");
        assertThat(result.text()).contains("故宫退票规则");
        assertThat(result.mediaType()).contains("text/plain");
    }
}
