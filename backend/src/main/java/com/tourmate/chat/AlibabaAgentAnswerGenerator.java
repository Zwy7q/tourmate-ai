package com.tourmate.chat;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;
import java.time.Duration;
import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.tool.definition.ToolDefinition;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.cloud.ai.graph.streaming.OutputType;

public class AlibabaAgentAnswerGenerator implements AnswerGenerator {
    private final ChatModel model;
    private final ToolCallback[] tools;
    public AlibabaAgentAnswerGenerator(ChatModel model, ToolCallback[] tools) {
        this.model = model;
        this.tools = tools.clone();
    }
    @Override public Flux<String> generate(GenerationContext context) {
        return Flux.defer(() -> {
            var counter = new java.util.concurrent.atomic.AtomicInteger();
            var guarded = java.util.Arrays.stream(tools).map(tool -> new GuardedToolCallback(tool, counter)).toArray(ToolCallback[]::new);
            var agent = ReactAgent.builder().name("tourmate_agent").model(model).tools(guarded)
                    .systemPrompt(GenerationPrompt.system(true)).returnReasoningContents(false)
                    .enableLogging(false).parallelToolExecution(false)
                    .compileConfig(CompileConfig.builder().recursionLimit(14).build()).build();
            try {
                // A model can send text before its tool_call chunk. Wait for the whole
                // model round so intermediate text never becomes the answer or history.
                return agent.stream(GenerationPrompt.user(context))
                        .ofType(StreamingOutput.class)
                        .filter(output -> output.getOutputType() == OutputType.AGENT_MODEL_STREAMING
                                || output.getOutputType() == OutputType.AGENT_MODEL_FINISHED)
                        .timeout(Duration.ofSeconds(60))
                        .bufferUntil(output -> output.getOutputType() == OutputType.AGENT_MODEL_FINISHED)
                        .filter(round -> round.stream().anyMatch(output -> output.getOutputType() == OutputType.AGENT_MODEL_FINISHED))
                        .filter(round -> round.stream().noneMatch(output -> output.message() instanceof AssistantMessage message && message.hasToolCalls()))
                        .concatMapIterable(round -> round.stream()
                                .filter(output -> output.getOutputType() == OutputType.AGENT_MODEL_STREAMING)
                                .map(StreamingOutput::message).filter(AssistantMessage.class::isInstance)
                                .map(AssistantMessage.class::cast).map(AssistantMessage::getText)
                                .filter(text -> text != null && !text.isEmpty()).toList());
            } catch (Exception exception) {
                return Flux.error(new IllegalStateException("Agent 生成失败"));
            }
        });
    }

    private record GuardedToolCallback(ToolCallback delegate, java.util.concurrent.atomic.AtomicInteger counter) implements ToolCallback {
        public ToolDefinition getToolDefinition() { return delegate.getToolDefinition(); }
        @Override public String call(String input, org.springframework.ai.chat.model.ToolContext context) { return call(input); }
        public String call(String input) {
            if (counter.incrementAndGet() > 4) throw new IllegalStateException("工具调用次数超限");
            if (input == null || input.length() > 1000) return "{\"success\":false,\"message\":\"工具参数无效\"}";
            org.slf4j.LoggerFactory.getLogger(AlibabaAgentAnswerGenerator.class).info("MCP_TOOL_CALL {}", getToolDefinition().name());
            try { return delegate.call(input); }
            catch (RuntimeException exception) { return "{\"success\":false,\"message\":\"工具服务暂时不可用\"}"; }
        }
    }
}
