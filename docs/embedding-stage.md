# 真实向量检索配置

当前使用 **DeepSeek 对话 + Ollama 本地 Qwen3 向量模型**，不再使用阿里云模型，无需 DASHSCOPE_API_KEY。完整安装、启动、重建说明见 [本地向量检索说明](local-embedding-stage.md)。

从 backend 目录启动：

```powershell
mvn -s maven-central-settings.xml spring-boot:run '-Dspring-boot.run.profiles=deepseek,postgres,redis,local-embedding'
```

启动前需要 Docker 中的 PostgreSQL、Redis、Ollama 已就绪，并完成 qwen3-embedding:0.6b 下载。本地向量模型输出 1024 维，数据库使用独立字段，不删除旧的 1536 维向量和原文。切换后需重建旧向量或重新导入文档。

接口与数据库测试覆盖长文分块、向量来源隔离、分批重建、失败保留原文和并发修改跳过。远端兼容适配代码保留，但不是当前运行方案；无需恢复阿里云计费状态。
