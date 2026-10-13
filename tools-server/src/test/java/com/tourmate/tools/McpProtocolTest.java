package com.tourmate.tools;

import java.time.Duration;
import java.util.Map;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpProtocolTest {
    @LocalServerPort int port;

    @Test void discoversSchemasAndCallsReadOnlyToolsOverRealMcp() {
        var transport = HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + port).endpoint("/mcp").build();
        try (var client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(10)).build()) {
            client.initialize();
            var tools = client.listTools().tools();
            assertThat(tools).extracting(McpSchema.Tool::name).containsExactlyInAnyOrder("get_weather", "get_demo_order");
            var order = client.callTool(new McpSchema.CallToolRequest("get_demo_order", Map.of("orderId", "DEMO-1001")));
            assertThat(order.isError()).isFalse();
            assertThat(order.content().toString()).contains("DEMO-1001", "demo", "18");
            var unknown = client.callTool(new McpSchema.CallToolRequest("get_demo_order", Map.of("orderId", "REAL-0001")));
            assertThat(unknown.content().toString()).contains("未找到演示订单");
            var invalid = client.callTool(new McpSchema.CallToolRequest("get_weather", Map.of("city", "杭州", "date", "wrong")));
            assertThat(invalid.content().toString()).contains("YYYY-MM-DD");
        }
    }
}
