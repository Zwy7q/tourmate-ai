import { test } from "node:test";
import assert from "node:assert/strict";
import { readChatStream, requestJson } from "../src/api.js";

function response(text, size = 3) {
  const bytes = new TextEncoder().encode(text);
  return new Response(
    new ReadableStream({
      start(controller) {
        for (let i = 0; i < bytes.length; i += size)
          controller.enqueue(bytes.slice(i, i + size));
        controller.close();
      },
    }),
  );
}
test("SSE preserves split UTF8, spaces, empty deltas and multiline data with CRLF", async () => {
  const events = [];
  const result = await readChatStream(
    response(
      'event:meta\r\ndata:{"intent":"WEATHER"}\r\n\r\nevent:delta\r\ndata:杭州\r\n\r\nevent:delta\r\ndata: \r\n\r\nevent:delta\r\ndata:\r\n\r\nevent:delta\r\ndata:第一行\r\ndata:第二行\r\n\r\nevent:done\r\ndata:{"answer":"杭州天气","citations":[]}\r\n\r\n',
    ),
    (e) => events.push(e),
  );
  assert.equal(result.answer, "杭州天气");
  assert.deepEqual(
    events.filter((e) => e.type === "delta").map((e) => e.data),
    ["杭州", " ", "", "第一行\n第二行"],
  );
});
test("SSE rejects server errors and incomplete streams", async () => {
  await assert.rejects(
    readChatStream(
      response('event:error\ndata:{"message":"生成失败"}\n\n'),
      () => {},
    ),
    /生成失败/,
  );
  await assert.rejects(
    readChatStream(response("event:delta\ndata:半截答案\n\n"), () => {}),
    /中断/,
  );
});
test("HTTP errors use safe Chinese fallback for HTML upstream errors", async () => {
  await assert.rejects(
    requestJson(
      "/api/test",
      {},
      async () => new Response("<h1>proxy error</h1>", { status: 502 }),
    ),
    /服务暂时不可用/,
  );
  await assert.rejects(
    requestJson("/api/test", {}, async () =>
      Response.json({ error: "行程已更新" }, { status: 409 }),
    ),
    (error) => error.status === 409 && error.message === "行程已更新",
  );
});
test("SSE honors abort before reading and cancels reader", async () => {
  const abort = new AbortController();
  abort.abort();
  await assert.rejects(
    readChatStream(response(""), () => {}, abort.signal),
    { name: "AbortError" },
  );
});
