export async function requestJson(path, options = {}, fetcher = fetch) {
  const response = await fetcher(path, options);
  const text = await response.text();
  let body;
  try {
    body = text ? JSON.parse(text) : null;
  } catch {
    /* A proxy may return HTML. */
  }
  if (!response.ok) {
    const error = new Error(
      body?.error || body?.message || "服务暂时不可用，请稍后重试",
    );
    error.status = response.status;
    throw error;
  }
  if (body === undefined) throw new Error("服务返回格式异常，请重试");
  return body;
}

export const postJson = (path, body, options = {}) =>
  requestJson(path, {
    ...options,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });

export async function readChatStream(response, onEvent, signal) {
  if (!response.ok) throw new Error("对话服务暂时不可用，请稍后重试");
  if (!response.body) throw new Error("浏览器不支持流式读取");
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "",
    event = "message",
    data = [],
    result = null;
  const aborted = () => reader.cancel().catch(() => {});
  signal?.addEventListener("abort", aborted, { once: true });
  function dispatch() {
    if (data.length) {
      const raw = data.join("\n");
      let value = raw;
      if (event !== "delta") {
        try {
          value = JSON.parse(raw);
        } catch {
          throw new Error("对话事件格式异常");
        }
      }
      if (event === "error")
        throw new Error(value.message || "生成失败，请重试");
      onEvent({ type: event, data: value });
      if (event === "done") result = value;
    }
    event = "message";
    data = [];
  }
  function consume() {
    let index;
    while ((index = buffer.indexOf("\n")) !== -1) {
      const line = buffer.slice(0, index).replace(/\r$/, "");
      buffer = buffer.slice(index + 1);
      if (line === "") {
        dispatch();
        continue;
      }
      if (line.startsWith(":")) continue;
      const colon = line.indexOf(":");
      const field = colon < 0 ? line : line.slice(0, colon);
      let value = colon < 0 ? "" : line.slice(colon + 1);
      // Spring SseEmitter writes "data:" directly before content, including a
      // single-space token. Preserve delta whitespace; JSON accepts padding.
      if (field !== "data" && value.startsWith(" ")) value = value.slice(1);
      if (field === "event") event = value;
      if (field === "data") data.push(value);
    }
  }
  try {
    signal?.throwIfAborted();
    while (!result) {
      const { value, done } = await reader.read();
      signal?.throwIfAborted();
      if (done) {
        buffer += decoder.decode();
        consume();
        break;
      }
      buffer += decoder.decode(value, { stream: true });
      consume();
    }
    if (!result) throw new Error("连接已中断，回答未完整保存，请重试");
    return result;
  } finally {
    signal?.removeEventListener("abort", aborted);
    await reader.cancel().catch(() => {});
    reader.releaseLock();
  }
}

export async function streamChat(body, onEvent, signal) {
  const response = await fetch("/api/chat/stream", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
    signal,
  });
  return readChatStream(response, onEvent, signal);
}
