# MCP 工具与 Agent 阶段设计

目标：把固定的演示天气替换为独立工具服务，并通过 Spring AI Alibaba ReAct Agent 让 DeepSeek 选择工具。用户已明确要求继续上一轮的 Agent 与 MCP 阶段，不需再次批准常规实现选择。

推荐架构为独立 Java tools-server 提供 MCP 工具，backend 通过 MCP SDK 获取工具定义并注册到 ReAct Agent。直连 REST 更简单但无法展示 MCP；stdio 适合单机助手，HTTP 适合本项目独立服务，故采用 HTTP MCP。使用框架提供的标准传输，不自制协议。

工具提供 get_weather(city,date) 和只读的 get_demo_order(orderId)。天气使用无需 Key 的 Open-Meteo，输入为中文城市名与 ISO 日期，最多未来 7 天，时区 Asia/Shanghai。首版使用12个常见旅游城市的固定坐标表，未指定或不支持的城市返回结构化失败，不猜测城市，禁止任意 URL 输入。结果标注预报来源与时间；上游失败不返回模拟天气。订单仅返回明确标注的虚构演示数据；不访问真实订单，也不提供退款提交工具。

backend 的 agent profile 启用 Alibaba Agent，deepseek profile 继续提供 DeepSeek ChatModel。Agent 接收检索资料和历史，模型自行进行工具调用循环；调用次数有限制，工具结果作为数据而非指令。旧 deepseek 模式及无 Key 离线模式保留，不依赖 MCP 服务启动。

退款确认保持后端独立流程：明确退款请求可返回一次性确认令牌；模型不能直接调用退款提交接口。查订单需要用户提供演示订单编号，不让模型猜测。

依赖选择与官方 API 核对后锁定，统一 Spring AI BOM，跑现有全量测试防止升级回归。MCP 工具服务只绑定本机，公开部署前需鉴权。新增单元测试、真实协议工具发现与调用测试、模型两轮工具调用测试；真实天气联调需联网，测试默认模拟上游而非调用外部 API。

验收：用户问目的地天气时能抽取城市、日期，DeepSeek 发起 MCP 工具调用并根据真实天气回答；订单查询明确演示；退款必须确认；原有 RAG、历史和 SSE 正常。文档中文，验证后提交并推送。
