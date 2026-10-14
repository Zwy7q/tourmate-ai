# TourMate AI 文旅票务智能客服与行程助手

TourMate AI 是一个面向景区和票务平台的智能客服系统。用户可以咨询景区开放时间、门票价格、退改规则和天气信息；系统会根据问题类型检索知识库或调用工具。对于退款、改签等有副作用的操作，系统会先要求用户确认。

## 当前版本功能

已提供 Vue 3 中文工作台：智能客服、行程助手、知识库上传与检索，支持手机布局。启动与演示步骤见 [前端使用说明](docs/frontend-stage.md)。

- `GET /api/health`：服务健康检查
- `POST /api/chat`：同步问答，返回意图、答案、知识引用和确认信息
- `POST /api/chat/stream`：通过 SSE 流式返回答案
- `GET /api/knowledge/search`：搜索知识库
- `POST /api/knowledge/documents`：新增知识文档
- `POST /api/knowledge/upload`：上传并解析文档，按段落写入知识库
- `GET /api/conversations/{id}/messages`：读取会话最近消息
- `POST /api/refunds/confirm`：确认退款操作
- `POST /api/itineraries/plan`：收集需求、生成或修改结构化行程
- `GET /api/itineraries/{conversationId}`：读取保存的行程

当前 MVP 已内置景区开放时间、票价和退改规则示例知识，并提供确定性天气工具。不配置外部模型 API Key 也可以验证完整业务流程。

启用 `deepseek,agent` 后使用 Spring AI Alibaba ReAct Agent，经独立 MCP 服务查询真实天气与虚构演示订单，不执行真实退款。完整启动步骤与 MCP 解释见 [Agent 与 MCP 工具说明](docs/mcp-agent-stage.md)。

行程工作流支持需求追问、按天规划、室内要求、天气与雨天备选、门票预算校验及Redis版本保存，详见 [行程规划说明](docs/itinerary-stage.md)。需要先导入结构化景点资料；提供6份虚构演示资料与导入脚本。预算目前只核验已知门票，不含住宿、交通和餐饮。

文档上传接口当前使用 Apache Tika 识别文件类型并提取文本，支持纯文本、Markdown 以及 Tika 已安装解析器支持的文档格式。

在调用聊天接口时传入 `conversationId`，系统会保存用户消息和助手回答；默认采用内存窗口，启用 redis profile 后写入 Redis。启用 deepseek profile 后，历史用于模型上下文，并支持基础追问补全。

## 本地启动

已支持 DeepSeek 对话生成、本地 .env 配置加载与模型流式输出。启动命令及业务流程见 [DeepSeek 接入说明](docs/deepseek-stage.md)。

真实向量检索使用 Ollama 本地 Qwen3 模型，对话继续使用 DeepSeek，只有 DeepSeek 需要 API Key，详见 [本地向量检索说明](docs/local-embedding-stage.md)。已支持长文分块、模型来源隔离与旧向量分批重建。

环境要求：Java 21、Maven 3.9+。PGVector 和 Redis 联调需要 Docker Desktop 正常运行。

```powershell
cd backend
mvn spring-boot:run
```

如果本机 Maven 镜像无法下载 Tika 解析器，可以使用项目提供的 Maven Central 配置：

```powershell
mvn -s maven-central-settings.xml spring-boot:run
```

服务默认地址为 `http://localhost:8080`。

启动数据库基础设施：

```powershell
cd infra
docker compose up -d
```

PostgreSQL 映射到 `localhost:5433`，Redis 映射到 `localhost:6380`。数据库首次启动时会自动创建文档、向量、会话消息和全文检索表。

启用 PostgreSQL 与 Redis 时，在 backend 目录执行：

```powershell
mvn -s maven-central-settings.xml spring-boot:run '-Dspring-boot.run.profiles=postgres,redis'
```

默认使用演示向量。真实模型模式、完整测试命令和当前实现边界见 [存储阶段说明](docs/storage-stage.md)。

## 接口示例

```powershell
Invoke-RestMethod http://localhost:8080/api/chat -Method Post -ContentType 'application/json' -Body '{"message":"故宫几点开放？"}'
```

明确提出“我要退票”等操作请求时会返回一次性确认令牌；普通退票政策咨询不会触发确认。以下接口仅模拟退款，不处理真实订单：

```powershell
Invoke-RestMethod http://localhost:8080/api/refunds/confirm `
  -Method Post `
  -ContentType 'application/json' `
  -Body '{"token":"上一步返回的令牌"}'
```

## 开发计划

已完成文档解析、PGVector 存储与本地 Qwen3 向量检索、Redis 会话、DeepSeek 对话、Spring AI Alibaba Agent 与 MCP 天气／演示订单工具、结构化行程工作流和 Vue 3 中文工作台。后续计划：

1. 完善中文检索、重排序与问答评测。
2. 增加限流与调用链日志，完善部署与演示脚本。
3. 按需扩展地图工具和更完整的行程预算能力。
