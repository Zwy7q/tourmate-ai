# 简历项目描述

## TourMate AI 文旅票务智能客服与行程助手

**项目简介：** 面向景区和票务平台的智能客服系统，为用户提供景区信息、票务政策、天气和路线查询。系统结合知识库和外部工具回答用户问题，并在退款、改签等敏感操作前进行人工确认。

**技术栈：** Java 21、Spring Boot、Spring AI Alibaba、PostgreSQL、PGVector、Redis、MCP、Vue 3、Docker。

**主要工作：**

1. 基于 Spring AI Alibaba 实现 Agent 意图识别与任务路由，将用户请求分发到知识问答、天气查询、路线规划和订单处理等业务链路。

2. 使用 Apache Tika 完成 PDF、Word 和 Markdown 文档解析，结合 PGVector 向量检索、关键词检索和 Rerank 重排序，实现带来源引用的票务政策问答。

3. 基于 MCP 接入天气、路线和订单查询工具，对退款、改签等有副作用的操作增加人工确认、一次性令牌和幂等校验，避免 Agent 越权执行。

4. 使用 Redis 保存多轮会话上下文，基于 SSE 实现流式对话；增加调用超时、重试、限流和问答评测，提升系统稳定性和可维护性。

**简历精简版：**

> 面向景区和票务平台构建文旅智能客服 Agent，支持景区信息、票务政策、天气和路线查询。基于 Spring AI Alibaba 实现意图路由和 Agent 工作流，使用 Apache Tika、PGVector 和 Rerank 完成文档解析与混合检索；通过 MCP 接入天气、路线和订单工具，并对退款、改签操作增加人工确认；使用 Redis 管理多轮会话，基于 SSE 实现流式问答。
