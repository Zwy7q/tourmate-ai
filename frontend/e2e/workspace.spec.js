import { test, expect } from "@playwright/test";

const day = (name, date) => ({
  date,
  stops: [
    {
      documentId: "doc1",
      name,
      period: "上午",
      visitHours: 2,
      ticketPrice: 20,
      source: "演示资料",
      demo: true,
    },
  ],
  weather: {
    available: true,
    condition: "阴天",
    low: 18,
    high: 25,
    source: "Open-Meteo",
  },
  rainAlternatives: [],
});
const trip = {
  version: 1,
  status: "READY",
  requirements: { city: "杭州", days: 2, budget: 1000 },
  warnings: ["交通、餐饮、住宿未计"],
  plan: {
    ticketSubtotal: 40,
    budgetNote: "仅核验单人门票，其他费用未计",
    days: [day("演示公园", "2026-10-17"), day("演示展馆", "2026-10-18")],
  },
};

test.beforeEach(async ({ page }) => {
  await page.route("**/api/health", (route) =>
    route.fulfill({ json: { status: "UP" } }),
  );
  await page.route("**/api/conversations/*/messages", (route) =>
    route.fulfill({ json: [] }),
  );
  await page.route("**/api/itineraries/*", (route) =>
    route.fulfill({ status: 404, json: {} }),
  );
});

test("chat streams text, displays references, and confirms only explicit demo refunds", async ({
  page,
}) => {
  let confirmCalls = 0;
  await page.route("**/api/chat/stream", (route) =>
    route.fulfill({
      contentType: "text/event-stream",
      body: 'event:meta\ndata:{"intent":"REFUND_POLICY"}\n\nevent:delta\ndata:需要您确认演示退款。\n\nevent:done\ndata:{"answer":"需要您确认演示退款。","citations":[{"document":{"id":"d1","title":"演示退改规则","content":"未使用门票可申请退票"}}],"confirmationRequired":true,"confirmationToken":"demo-token"}\n\n',
    }),
  );
  await page.route("**/api/refunds/confirm", (route) => {
    confirmCalls++;
    expect(route.request().postDataJSON().token).toBe("demo-token");
    return route.fulfill({ json: { success: true } });
  });
  await page.goto("/");
  await page.getByLabel("向客服提问").fill("我要退票");
  await page.getByRole("button", { name: "发送消息", exact: true }).click();
  await expect(
    page.getByText("需要您确认演示退款。", { exact: true }),
  ).toBeVisible();
  await page.locator(".citations > summary").click();
  await page.locator(".citations details summary").click();
  await expect(
    page.getByText("未使用门票可申请退票", { exact: true }),
  ).toBeVisible();
  expect(confirmCalls).toBe(0);
  page.once("dialog", (dialog) => dialog.accept());
  await page.getByRole("button", { name: "确认演示退票" }).click();
  await expect(page.getByText("演示申请已提交，未执行真实退款")).toBeVisible();
  await expect(page.getByRole("button", { name: "确认演示退票" })).toHaveCount(
    0,
  );
  expect(confirmCalls).toBe(1);
});

test("itinerary creates, preserves on failure and restores after reload", async ({
  page,
}) => {
  let saved = false,
    calls = 0;
  await page.route("**/api/itineraries/*", (route) => {
    if (route.request().method() === "POST") {
      if (++calls > 1)
        return route.fulfill({
          status: 502,
          json: { error: "行程生成暂时不可用" },
        });
      saved = true;
      return route.fulfill({ json: trip });
    }
    return route.fulfill({
      status: saved ? 200 : 404,
      json: saved ? trip : {},
    });
  });
  await page.goto("/");
  await page.getByRole("button", { name: "行程助手", exact: true }).click();
  await page.getByLabel("描述出游需求").fill("周末去杭州两天，预算1000");
  await page.getByRole("button", { name: "生成行程", exact: true }).click();
  await expect(page.getByRole("heading", { name: "演示公园" })).toBeVisible();
  await expect(
    page.getByText("交通、餐饮、住宿未计", { exact: true }),
  ).toBeVisible();
  await page.getByLabel("描述出游需求").fill("第二天改室内");
  await page.getByRole("button", { name: "更新行程", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("行程生成暂时不可用");
  await expect(page.getByRole("heading", { name: "演示公园" })).toBeVisible();
  await page.reload();
  await expect(page.getByRole("heading", { name: "演示展馆" })).toBeVisible();
});

test("knowledge uploads and searches while displaying unsafe text without executing it", async ({
  page,
}) => {
  await page.route("**/api/knowledge/upload*", (route) =>
    route.fulfill({ json: { filename: "policy.md", chunks: 1 } }),
  );
  await page.route("**/api/knowledge/search*", (route) =>
    route.fulfill({
      json: [
        {
          document: {
            title: "景区说明",
            content: "<img src=x onerror=alert(1)>门票18元",
            category: "ticket-price",
          },
          score: 0.9,
        },
      ],
    }),
  );
  await page.goto("/");
  await page.getByRole("button", { name: "知识库", exact: true }).click();
  await page
    .getByLabel("选择知识文件")
    .setInputFiles({
      name: "policy.md",
      mimeType: "text/markdown",
      buffer: Buffer.from("演示门票18元"),
    });
  await page.getByRole("button", { name: "上传并入库", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("上传成功");
  await page.getByLabel("检索知识库").fill("门票");
  await page.getByRole("button", { name: "搜索资料", exact: true }).click();
  await expect(
    page.getByText("<img src=x onerror=alert(1)>门票18元", { exact: true }),
  ).toBeVisible();
  await expect(page.locator(".knowledge-result img")).toHaveCount(0);
});

test("mobile layout stays inside viewport and new session rotates the id", async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/");
  await expect(page.getByLabel("向客服提问")).toBeVisible();
  const first = await page.evaluate(() =>
    localStorage.getItem("tourmate.conversation"),
  );
  await page.getByRole("button", { name: "新会话", exact: true }).click();
  const second = await page.evaluate(() =>
    localStorage.getItem("tourmate.conversation"),
  );
  expect(first).not.toBe(second);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
});
