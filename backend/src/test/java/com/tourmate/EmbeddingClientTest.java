package com.tourmate;

import com.tourmate.knowledge.EmbeddingClient;
import org.junit.jupiter.api.Test;
import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmbeddingClientTest {
    @Test void cheapBailianFlashUsesRemote1024VectorsAndSeparateIdentity() throws Exception {
        var request = new AtomicReference<String>();
        var authorization = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/embeddings", exchange -> {
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            var bytes = ("{\"data\":[{\"embedding\":[1" + ",0".repeat(1023) + "]}]}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/embeddings";
            var client = new EmbeddingClient(endpoint, "test-key", "qwen3.7-text-embedding-flash", "remote");
            assertThat(client.embed("学生票")).hasSize(1024);
            assertThat(authorization.get()).isEqualTo("Bearer test-key");
            var json = new ObjectMapper().readTree(request.get());
            assertThat(json.path("dimensions").asInt()).isEqualTo(1024);
            assertThat(json.path("encoding_format").asText()).isEqualTo("float");
            assertThat(json.path("model").asText()).isEqualTo("qwen3.7-text-embedding-flash");
            assertThat(client.vectorColumn()).isEqualTo("embedding_local");
            assertThat(client.identity()).startsWith("remote:qwen3.7-text-embedding-flash:1024:")
                    .isNotEqualTo(new EmbeddingClient(endpoint, "", "qwen3-embedding:0.6b", "ollama").identity());
        } finally { server.stop(0); }
    }

    @Test void callsLocalOllamaWithoutAnApiKey() throws Exception {
        var request = new AtomicReference<String>();
        var authorization = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/embed", exchange -> {
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            var bytes = ("{\"embeddings\":[[1" + ",0".repeat(1023) + "]]}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var client = new EmbeddingClient("http://127.0.0.1:" + server.getAddress().getPort() + "/api/embed", "", "qwen3-embedding:0.6b", "ollama");
            assertThat(client.embed("学生票")).hasSize(1024);
            assertThat(authorization.get()).isNull();
            assertThat(new ObjectMapper().readTree(request.get()).path("model").asText()).isEqualTo("qwen3-embedding:0.6b");
        } finally { server.stop(0); }
    }

    @Test void sendsExplicitFloatEncodingAndValidatesRemoteVectors() throws Exception {
        var request = new AtomicReference<String>();
        var response = new AtomicReference<>("{\"data\":[{\"embedding\":[1" + ",0".repeat(1535) + "]}]}");
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/embeddings", exchange -> {
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var bytes = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var client = new EmbeddingClient("http://127.0.0.1:" + server.getAddress().getPort() + "/embeddings", "test-key", "text-embedding-v4", "remote");
            assertThat(client.embed("退票政策")).hasSize(1536);
            var json = new ObjectMapper().readTree(request.get());
            assertThat(json.path("dimensions").asInt()).isEqualTo(1536);
            assertThat(json.path("encoding_format").asText()).isEqualTo("float");
            assertThat(json.path("model").asText()).isEqualTo("text-embedding-v4");
            response.set("{\"data\":[{\"embedding\":[0" + ",0".repeat(1535) + "]}]}");
            assertThatThrownBy(() -> client.embed("测试")).isInstanceOf(IllegalStateException.class);
            response.set("{\"data\":[{\"embedding\":[1,2]}]}");
            assertThatThrownBy(() -> client.embed("测试")).isInstanceOf(IllegalStateException.class);
            response.set("{\"data\":[{\"embedding\":[\"invalid\"" + ",0".repeat(1535) + "]}]}");
            assertThatThrownBy(() -> client.embed("测试")).isInstanceOf(IllegalStateException.class);
            response.set("{\"data\":[{\"embedding\":[1e999" + ",0".repeat(1535) + "]}]}");
            assertThatThrownBy(() -> client.embed("测试")).isInstanceOf(IllegalStateException.class);
        } finally { server.stop(0); }
    }

    @Test void doesNotExposeUpstreamErrorsOrCredentials() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/embeddings", exchange -> {
            var bytes = "credential-secret upstream-details".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var client = new EmbeddingClient("http://127.0.0.1:" + server.getAddress().getPort() + "/embeddings", "credential-secret", "text-embedding-v4", "remote");
            assertThatThrownBy(() -> client.embed("政策")).isInstanceOf(IllegalStateException.class)
                    .hasMessage("向量服务调用失败，请检查密钥、模型权限与接口配置").hasNoCause();
        } finally { server.stop(0); }
    }

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
