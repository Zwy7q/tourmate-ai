package com.tourmate.knowledge;

import jakarta.validation.constraints.NotBlank;

public record KnowledgeDocument(
        String id,
        @NotBlank String title,
        @NotBlank String content,
        String category,
        String effectiveDate) {
}
