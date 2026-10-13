# 本地向量模型与知识检索

当前运行方案为 DeepSeek 对话与 Ollama 本地 Qwen3 向量检索，无需阿里云模型或第二份 API Key。本地向量推理没有云端接口费用，会消耗电脑的内存、CPU 和磁盘；DeepSeek 对话仍按其平台规则计费。

## 为什么只用 DeepSeek Key 也能运行

DeepSeek 负责根据资料回答问题。本地 Qwen3 Embedding 负责把文档和问题转换为向量，PGVector 找出最相关的文档。向量模型通过 Ollama 在电脑上运行，不需要 DashScope 或其他平台的 Key。

比如文档写“学生购票需出示学生证”，用户问“大学生买票要带什么证件”，系统先把问题向量化、找到相关政策，再让 DeepSeek 结合政策回答。是否正确召回需要用真实模型和评测数据验证，不能仅凭这一例子声称准确率提升。

## 首次启动

在项目根目录执行：

```powershell
cd infra
docker compose --profile local-embedding up -d
docker compose exec ollama ollama pull qwen3-embedding:0.6b
cd ../backend
mvn -s maven-central-settings.xml spring-boot:run '-Dspring-boot.run.profiles=deepseek,postgres,redis,local-embedding'
```

首次需要下载 Ollama 镜像和模型，模型下载完成后保存在 Docker 卷中，下次无需重下。本方案可使用 CPU；首次加载较慢，响应时间受电脑性能影响。Ollama 端口为 localhost:11434，仅绑定本机；后端默认 localhost:8080。项目根目录 `.env` 中只需要有效的 DEEPSEEK_API_KEY。

可选配置见 `.env.example` 中的 TOURMATE_LOCAL_EMBEDDING_URL、TOURMATE_LOCAL_EMBEDDING_MODEL。当前本地模式只支持返回 1024 维向量的模型，不可随意换成其他维数。

## 导入新文档与重建旧数据

新增文档、上传文件仍使用原接口。文本按段落切分，每块最多 800 字符；长段落连续块之间保留 100 字符重叠。上传接口返回保存的块数。

数据库启动时增加来源标记与独立的 1024 维本地向量字段，原有 1536 维向量和文档保留。未知来源的旧向量不会参与当前检索；可以重新导入文档，或分批重建：

```powershell
Invoke-RestMethod 'http://localhost:8080/api/knowledge/reindex?limit=20' -Method Post
```

返回 updated（本次更新数）、skipped（并发修改而跳过的块数）、failedChunkIds（失败块 ID）、remaining（尚需重建的数量）和 message。重复调用直到 remaining 为 0；出现失败时先按 message 排查，不要无限循环。可追加 `&documentId=某个文档UUID` 只处理一个文档。

模型服务失败时保留旧向量；恢复后再次调用即可继续。超过 800 字符的旧块需要重新导入对应文档，以应用分块策略。重建接口用于本地维护，公开部署需要管理员鉴权。

## 处理流程与当前边界

1. 解析文档，切块，调用本地 /api/embed，校验返回 1024 维、有限非零数值。
2. 保存原文块、向量和模型来源；新文档写入由数据库事务保证。
3. 问题使用同一模型生成向量，只检索来源一致的块；结合 PostgreSQL 全文得分做基础排序。
4. 相关原文与会话历史交给 DeepSeek，生成带引用的中文回答。

当前不是 RRF、Rerank 或完整中文关键词检索。模型标签、维数或接口地址改变后，需要重新生成向量；同名模型权重若被替换，也应重新导入文档。文件上传按块保存，如果中途失败可能已有部分块入库，尚未实现文件级原子性与去重。

历史代码中的远端 embedding profile 保留用于兼容，但当前不启用；启动只用 local-embedding。默认未启用任何向量 profile 时仍使用演示字符哈希向量，不具备真实语义理解能力。

## 自动化验证

```powershell
cd backend
mvn -s maven-central-settings.xml clean verify
$env:TOURMATE_INTEGRATION='true'
mvn -s maven-central-settings.xml '-Dtest=StorageIntegrationTest' test
Remove-Item Env:TOURMATE_INTEGRATION
```

离线测试通过本地 HTTP 服务验证远端兼容格式、Ollama 无 Key 调用、维度和错误处理。数据库测试验证长文分块、模型来源隔离、分批重建和失败保留原向量；不会调用收费 API，仅清理测试自己的 UUID 数据。

## 真实联调记录

2026-10-13 已下载 qwen3-embedding:0.6b（约 639 MB），使用 Ollama 0.13.5 完成真实本地推理；返回 1024 维向量，首次加载并生成一个向量约 11 秒，仅为当前电脑的一次观测。

使用 deepseek、postgres、redis、local-embedding 启动后，旧文档重建返回 updated=1、remaining=0。新增“青岚湖景区演示政策”后，换一种问法查询在校生证件要求，成功命中入库文档。DeepSeek 回答优惠门票 18 元、入园需学生证和身份证，并引用该文档、明确标注项目演示政策；Redis 保存了该轮用户问题和模型回答。

同一会话通过 SSE 追问“那入园证件还需要身份证吗？”，收到 meta、53 个 delta 和 done，无 error；回答明确需要身份证，Redis 保存两轮共 4 条消息。

全量验证包含 29 项离线测试与 6 项数据库集成测试，共 35 项通过。上述联调仅证明当前样例和完整链路可用，不代表检索准确率评测。文档为虚构景区的演示资料，不能作为真实出行依据。
