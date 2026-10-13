package com.tourmate;

import com.tourmate.knowledge.EmbeddingClient;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmbeddingClientTest {
    @Test void demoVectorsAreNormalizedStableAndHaveDatabaseDimension() {
        var client = new EmbeddingClient("", "", "", "demo");
        var vector = client.embed("海洋馆退票政策");
        assertThat(vector).hasSize(1536);
        assertThat(vector).containsExactly(client.embed("海洋馆退票政策"));
        double norm = 0;
        for (var value : vector) norm += value * value;
        assertThat(norm).isCloseTo(1, org.assertj.core.data.Offset.offset(0.00001));
    }

    @Test void remoteModeRefusesMissingCredentialsRatherThanFabricatingVectors() {
        var client = new EmbeddingClient("https://example.com", "", "model", "remote");
        assertThatThrownBy(() -> client.embed("海洋馆")).isInstanceOf(IllegalStateException.class);
    }
}
