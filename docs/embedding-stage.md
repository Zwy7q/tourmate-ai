# 百炼低价向量模型配置

当前用户选择：**DeepSeek 对话 + 百炼 qwen3.7-text-embedding-flash 向量模型**。不需要启动或下载 Ollama 模型。仍可选择 [本地向量方案](local-embedding-stage.md)。

## 价格与用途

2026-10-14查询[百炼官方价目页](https://help.aliyun.com/zh/model-studio/model-pricing)，北京地域 qwen3.7-text-embedding-flash 实时输入价格为 **0.125元/百万Token**（约0.000125元/千Token），比 text-embedding-v4 的0.5元/百万Token便宜75%。例如100万输入Token约0.125元，仅计算向量费用；DeepSeek生成回答单独计费。免费额度及有效期以百炼控制台为准。

向量模型负责把资料和问题转为向量，用于检索，不负责聊天或工具调用。选用的是文本向量价目中的低价型号，不将图文向量或Batch价格与实时文本调用混比。

## 配置与启动

项目根目录 `.env` 填写北京地域百炼 Key：

```dotenv
DASHSCOPE_API_KEY=在本地填写，不要提交
TOURMATE_EMBEDDING_MODEL=qwen3.7-text-embedding-flash
TOURMATE_EMBEDDING_URL=https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings
```

`DEEPSEEK_API_KEY`、`DEEPSEEK_MODEL` 保持原值。embedding profile 已默认选择上述型号，不填写两个 TOURMATE 配置也可使用默认值。

启动PostgreSQL与Redis，按 [MCP说明](mcp-agent-stage.md) 启动 tools-server；然后在backend目录执行：

```powershell
mvn -s maven-central-settings.xml spring-boot:run '-Dspring-boot.run.profiles=deepseek,postgres,redis,embedding,agent'
```

不要同时启用 `embedding` 和 `local-embedding`。向量改为云端后，不需要执行 `ollama pull`。前端启动方法保持不变，见 [前端使用说明](frontend-stage.md)。

## 旧资料重建

该型号实测返回1024维，项目使用现有1024维字段保存；其他旧远端型号保持1536维。本地模型和百炼即使维度相同，向量也不能混用，检索会校验模型、模式、端点与维度来源。

切换模型后必须重建已有资料的向量。接口每次最多20块，重复调用直到 `remaining` 为0；出现 failedChunkIds 时先检查计费、Key和网络，不要无限重试：

```powershell
Invoke-RestMethod 'http://127.0.0.1:8080/api/knowledge/reindex?limit=20' -Method Post
```

重建不删除原文，会替换被重建记录的1024维向量及来源标记。以后切回Ollama时也需要再次重建。重建未完成前，旧模型资料不会作为新模型的向量召回结果。

重建后可在知识库页搜索“学生票”，再在客服页询问演示景区票价。真实调用出现Arrearage时检查余额，401时检查Key地域和权限；失败不会返回伪造向量。

## 本次验证

2026-10-14：后端 `TOURMATE_INTEGRATION=true` 的 clean verify 共60项通过，失败、错误、跳过均为0。真实百炼调用成功返回1024维；9个已有资料块全部重建，remaining=0，无失败。数据库来源均为remote/qwen3.7-text-embedding-flash/1024。搜索命中演示学生票资料，DeepSeek基于资料回答23元及学生证，返回2条候选引用。前端代理健康检查正常，独立代码审查未发现阻塞问题。
