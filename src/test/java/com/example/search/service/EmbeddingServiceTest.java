package com.example.search.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmbeddingServiceTest {

    @Test
    void localEmbeddingHasConfiguredDimension() {
        EmbeddingService service = new EmbeddingService(
                "local", "", "us-central1", "gemini-embedding-001", 32, 2000);

        var vector = service.queryEmbedding("distributed search");

        assertThat(vector).hasSize(32);
        assertThat(vector).anyMatch(value -> value != 0f);
    }
}
