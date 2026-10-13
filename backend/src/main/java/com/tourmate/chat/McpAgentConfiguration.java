package com.tourmate.chat;

import java.time.Duration;
import java.util.Set;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.mcp.SyncMcpToolCallback;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("deepseek & agent")
public class McpAgentConfiguration {
    @Bean(destroyMethod = "close")
    McpSyncClient travelMcpClient(@Value("${tourmate.mcp.base-url:http://127.0.0.1:18090}") String baseUrl) {
        var transport = HttpClientStreamableHttpTransport.builder(baseUrl).endpoint("/mcp")
                .connectTimeout(Duration.ofSeconds(5)).build();
        var client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(25))
                .initializationTimeout(Duration.ofSeconds(10)).build();
        try { client.initialize(); return client; }
        catch (RuntimeException exception) {
            client.close();
            throw new IllegalStateException("MCP 工具服务未就绪，请先启动 tools-server");
        }
    }

    @Bean
    AnswerGenerator agentAnswerGenerator(ChatModel model, McpSyncClient travelMcpClient) {
        var allowed = Set.of("get_weather", "get_demo_order");
        var tools = travelMcpClient.listTools().tools().stream().filter(tool -> allowed.contains(tool.name()))
                .map(tool -> new SyncMcpToolCallback(travelMcpClient, tool)).toArray(ToolCallback[]::new);
        if (tools.length != 2) throw new IllegalStateException("MCP 服务必须提供天气与演示订单两个只读工具");
        return new AlibabaAgentAnswerGenerator(model, tools);
    }
}
