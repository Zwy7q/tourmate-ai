package com.tourmate;

import static org.assertj.core.api.Assertions.assertThat;

import com.tourmate.agent.Intent;
import com.tourmate.agent.IntentRouter;
import com.tourmate.agent.TourmateAgent;
import com.tourmate.knowledge.InMemoryKnowledgeRepository;
import com.tourmate.refund.RefundService;
import com.tourmate.tool.DeterministicWeatherTool;
import org.junit.jupiter.api.Test;

class TourmateAgentTest {
    @Test void agentModeDoesNotCallDemoWeatherBeforeModelChoosesTools() {
        com.tourmate.tool.WeatherTool forbiddenDemo = (city, date) -> { throw new AssertionError("Agent 不得提前查询演示天气"); };
        var live = new TourmateAgent(new IntentRouter(), new InMemoryKnowledgeRepository(), forbiddenDemo, new RefundService(), true);
        var answer = live.answer("杭州明天天气？");
        assertThat(answer.intent()).isEqualTo(Intent.WEATHER);
        assertThat(answer.answer()).contains("get_weather").doesNotContain("22°C", "北京");
    }
    private final TourmateAgent agent = new TourmateAgent(new IntentRouter(), new InMemoryKnowledgeRepository(),
            new DeterministicWeatherTool(), new RefundService());

    @Test
    void answersAttractionQuestionWithCitation() {
        var response = agent.answer("故宫几点开放？");
        assertThat(response.intent()).isEqualTo(Intent.ATTRACTION_INFO);
        assertThat(response.citations()).isNotEmpty();
        assertThat(response.answer()).contains("8:30");
    }

    @Test
    void asksForConfirmationBeforeRefund() {
        var response = agent.answer("我要退票");
        assertThat(response.intent()).isEqualTo(Intent.REFUND_POLICY);
        assertThat(response.confirmationRequired()).isTrue();
        assertThat(response.confirmationToken()).isNotBlank();
    }

    @Test
    void policyConsultationDoesNotCreateRefundAction() {
        var response = agent.answer("下雨了可以退票吗？");
        assertThat(response.intent()).isEqualTo(Intent.REFUND_POLICY);
        assertThat(response.confirmationRequired()).isFalse();
        assertThat(response.confirmationToken()).isNull();
    }

    @Test
    void handlesUnknownQuestionSafely() {
        var response = agent.answer("帮我写一首诗");
        assertThat(response.intent()).isEqualTo(Intent.UNKNOWN);
        assertThat(response.answer()).contains("暂时只能处理");
    }
}
