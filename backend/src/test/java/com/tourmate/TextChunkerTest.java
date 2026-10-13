package com.tourmate;

import com.tourmate.knowledge.TextChunker;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TextChunkerTest {
    @Test void boundsLongParagraphsAndKeepsOverlap() {
        var text = "学生优惠".repeat(500);
        var chunks = new TextChunker().split(text);
        assertThat(chunks).allMatch(chunk -> chunk.length() <= 800);
        assertThat(chunks.getFirst()).isEqualTo(text.substring(0, 800));
        assertThat(chunks.get(1)).isEqualTo(text.substring(700, 1500));
        assertThat(chunks.getLast()).endsWith(text.substring(text.length() - 100));
    }

    @Test void respectsParagraphsAndSkipsEmptyContent() {
        var chunker = new TextChunker();
        assertThat(chunker.split("开放时间\r\n\r\n  学生票优惠  ")).containsExactly("开放时间", "学生票优惠");
        assertThat(chunker.split(" \n\n ")).isEmpty();
    }
}
