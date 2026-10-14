# 百炼低价向量模型切换

用户确认仅向量改用百炼，DeepSeek 对话保持不变。

模型选择：官方北京地域价目页列出的 qwen3.7-text-embedding-flash 为0.125元/百万输入Token，比 text-embedding-v4 的0.5元低。真实调用已验证模型返回1024维，即使请求1536维也返回1024维。

设计：仅为该远端型号识别1024维，发送匹配维度，复用现有 embedding_local vector(1024) 字段；本地模式及其他远端旧型号维度不变。来源标记包含模式、模型、维度和端点散列，因此旧本地模型向量不会用于新模型检索。切换后分批重建资料索引，不删除原文。

- [x] 增加真实HTTP模拟测试，验证远端1024维、鉴权、请求参数、存储字段与来源隔离，先确认旧实现失败。
- [x] 最小修改 EmbeddingClient 与 embedding profile 默认型号，补充无密钥示例配置及中文切换说明。
- [x] 执行后端全量含数据库验证并打包，重启后端为 deepseek,postgres,redis,embedding,agent。
- [x] 分批重建并验证真实检索、DeepSeek带引用回答；独立审查。

验证：后端60项通过，无失败、错误或跳过；9块重建完成，remaining=0，真实搜索与DeepSeek基于演示政策回答通过。发布方式沿用用户授权：分支提交、main快进合并、推送origin并核验远程提交。

官方价格来源：https://help.aliyun.com/zh/model-studio/model-pricing （2026-10-14查询，地域和价格以官方实际计费为准）。
