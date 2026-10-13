# 真实向量检索与低成本模型配置

## 推荐模型

对话继续使用 DeepSeek 的 deepseek-chat；文本向量使用 DashScope text-embedding-v4。两者各用对应平台的 Key。DeepSeek 的 Key 不能调用 DashScope 向量接口。

截至 2026-10-13，阿里云官方文档列出的北京地域 text-embedding-v4 价格为 0.0005 元／千 Token，100 万 Token 约 0.5 元，仅指向量费用。模型支持 1536 维，无需修改原有 PGVector 字段维数。v3 不支持 1536 维，不能直接替换。实际计费、免费额度及价格变化以平台为准。

官方规格与价格：https://help.aliyun.com/zh/model-studio/embedding

文档入库时调用向量模型，查询时也调用一次；DeepSeek 生成回答另行计费。修改模型、接口地址或维数后需重建向量，因此开发时应固定配置，避免反复导入相同文件。文件去重和向量缓存尚未实现。

## 配置与启动

项目根目录 `.env` 填写 DEEPSEEK_API_KEY 和 DASHSCOPE_API_KEY。向量接口默认使用北京地域地址，模型默认 text-embedding-v4；密钥需要来自对应地域并拥有可用计费状态。

```powershell
cd infra
docker compose up -d
cd ../backend
mvn -s maven-central-settings.xml spring-boot:run '-Dspring-boot.run.profiles=deepseek,postgres,redis,embedding'
```

无需启动 Ollama，也不需要下载本地模型。完整 Key 示例仅在 `.env.example` 中留空，真实 `.env` 不提交。

## 文档入库与知识问答

文档经 Tika 解析后按段落切分；超过 800 字符的段落拆成多个块，连续块保留 100 字符重叠。直接新增文档同样支持分块。

每块使用 v4 生成 1536 维浮点向量，数据库同时记录模式、模型、维数与接口地址摘要。检索只使用与当前模型来源一致的向量，再将原文与历史对话交给 DeepSeek，生成带引用的回答。

旧向量来源未知时保留原文，但不参与当前检索。启动后通过维护接口重建：

```powershell
Invoke-RestMethod 'http://localhost:8080/api/knowledge/reindex?limit=20' -Method Post
```

每次最多处理 20 个旧块，返回 updated、skipped、failedChunkIds、remaining、message。可用 `&documentId=文档UUID` 限定一个文档。服务失败保留旧向量，恢复后重试；超过 800 字符的旧块需重新导入。不要在存在失败时无限重复调用。

更新会校验原始文本和来源，避免重建覆盖并发修改；该接口用于本地开发维护，公开部署需要管理员鉴权。

## 验证与问题排查

2026-10-13 已通过 29 项离线测试和 6 项 PostgreSQL／Redis 集成测试，覆盖模型请求格式、长文分块、来源隔离、原文保留、断点重建、失败处理及并发修改跳过。

当前真实 DashScope 调用已通过密钥鉴权，但供应商返回 Arrearage（账户欠费），线上向量与 DeepSeek 的完整联调需在账户恢复后执行。不要将本机模拟接口测试写成线上验证成功。

遇到服务错误：

- 401：检查 Key 和地域，不要把 DeepSeek Key 填进 DASHSCOPE_API_KEY。
- Arrearage：检查阿里云账户余额、欠费及模型服务计费状态。
- 维度不匹配：使用支持 1536 维的模型，默认 v4。
- 查询为空：确认已导入文档，并重建来源不匹配的旧向量。

对外接口不返回供应商错误正文或密钥。可选本地模型配置见 [本地向量说明](local-embedding-stage.md)。Ollama 服务配置与兼容接口已测试，但本地 Qwen3 模型尚未下载，未完成真实本地模型推理验证。

## 当前边界

排序仍为余弦相似度与 PostgreSQL 全文得分的基础融合，未实现 RRF、Rerank 或检索质量评测。上传按块保存，尚未实现文件级原子性和去重。本阶段不包括 MCP 与行程规划。
