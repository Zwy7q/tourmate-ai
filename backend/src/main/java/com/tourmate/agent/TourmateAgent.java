package com.tourmate.agent;

import com.tourmate.knowledge.KnowledgeRepository;
import com.tourmate.refund.RefundConfirmation;
import com.tourmate.refund.RefundService;
import com.tourmate.tool.WeatherTool;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TourmateAgent {
    private final IntentRouter intentRouter;
    private final KnowledgeRepository knowledgeRepository;
    private final WeatherTool weatherTool;
    private final RefundService refundService;

    public TourmateAgent(IntentRouter intentRouter, KnowledgeRepository knowledgeRepository,
                         WeatherTool weatherTool, RefundService refundService) {
        this.intentRouter = intentRouter;
        this.knowledgeRepository = knowledgeRepository;
        this.weatherTool = weatherTool;
        this.refundService = refundService;
    }

    public AgentResponse answer(String message) {
        var intent = intentRouter.route(message);
        var citations = knowledgeRepository.search(message, 2);
        return switch (intent) {
            case WEATHER -> {
                var weather = weatherTool.query("北京", "明天");
                yield new AgentResponse(intent,
                        weather.city() + "" + weather.date() + "预计" + weather.condition() + "，气温约 "
                                + weather.temperatureCelsius() + "°C。天气信息仅供参考，是否可以退票仍以票务规则为准。",
                        citations, false, null);
            }
            case REFUND_POLICY -> {
                if (!(message.contains("我要退票") || message.contains("帮我退票") || message.contains("提交退款"))) {
                    yield new AgentResponse(intent, citations.isEmpty()
                            ? "未检索到适用的退改政策，无法确认是否可以退款。"
                            : "退票需依据适用政策，咨询不会提交退款申请。" + citations.get(0).document().content(),
                            citations, false, null);
                }
                RefundConfirmation confirmation = refundService.requestConfirmation();
                yield new AgentResponse(intent,
                        "根据当前退改规则，未使用且未超过参观日的门票可以申请退票。若你要提交退款申请，请确认本次操作。",
                        citations, true, confirmation.token());
            }
            case ATTRACTION_INFO -> new AgentResponse(intent,
                    citations.isEmpty() ? "暂未找到相关景区资料，请补充景区名称。" : citations.get(0).document().content(),
                    citations, false, null);
            case GENERAL_CHAT -> new AgentResponse(intent, "你好，我可以帮你查询景区开放时间、票价、退改规则和天气。", List.of(), false, null);
            case UNKNOWN -> new AgentResponse(intent, "我暂时只能处理景区信息、票务政策和天气问题，请换一种方式描述。", List.of(), false, null);
        };
    }
}
