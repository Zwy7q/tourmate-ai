package com.tourmate.knowledge;

import java.time.Duration;
import java.util.Map;
import java.util.Locale;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClientException;

/** 演示模式使用归一化字符哈希向量；生产模式调用兼容 embeddings 的真实模型接口。 */
@Component
public class EmbeddingClient {
    private final String url;
    private final String apiKey;
    private final String model;
    private final String mode;
    private final RestClient client;

    public EmbeddingClient(@Value("${tourmate.embedding.url:}") String url,
                           @Value("${tourmate.embedding.api-key:}") String apiKey,
                           @Value("${tourmate.embedding.model:text-embedding-v4}") String model,
                           @Value("${tourmate.embedding.mode:demo}") String mode) {
        this.url = url; this.apiKey = apiKey; this.model = model; this.mode = mode;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(120));
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    public double[] embed(String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("向量输入不能为空");
        if ("demo".equals(mode)) return demoVector(text);
        boolean local = "ollama".equals(mode);
        if ((!local && !"remote".equals(mode)) || url.isBlank() || (!local && apiKey.isBlank())) {
            throw new IllegalStateException("真实向量模型需要配置地址和 API Key");
        }
        JsonNode response;
        try {
            var request = client.post().uri(url);
            if (!local) request.header("Authorization", "Bearer " + apiKey);
            response = request.body(local
                    ? Map.of("model", model, "input", text, "keep_alive", "5m")
                    : Map.of("model", model, "input", text, "dimensions", 1536, "encoding_format", "float"))
                    .retrieve().body(JsonNode.class);
        } catch (RestClientException exception) {
            // 不携带上游响应或异常原因，防止供应商错误正文泄漏配置。
            throw new IllegalStateException(local ? "本地向量服务调用失败，请检查 Ollama 与模型是否就绪"
                    : "向量服务调用失败，请检查密钥、模型权限与接口配置");
        }
        var values = response == null ? null : local ? response.path("embeddings").path(0)
                : response.path("data").path(0).path("embedding");
        if (values == null || !values.isArray() || values.size() != dimensions()) {
            throw new IllegalStateException("向量模型响应必须包含 " + dimensions() + " 维 embedding");
        }
        var vector = new double[dimensions()];
        double norm = 0;
        for (int i = 0; i < vector.length; i++) {
            if (!values.get(i).isNumber()) throw new IllegalStateException("向量响应包含非数值元素");
            vector[i] = values.get(i).asDouble();
            if (!Double.isFinite(vector[i])) throw new IllegalStateException("向量响应包含非有限值");
            norm = Math.hypot(norm, vector[i]);
        }
        if (norm == 0 || !Double.isFinite(norm)) throw new IllegalStateException("向量响应不能为零向量或无效向量");
        return vector;
    }

    public String identity() {
        if ("demo".equals(mode)) return "demo:char-hash-v1:1536";
        try {
            var endpoint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(url.getBytes(StandardCharsets.UTF_8)));
            return mode + ":" + model + ":" + dimensions() + ":" + endpoint;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("无法生成向量来源标记");
        }
    }

    public int dimensions() { return "ollama".equals(mode) ? 1024 : 1536; }

    /** 返回受控列名，不接收用户输入，不使用动态配置拼接 SQL 标识符。 */
    public String vectorColumn() { return "ollama".equals(mode) ? "embedding_local" : "embedding"; }

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
