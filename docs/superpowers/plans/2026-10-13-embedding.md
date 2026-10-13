# 真实向量检索实施计划

目标：补齐真实向量接口，保证新旧向量不会混用并支持现有数据重建。

架构：保持 EmbeddingClient、KnowledgeRepository 的现有接口；新增 TextChunker 与 PostgreSQL 专用重建服务。增量 SQL 为旧表增加来源字段。

技术：Java 21、Spring Boot、RestClient、JUnit、PGVector、DashScope text-embedding-v4；可选 Ollama qwen3-embedding:0.6b。

## 任务 1：向量接口与分块

- [x] 在 EmbeddingClientTest 添加本地 HTTP 服务器测试：读取请求模型、dimensions、encoding_format，并返回 1536 维数组；零向量、错误维度、远端 401 必须拒绝，401 内容不可进入异常文本。
- [x] 添加 TextChunkerTest，断言最长块为 800 字符，长文本重叠 100 字符，空输入无块。
- [x] 执行 `mvn -s maven-central-settings.xml '-Dtest=EmbeddingClientTest,TextChunkerTest' test`，确认缺失能力导致失败。
- [x] 修改 EmbeddingClient，复用 HTTP 客户端、验证响应、提供来源标记；新增 TextChunker 并用于入库。
- [x] 再运行相同命令，确认通过。

## 任务 2：数据库隔离与重建

- [x] 在 StorageIntegrationTest 添加来源不匹配时不可召回、重建后可召回、重建保留原文、多块写入测试。
- [x] 开启 TOURMATE_INTEGRATION，运行该测试并确认新测试失败。
- [x] 增加 db/embedding-metadata.sql 与初始化配置；修改 JdbcKnowledgeRepository 写入来源和多个块，并按来源筛选。
- [x] 新增 KnowledgeReindexService 和维护控制器，分批重建并使用条件更新避免覆盖并发改动。
- [x] 再运行真实数据库测试，确认通过并清理测试 UUID 数据。

## 任务 3：联调与交付

- [x] 新增 local-embedding profile 和 Ollama Compose 服务，保留可选远端 v4 配置；补齐 .env.example 和中文配置说明。
- [x] 运行 `mvn -s maven-central-settings.xml clean verify`，开启数据库集成模式，共 35 项测试通过。
- [ ] 验证真实远端向量、知识检索与 DeepSeek 问答。已确认 Key 鉴权有效，但账户返回 Arrearage；待计费状态恢复后继续。
- [ ] 检查暂存区不含本地密钥，提交并推送 origin main；确认远端提交与本地一致。
