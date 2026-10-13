package com.tourmate.agent;

import org.springframework.stereotype.Component;

@Component
public class IntentRouter {
    public Intent route(String message) {
        var text = message == null ? "" : message;
        if (containsAny(text, "退票", "退款", "退改", "改签")) return Intent.REFUND_POLICY;
        if (containsAny(text, "天气", "下雨", "温度", "晴天")) return Intent.WEATHER;
        if (containsAny(text, "开放", "几点", "票价", "门票", "景区", "故宫")) return Intent.ATTRACTION_INFO;
        if (containsAny(text, "你好", "您好", "谢谢", "在吗")) return Intent.GENERAL_CHAT;
        return Intent.UNKNOWN;
    }

    private boolean containsAny(String text, String... keywords) {
        for (var keyword : keywords) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }
}
