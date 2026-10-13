package com.tourmate.knowledge;

import java.time.Duration;
import java.util.Map;
import java.util.Locale;
import java.util.Arrays;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

/** 演示模式使用归一化字符哈希向量；生产模式调用兼容 embeddings 的真实模型接口。 */
@Component
public class EmbeddingClient {
    private final String url;
    private final String apiKey;
    private final String model;
    private final String mode;

    public EmbeddingClient(@Value("${tourmate.embedding.url:}") String url,
                           @Value("${tourmate.embedding.api-key:}") String apiKey,
                           @Value("${tourmate.embedding.model:text-embedding-v3}") String model,
                           @Value("${tourmate.embedding.mode:demo}") String mode) {
        this.url = url; this.apiKey = apiKey; this.model = model; this.mode = mode;
    }

    public double[] embed(String text) {
        if ("demo".equals(mode)) return demoVector(text);
        if (!"remote".equals(mode) || url.isBlank() || apiKey.isBlank()) {
            throw new IllegalStateException("真实向量模型需要配置地址和 API Key");
        }
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));
        var response = RestClient.builder().requestFactory(factory).build().post().uri(url)
                .header("Authorization", "Bearer " + apiKey)
                .body(Map.of("model", model, "input", text, "dimensions", 1536))
                .retrieve().body(JsonNode.class);
        var values = response == null ? null : response.path("data").path(0).path("embedding");
        if (values == null || !values.isArray() || values.size() != 1536) {
            throw new IllegalStateException("向量模型响应必须包含 1536 维 embedding");
        }
        var vector = new double[1536];
        for (int i = 0; i < vector.length; i++) {
            if (!values.get(i).isNumber()) throw new IllegalStateException("向量响应包含非数值元素");
            vector[i] = values.get(i).asDouble();
        }
        return vector;
    }

    private double[] demoVector(String text) {
        var vector = new double[1536];
        var normalized = text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        for (int i = 0; i < normalized.length(); i++) {
            var token = normalized.substring(i, Math.min(i + 2, normalized.length()));
            vector[Math.floorMod(token.hashCode(), vector.length)]++;
        }
        double norm = Math.sqrt(Arrays.stream(vector).map(value -> value * value).sum());
        if (norm > 0) for (int i = 0; i < vector.length; i++) vector[i] /= norm;
        return vector;
    }

    public static String literal(double[] vector) {
        return Arrays.toString(vector);
    }
}
