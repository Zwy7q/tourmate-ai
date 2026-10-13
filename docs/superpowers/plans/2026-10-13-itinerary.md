# 行程规划实施计划

沿用已确认的设计，本会话直接实施，测试先行。目标为可校验、可保存、可修改的后端行程，不增加前端。

## 1. 状态与工作流

文件：backend/src/main/java/com/tourmate/itinerary/ 下的 TripRequirements、TripState、ItineraryPlan、ItineraryModel、ItineraryStore、InMemoryItineraryStore、AttractionCatalog、ItineraryService。

- [x] 新建 ItineraryServiceTest：完整需求返回带文档编号的两天行程，后续室内修改保留城市和预算；缺字段只追问、不调用规划模型。
- [x] 执行 `mvn -s maven-central-settings.xml -Dtest=ItineraryServiceTest test`，确认新功能缺失导致失败。
- [x] 实现记录类型、需求合并、候选解析、规划调用与Java校验。模型接口为extract与schedule；选择器额外接收原行程和固定日期的景点编号，计划只接受文档编号。
- [x] 增加失败与边界测试：未知编号、闭馆、室内约束、单日6小时上限、重复景点、超预算、无资料、并发冲突与旧状态保留。
- [x] 测试通过后提交工作流代码。

## 2. 模型与天气

文件：DeepSeekItineraryModel、UnavailableItineraryModel、ItineraryWeather、McpItineraryWeather、UnavailableItineraryWeather。

- [x] 本机HTTP模拟OpenAI兼容响应，验证需求JSON提取和编号选择请求，错误JSON不可变成行程。
- [x] DeepSeek使用现有ChatModel；提示词包含当天中国日期与原需求，未明确的补丁字段为null。候选与工具结果标为数据。
- [x] MCP天气经现有客户端调用get_weather，不新增公网地址或其他工具；前4个可预报日期、超时／失败有结构化降级。
- [x] 运行模型与天气单测，确认默认测试无线上费用。

## 3. 持久化与HTTP

文件：RedisItineraryStore、api/ItineraryController，以及测试 ItineraryHttpTest、StorageIntegrationTest。

- [x] 先写内存版本冲突及HTTP缺字段测试，再实现POST plan／GET读取接口；400参数错误、409版本冲突、502生成失败、404未保存、503未启用。
- [x] Redis用Lua比较version再SET EX 86400，失败不覆盖；真实数据库测试只清理测试创建的随机键。
- [x] `TOURMATE_INTEGRATION=true` 执行 backend clean verify，工具模块 clean verify 回归。

## 4. 演示与交付

- [x] 添加中文行程阶段文档和JSON景点演示资料，注明演示费用、无精确交通、没有前端。
- [x] 真实DeepSeek生成杭州两日行程、第二天室内修改、降低预算与重启后读取；天气通过MCP验证。
- [x] 代码审查、diff检查、密钥检查、提交并推送既有远程仓库。

最终62项测试全部通过。审查与联调修正：未修改日程提前固定、室内限制差异计算、候选类别先过滤、偏好字符串数组兼容。实际边界见 [行程阶段说明](../../itinerary-stage.md)。本次按完整功能合并提交，避免交付尚不能启动的中间依赖状态。
