package com.tourmate.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

public class DemoOrderTools {
    @Tool(name = "get_demo_order", description = "仅查询虚构演示订单，绝非真实订单系统。用户必须明确提供 orderId，唯一可用编号为 DEMO-1001；不能退款或改签。")
    public DemoOrder getOrder(@ToolParam(description = "用户明确提供的演示订单号") String orderId) {
        if ("DEMO-1001".equals(orderId)) {
            return new DemoOrder(true, true, orderId, "青岚湖演示景区", 18, "虚构演示订单，未使用；不代表真实订单，查询不会提交退款");
        }
        return new DemoOrder(false, true, orderId, null, null, "未找到演示订单");
    }
    public record DemoOrder(boolean found, boolean demo, String orderId, String attraction, Integer priceYuan, String message) {}
}
