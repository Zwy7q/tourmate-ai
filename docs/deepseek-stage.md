# DeepSeek 对话模型接入

## 配置与启动

在项目根目录的 `.env` 中填写 `DEEPSEEK_API_KEY`。默认地址为 `https://api.deepseek.com`，默认模型为 `deepseek-chat`。不需要把密钥发到聊天中。

后端启动时通过 Spring Boot 配置导入读取 `.env`，文件按 properties 格式解析：写 `KEY=值`，不加引号，不写 export。环境变量优先于文件。支持从 backend 目录启动，或从项目根目录启动打包后的 JAR。

从 backend 目录启动真实模型（内存示例知识库）：

```powershell
mvn -s maven-central-settings.xml spring-boot:run '-Dspring-boot.run.profiles=deepseek'
```

需要 Redis 时，先启动 Docker 服务，再使用 profiles `deepseek,redis`。需要 PostgreSQL 时使用 `deepseek,postgres,redis`，并先导入政策文档。对话使用 DeepSeek，向量默认仍为演示向量；真实向量模型需要另外配置，不可混用不同模型生成的向量。

未启用 deepseek profile 时，使用离线规则回复，自动化测试不会使用真实密钥或产生模型费用。

## 一次请求如何执行

1. ChatService 读取该会话最近 20 条消息；简单的“那、它、如果”追问会结合最近用户问题做检索。
2. TourmateAgent 识别意图，检索知识库，返回政策引用及业务状态。
3. DeepSeekAnswerGenerator 将历史、最多两条检索资料和当前问题传给 Spring AI ChatClient。
4. 模型返回文本片段，SSE 依次发送 meta、delta 和 done；模型失败发送 error 事件。
5. 完整生成后保存用户问题与模型回答。生成失败或中断时不保存不完整的助手回答。

同步接口 `POST /api/chat` 也通过相同流程生成答案，返回 AgentResponse。

## 可重复测试

```powershell
mvn -s maven-central-settings.xml clean verify
```

ChatServiceTest 验证上下文、检索资料、真实片段、最终回答及失败不保存。
DeepSeekClientTest 使用本机 HTTP 服务器模拟兼容的模型接口，验证真实 Spring AI 请求格式和 SSE 解析，不调用线上模型。
ChatHttpTest 验证多轮 HTTP 请求与 SSE 事件。

## 线上联调记录

2026-10-13 使用本地密钥完成真实 DeepSeek 调用：

- 根据示例知识回答故宫开放时间，返回引用编号。
- 同一会话追问“那学生票呢？”，模型结合历史说明资料未提供具体票价；SSE 返回 91 个文本片段及最终结果，Redis 保存了两轮共 4 条消息。
- 启用 deepseek、postgres、redis，导入“TourMate演示景区学生票为18元，需要学生证”后，模型回答 18 元并引用该文档，验证文档入库、PGVector 检索、模型生成与会话保存链路。

上述政策均为项目演示资料，不作为真实景区票务依据。默认字符哈希向量只能验证链路，真实语义检索仍需接入 Embedding 模型。

## 本阶段边界

- 采用 Spring AI 的兼容接口调用 DeepSeek，尚未引入 Spring AI Alibaba Agent 编排。
- 对话历史用于提示词和基础追问补全，尚未实现摘要或复杂意图重写。
- 引用编号由提示词约束，尚未实现生成结果的引用校验。
- 天气仍为明确标注的演示数据，MCP 与真实订单工具待后续接入。
- 退款咨询不会触发确认流程；明确退款请求仅演示确认，不执行真实退款。
