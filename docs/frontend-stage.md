# Vue 中文工作台使用说明

## 能做什么

工作台提供智能客服、行程助手和知识库三个页面，支持电脑与手机布局。

- 智能客服：发送问题、流式查看回答、停止生成、展开参考资料；演示退票需要点击按钮并二次确认。
- 行程助手：用自然语言描述城市、日期、天数、单人预算和偏好，查看每天的景点、天气、来源、已知门票合计与雨天备选；可继续修改需求。
- 知识库：上传 PDF、Word、Markdown 或纯文本，选择资料分类，检索并查看原文。单文件上限为1MB，与当前后端默认限制一致。

前端使用 Vue 3、Vite、原生 fetch 与 Lucide 图标，未引入大型组件库。模型调用、知识检索与 MCP 编排均在 Java 后端完成。

## 启动

要求 Node.js 22.12+（推荐22或24）。先按照 [Agent 与 MCP 启动说明](mcp-agent-stage.md) 启动后端；完整模式使用 DeepSeek 对话、Ollama 本地向量、PostgreSQL 与 Redis，不使用阿里云模型。

注意：Ollama 是可选 Compose profile，启动完整基础设施应执行：

```powershell
cd infra
docker compose --profile local-embedding up -d
docker compose exec ollama ollama pull qwen3-embedding:0.6b
```

另开终端，在项目根目录执行：

```powershell
cd frontend
npm ci
npm run dev
```

打开 http://127.0.0.1:5173 。Vite 将 `/api` 代理到 `http://127.0.0.1:8080`，前端不需要 API Key。根目录 `.env` 只供后端读取；不要把密钥放入 `VITE_` 变量或浏览器存储。

后端使用其他端口时，在启动 Vite 的终端设置：

```powershell
$env:TOURMATE_BACKEND_URL='http://127.0.0.1:18084'
npm run dev
```

无模型 Key 时，也可以启动后端默认配置，演示基础知识问答、上传和检索；真实天气与行程规划需要完整模式。行程页面需要结构化景点资料，先运行 [行程阶段说明](itinerary-stage.md) 中的导入脚本。普通上传文档用于客服问答，不会自动成为可计算的景点。

## 会话与异常

浏览器只保存随机会话编号与当前页签；对话正文和行程由后端保存。点击“新会话”更换编号，旧数据按后端保留策略到期，不会立即删除。清除浏览器存储后无法自动找回原编号。

刷新可恢复最近消息与未过期的行程。现有历史接口只提供角色、正文和时间，因此刷新后的消息不恢复引用和退票按钮，需要重新提问获取。演示退票不操作真实订单或资金。

生成期间锁定页签切换和新会话；对话可停止。连接中断或未收到 `done` 时标记未完整保存。行程生成失败保留旧计划，版本冲突时点击“读取最新行程”再修改。当前费用只核验已知单人门票，其他支出以页面说明为准。

## 验证

```powershell
cd frontend
npm test
npm run build
npx playwright install chromium
npm run test:e2e
```

浏览器下载不便且已安装 Edge 时，可以使用：

```powershell
$env:PLAYWRIGHT_CHANNEL='msedge'
npm run test:e2e
```

2026-10-14 本机验证：4项 Node 测试、4项浏览器交互测试通过，生产构建成功。覆盖 UTF-8 与 SSE 跨块解析、空格保留、服务失败、流中断、取消、引用、退票确认、行程失败保留与刷新、文件上传、文本安全展示及手机宽度。

真实浏览器联调另行调用运行中的后端，已验证上传一份演示资料、本地向量检索、DeepSeek 带引用问答、Redis 历史恢复、读取已有两天行程及手机布局；页面脚本错误为0。浏览器自动化测试使用模拟接口，不调用付费模型；真实联调单独运行，会写入演示资料并产生 DeepSeek 调用费用：

```powershell
$env:TOURMATE_LIVE='true'
# 可选：已有且未过期的行程编号，用于验证恢复；不填则跳过行程恢复
$env:TOURMATE_LIVE_TRIP_ID='你的行程会话编号'
node scripts/live-smoke.mjs
```

截图写入 `frontend/test-results/`，不提交到仓库。GitHub Actions 已配置独立前端测试任务；远程运行结果以 Actions 实际记录为准。

## 构建与当前边界

`npm run build` 生成 `frontend/dist`；`npm run preview` 在本机4173端口预览，并使用相同的开发代理。正式部署需为 `/api` 配置反向代理，构建产物不会内置 Java 后端地址或密钥。

当前工作台用于本地演示，没有登录与租户隔离；知识库是共享库。开发服务仅绑定本机，不应直接作为公网服务。Rerank、统一评测、限流和调用链观测尚待后续阶段实现。
