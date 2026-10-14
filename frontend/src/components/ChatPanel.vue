<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from "vue";
import {
  ArrowUp,
  Square,
  Sparkles,
  CloudSun,
  Ticket,
  Landmark,
  ArrowUpRight,
  BookOpen,
  Check,
} from "lucide-vue-next";
import { requestJson, postJson, streamChat } from "../api.js";
const props = defineProps({ conversationId: { type: String, required: true } });
const emit = defineEmits(["busy"]);
const input = ref(""),
  messages = ref([]),
  busy = ref(false),
  loading = ref(true),
  error = ref(""),
  list = ref(null);
const restoring = new AbortController();
let generation;
const suggestions = [
  { icon: Ticket, title: "门票与退改", text: "故宫门票的退改规则是什么？" },
  {
    icon: CloudSun,
    title: "出行天气",
    text: "杭州明天天气怎么样？请注明来源。",
  },
  { icon: Landmark, title: "景区信息", text: "故宫几点开放，需要提前预约吗？" },
];
const scroll = () =>
  nextTick(() => {
    list.value?.scrollTo({ top: list.value.scrollHeight, behavior: "smooth" });
  });
onMounted(async () => {
  try {
    messages.value = (
      await requestJson(
        `/api/conversations/${encodeURIComponent(props.conversationId)}/messages`,
        { signal: restoring.signal },
      )
    ).map((m) => ({ ...m, state: "saved" }));
    scroll();
  } catch (e) {
    if (e.name !== "AbortError")
      error.value = "历史消息读取失败，可新建会话或稍后刷新。";
  } finally {
    loading.value = false;
  }
});
async function send(text = input.value) {
  text = text.trim();
  if (!text || busy.value || loading.value) return;
  input.value = "";
  error.value = "";
  busy.value = true;
  emit("busy", true);
  messages.value.push({ role: "user", content: text });
  messages.value.push({ role: "assistant", content: "", state: "generating" });
  const answer = messages.value[messages.value.length - 1];
  generation = new AbortController();
  try {
    const done = await streamChat(
      { message: text, conversationId: props.conversationId },
      (event) => {
        if (event.type === "delta") answer.content += event.data;
        if (event.type === "meta") answer.intent = event.data.intent;
        scroll();
      },
      generation.signal,
    );
    answer.content = done.answer;
    answer.citations = done.citations;
    answer.token = done.confirmationRequired ? done.confirmationToken : null;
    answer.state = "saved";
  } catch (e) {
    answer.state = "incomplete";
    error.value =
      e.name === "AbortError"
        ? "已停止生成；本轮未完整保存，可重新发送。"
        : e.message;
  } finally {
    busy.value = false;
    emit("busy", false);
    scroll();
  }
}
async function confirmRefund(message) {
  if (
    !message.token ||
    message.confirming ||
    !window.confirm("仅提交演示退票申请，不会执行真实退款。是否确认？")
  )
    return;
  message.confirming = true;
  error.value = "";
  try {
    const result = await postJson("/api/refunds/confirm", {
      token: message.token,
    });
    message.token = null;
    message.confirmed = result.success;
    message.confirmationNote = result.success
      ? "演示申请已提交，未执行真实退款"
      : "确认已过期或已使用，请重新发起请求";
  } catch (e) {
    error.value = e.message;
  } finally {
    message.confirming = false;
  }
}
onBeforeUnmount(() => {
  restoring.abort();
  generation?.abort();
  emit("busy", false);
});
</script>

