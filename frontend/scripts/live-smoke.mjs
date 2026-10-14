// 手工联调：需先启动完整后端、MCP、Ollama 与 Vite；会上传演示资料并调用 DeepSeek。
import { chromium } from '@playwright/test';
import assert from 'node:assert/strict';
import { mkdir } from 'node:fs/promises';

if (process.env.TOURMATE_LIVE !== 'true') throw new Error('真实联调需设置 TOURMATE_LIVE=true');
await mkdir('test-results', { recursive: true });
const browser = await chromium.launch({ channel: process.env.PLAYWRIGHT_CHANNEL || undefined });
try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
  page.setDefaultTimeout(120_000);
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.goto('http://127.0.0.1:5173');
  await page.getByText('后端服务已连接', { exact: true }).waitFor();
  await page.screenshot({ path: 'test-results/desktop-home.png', fullPage: true });
  await page.getByRole('button', { name: '知识库', exact: true }).click();
  await page.getByLabel('选择知识文件').setInputFiles({ name: '前端联调演示.md', mimeType: 'text/markdown', buffer: Buffer.from('# 前端联调演示景区学生票\n本资料仅供开发联调，不是真实景区政策。前端联调演示景区的学生票为23元，入园须出示学生证。') });
  await page.getByRole('button', { name: '上传并入库', exact: true }).click();
  await page.getByRole('status').filter({ hasText: '已导入 1 个文本块' }).waitFor();
  await page.getByLabel('检索知识库').fill('前端联调演示景区学生票');
  await page.getByRole('button', { name: '搜索资料', exact: true }).click();
  await page.locator('.knowledge-result').filter({ hasText: '23元' }).first().waitFor();
  await page.getByRole('button', { name: '智能客服', exact: true }).click();
  await page.getByLabel('向客服提问').fill('前端联调演示景区的学生票多少钱，需要什么证件？请注明这是演示资料。');
  await page.getByRole('button', { name: '发送消息', exact: true }).click();
  await page.getByRole('button', { name: '停止生成', exact: true }).waitFor({ state: 'hidden' });
  const answer = page.locator('.message.assistant .answer-text').last();
  assert.match(await answer.innerText(), /23/);
  assert.equal(await page.getByRole('alert').count(), 0);
  await page.locator('.citations > summary').waitFor();
  await page.reload();
  await page.locator('.message.assistant .answer-text').last().waitFor();
  assert.match(await page.locator('.message.assistant .answer-text').last().innerText(), /23/);
  await page.screenshot({ path: 'test-results/desktop-chat.png', fullPage: true });
  // 已有行程仍在 Redis 24 小时有效期内时，验证前端真实恢复。
  const tripId = process.env.TOURMATE_LIVE_TRIP_ID;
  if (tripId) {
    await page.evaluate(id => { localStorage.setItem('tourmate.conversation', id); localStorage.setItem('tourmate.tab', 'trip'); }, tripId);
    await page.reload();
    await page.locator('.day-card').first().waitFor();
    await page.screenshot({ path: 'test-results/desktop-trip.png', fullPage: true });
  }
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole('button', { name: '智能客服', exact: true }).click();
  await page.getByRole('button', { name: '新会话', exact: true }).click();
  await page.getByRole('heading', { name: '下一站，一起出发。' }).waitFor();
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
  await page.screenshot({ path: 'test-results/mobile-home.png', fullPage: true });
  assert.deepEqual(errors, []);
  console.log(JSON.stringify({ upload: '通过', vectorSearch: '通过', deepseekChat: '通过', historyRestore: '通过', tripRestore: tripId ? '通过' : '未运行', mobile: '通过', pageErrors: errors.length }));
} finally { await browser.close(); }
