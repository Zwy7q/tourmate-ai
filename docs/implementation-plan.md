# TourMate AI 文旅票务智能客服与行程助手实施计划

> 本计划用于指导 TourMate AI 的分阶段开发。每个阶段都必须先写测试、实现功能，再运行验证。

**目标：** 构建一个面向景区和票务平台的 Java Agent，支持知识库问答、天气工具、退款人工确认、流式输出，并逐步接入 PGVector、真实大模型、MCP 和 Vue 前端。

**总体架构：** Spring Boot 后端负责接口、意图路由和 Agent 编排；知识检索和外部工具都通过接口隔离。第一阶段使用内存适配器保证项目不依赖外部服务即可运行，后续替换为 PGVector、真实模型和 MCP，不改变对外接口。

**技术栈：** Java 21、Spring Boot、Maven、Spring Web、Bean Validation、JUnit 5、PostgreSQL/PGVector、Redis、Spring AI Alibaba、SSE、Vue 3、Docker。

## 当前进度（2026-10-13）

已完成后端基础、Tika 解析与分块、PGVector 本地真实向量检索与重建、Redis 会话、DeepSeek 流式对话、Spring AI Alibaba ReAct Agent、MCP 真实天气与演示订单查询、演示退款人工确认。见 [Agent 与 MCP 工具阶段](mcp-agent-stage.md)。

未完成：行程规划、Vue 前端、Rerank、限流与统一评测。下文保留原设计顺序，不表示每个扩展项均已实现。简历为目标稿；正式投递时保留可以演示和解释的能力，不填写尚未测量的收益。

## 第一阶段：后端基础与健康检查

文件：`tourmate-ai/backend/pom.xml`、`TourmateApplication.java`、`HealthController.java`。

- 创建 Spring Boot 3.4 项目，使用 Java 21。
- 增加 `GET /api/health` 健康检查接口。
- 编写 MVC 测试，验证接口返回 `status=UP`。
- 执行 `mvn test -q`，确认测试通过。

## 第二阶段：知识库和检索接口

文件：`knowledge/KnowledgeDocument.java`、`KnowledgeRepository.java`、`knowledge/InMemoryKnowledgeRepository.java`、`api/KnowledgeController.java`。

- 定义知识文档、检索结果和知识库接口。
- 使用内存适配器实现确定性关键词召回，并返回文档来源和分数。
- 内置景区开放时间、门票价格和退改规则示例文档。
- 增加文档新增接口和搜索接口。
- 测试“退票”问题优先命中退改规则文档。

## 第三阶段：意图路由和 Agent 编排

文件：`agent/Intent.java`、`agent/IntentRouter.java`、`agent/TourmateAgent.java`、`api/ChatController.java`。

- 将请求分为票务政策、天气、景区信息、普通对话和未知问题。
- Agent 先识别意图，再调用知识库或工具，最后组织回答和引用来源。
- 对无法识别的问题返回安全兜底，不编造票务政策。
- 增加 `POST /api/chat` 同步问答接口。
- 测试景区咨询、退票咨询和未知问题三类流程。

## 第四阶段：天气工具和人工确认

文件：`tool/WeatherTool.java`、`tool/DeterministicWeatherTool.java`、`refund/RefundService.java`、`refund/RefundConfirmation.java`。

- 抽象天气工具接口，第一阶段使用确定性实现。
- 天气问题调用工具并返回结构化天气结果。
- 退款问题只生成一次性确认令牌，不直接执行退款。
- 增加 `POST /api/refunds/confirm`，令牌只能使用一次并具有过期时间。
- 测试确认前不改变状态、确认后状态变更、重复确认失败。

## 第五阶段：SSE 流式输出与项目文档

文件：`api/ChatController.java`、`README.md`、`docs/resume-project.md`。

- 增加 `POST /api/chat/stream`。
- 按 `meta`、`delta`、`done` 事件返回意图、答案片段和最终结果。
- 编写中文 README，说明启动方式、接口示例和当前功能边界。
- 编写中文简历项目目标稿，并明确实际进度；正式投递版本只保留已完成的功能。
- 执行 `mvn test -q` 和 `mvn package -q -DskipTests`。

## 第六阶段：接入 PostgreSQL、PGVector 和 Spring AI Alibaba

- 使用 Docker Compose 启动 PostgreSQL 和 PGVector。
- 增加文档分块、向量、来源、景区和生效日期字段。
- 使用 Apache Tika 解析 PDF、Word 和 Markdown。
- 实现 Embedding、向量检索、关键词检索和 Rerank 融合。
- 接入 Spring AI Alibaba，并保留无模型 API Key 的测试配置。
- 外部模型不可用时回退到可解释的错误提示，不返回伪造答案。

## 第七阶段：MCP、Redis、前端和评测

- 使用 MCP 接入天气、地图和景区实时状态工具。
- 使用 Redis 保存多轮会话、限流信息和一次性确认状态。
- 增加 Vue 3 前端，展示流式答案、知识引用、工具状态和确认按钮。
- 编写至少 20 条文旅问题评测数据，统计意图准确率、引用命中率和响应耗时。
- 完成部署拓扑、异常处理、接口文档和演示脚本。

## 验收标准

- 新环境执行启动命令后可以访问健康检查接口。
- 景区开放时间、票价、退票和天气问题能进入正确处理链路。
- 退款操作必须经过用户确认，确认令牌不能重复使用。
- SSE 客户端可以收到完整的开始、内容片段和结束事件。
- 每个简历中描述的技术点都有对应代码、测试或演示记录。