<template>
  <section class="chat-page">
    <div ref="list" class="chat-scroll">
      <div v-if="messages.length === 0" class="welcome">
        <span class="eyebrow"
          ><span class="tiny-line"></span> YOUR TRAVEL COMPANION</span
        >
        <h1>下一站，<span>一起出发。</span></h1>
        <p class="intro">
          从一张门票到一次出游，<br />让每个问题有答案，让每段旅程有准备。
        </p>
        <div class="landscape" aria-hidden="true">
          <div class="landscape-sun"></div>
          <div class="mountain far"></div>
          <div class="mountain near"></div>
          <div class="lake"></div>
          <span class="landscape-label"
            >山水之间，自有答案 <ArrowUpRight :size="15" /></span
          ><svg class="landscape-route" viewBox="0 0 600 120">
            <path d="M-20 110 Q100 -30 210 65 T430 50 T650 20" />
          </svg>
        </div>
        <div class="suggestions">
          <button
            v-for="item in suggestions"
            :key="item.title"
            :disabled="loading || busy"
            @click="send(item.text)"
          >
            <component :is="item.icon" :size="20" /><strong>{{
              item.title
            }}</strong
            ><span>{{ item.text }}</span
            ><ArrowUpRight :size="15" />
          </button>
        </div>
      </div>
      <div v-else class="messages">
        <div class="section-heading">
          <span class="eyebrow">一路，慢慢聊</span>
          <h1>你的旅行对话</h1>
        </div>
        <article
          v-for="(message, index) in messages"
          :key="index"
          :class="['message', message.role]"
        >
          <div class="avatar">
            <Sparkles v-if="message.role === 'assistant'" :size="17" /><span
              v-else
              >我</span
            >
          </div>
          <div class="message-body">
            <div class="message-author">
              {{ message.role === "assistant" ? "TourMate" : "你"
              }}<small v-if="message.state === 'incomplete'">未完整保存</small>
            </div>
            <p class="answer-text">
              {{
                message.content ||
                (message.state === "generating"
                  ? "正在查询资料与工具，请稍候…"
                  : "")
              }}
            </p>
            <details v-if="message.citations?.length" class="citations">
              <summary>
                <BookOpen :size="14" /> 参考资料 ·
                {{ message.citations.length }}
              </summary>
              <details v-for="(citation, i) in message.citations" :key="i">
                <summary>[{{ i + 1 }}] {{ citation.document.title }}</summary>
                <p>{{ citation.document.content }}</p>
              </details>
              <small>候选检索资料，具体引用以回答中的编号为准。</small>
            </details>
            <div
              v-if="message.token || message.confirmationNote"
              class="refund-box"
            >
              <span>演示退票 · 需要确认</span>
              <p>仅演示申请流程，不处理真实订单与资金。</p>
              <button
                v-if="message.token"
                class="button secondary"
                :disabled="message.confirming"
                @click="confirmRefund(message)"
              >
                {{ message.confirming ? "正在确认…" : "确认演示退票" }}
              </button>
              <p v-if="message.confirmationNote" class="confirmation-note">
                <Check v-if="message.confirmed" :size="15" />{{
                  message.confirmationNote
                }}
              </p>
            </div>
          </div>
        </article>
      </div>
    </div>
    <div class="composer-wrap">
      <div v-if="error" class="error" role="alert">{{ error }}</div>
      <form class="composer" @submit.prevent="send()">
        <textarea
          v-model="input"
          aria-label="向客服提问"
          placeholder="想去哪里？或者，问我一个旅行问题…"
          rows="2"
          maxlength="4000"
          :disabled="loading"
          @keydown.enter.exact.prevent="!$event.isComposing && send()"
        ></textarea>
        <div class="composer-bottom">
          <span
            ><Sparkles :size="13" />{{
              loading
                ? "正在恢复会话…"
                : busy
                  ? "正在为你整理答案…"
                  : "票务政策 · 景区咨询 · 天气查询"
            }}</span
          ><button
            v-if="busy"
            class="send-button stop"
            type="button"
            aria-label="停止生成"
            @click="generation?.abort()"
          >
            <Square :size="16" /></button
          ><button
            v-else
            class="send-button"
            type="submit"
            aria-label="发送消息"
            :disabled="!input.trim() || loading"
          >
            <ArrowUp :size="20" />
          </button>
        </div>
      </form>
      <p class="composer-note">
        AI 回答请结合资料核实；景区信息以官方公告为准。
      </p>
    </div>
  </section>
</template>
