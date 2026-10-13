# TourMate AI 文旅票务智能客服与行程助手

TourMate AI 是一个面向景区和票务平台的智能客服系统。用户可以咨询景区开放时间、门票价格、退改规则和天气信息；系统会根据问题类型检索知识库或调用工具。对于退款、改签等有副作用的操作，系统会先要求用户确认。

## 当前版本功能

- `GET /api/health`：服务健康检查
- `POST /api/chat`：同步问答，返回意图、答案、知识引用和确认信息
- `POST /api/chat/stream`：通过 SSE 流式返回答案
- `GET /api/knowledge/search`：搜索知识库
- `POST /api/knowledge/documents`：新增知识文档
- `POST /api/knowledge/upload`：上传并解析文档，按段落写入知识库
- `GET /api/conversations/{id}/messages`：读取会话最近消息
- `POST /api/refunds/confirm`：确认退款操作

当前 MVP 已内置景区开放时间、票价和退改规则示例知识，并提供确定性天气工具。不配置外部模型 API Key 也可以验证完整业务流程。

文档上传接口当前使用 Apache Tika 识别文件类型并提取文本，支持纯文本、Markdown 以及 Tika 已安装解析器支持的文档格式。

在调用聊天接口时传入 `conversationId`，系统会保存用户消息和助手回答；默认采用内存窗口，启用 redis profile 后写入 Redis。历史目前仅用于保存与查询，尚未用于追问补全。

## 本地启动

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

退款问题会返回一次性确认令牌：

```powershell
Invoke-RestMethod http://localhost:8080/api/refunds/confirm `
  -Method Post `
  -ContentType 'application/json' `
  -Body '{"token":"上一步返回的令牌"}'
```

## 开发计划

1. 将内存知识库替换为 PostgreSQL 和 PGVector。
2. 使用 Apache Tika 支持 PDF、Word 和 Markdown 文档导入。
3. 接入 Spring AI Alibaba 和真实大模型。
4. 使用 MCP 接入天气、地图和景区实时状态工具。
5. 增加 Redis 会话记忆、限流和调用链日志。
6. 增加 Vue 3 前端和问答评测数据集。
