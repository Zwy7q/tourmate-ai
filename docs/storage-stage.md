# PostgreSQL、PGVector 和 Redis 存储阶段

## 启动

在 infra 目录执行 `docker compose up -d`，然后在 backend 目录执行：

```powershell
mvn -s maven-central-settings.xml spring-boot:run '-Dspring-boot.run.profiles=postgres,redis'
```

PostgreSQL 地址为 localhost:5433，Redis 地址为 localhost:6380。默认演示模式使用字符哈希向量进行联调，不具备真实模型的语义理解能力。

如果已配置 DASHSCOPE_API_KEY，可将启动 profiles 改为 `postgres,redis,embedding`。真实接口必须返回 1536 维向量。切换模型或模式后需重新导入文档；不要混用不同模型的向量。

## 已实现

- postgres profile 自动关闭内存知识库，使用事务写入 UUID 文档及其向量。
- 1536 维向量存入 PGVector，使用余弦相似度与全文得分相加排序。
- redis profile 自动关闭内存会话，原始时间戳随 JSON 一起保存。
- Redis Lua 脚本原子执行追加、最近 20 条裁剪及 24 小时过期设置。
- 默认离线测试保持内存适配器，真实集成测试显式开启。

## 验证方式

```powershell
mvn -s maven-central-settings.xml clean verify
$env:TOURMATE_INTEGRATION='true'
mvn -s maven-central-settings.xml '-Dtest=StorageIntegrationTest' test
Remove-Item Env:TOURMATE_INTEGRATION
```

已在 Docker PostgreSQL 16 / PGVector 和 Redis 7.2 上执行真实测试，验证中文文档保存与检索、向量维数、会话窗口、时间戳和过期时间。测试仅清理自己创建的 UUID 文档与会话。

## 当前边界与后续任务

- 真实 Embedding 请求需要 API Key，尚未进行线上调用。
- 得分融合为基础版本，尚未实现中文分词、RRF 与 Rerank。
- 对话历史已保存；启用 deepseek profile 后，历史用于模型上下文与基础追问补全。
- 默认仍为规则式回答；已通过 Spring AI 兼容接口接入 DeepSeek，详见 [DeepSeek 接入说明](deepseek-stage.md)。Spring AI Alibaba Agent 编排待后续实现。
- PostgreSQL 模式启动后知识库为空，先通过文档上传或新增接口导入数据。
- 退款与改签的订单规则、授权与幂等机制属于后续阶段。

## Git 管理

远程地址为 git@github.com:Zwy7q/tourmate-ai.git。每次修改先验证，再按功能提交并推送；构建产物、密钥和本地配置不纳入版本管理。
