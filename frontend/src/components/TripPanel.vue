<script setup>
import { ref, onMounted, onBeforeUnmount } from "vue";
import {
  ArrowUpRight,
  MapPin,
  CloudSun,
  CalendarDays,
  Wallet,
  RefreshCw,
  ArrowRight,
  BookOpen,
} from "lucide-vue-next";
import { requestJson, postJson } from "../api.js";
const props = defineProps({ conversationId: { type: String, required: true } });
const emit = defineEmits(["busy"]);
const input = ref(""),
  state = ref(null),
  busy = ref(false),
  loading = ref(true),
  error = ref("");
const controller = new AbortController();
const statusLabels = {
  READY: "行程已就绪",
  NEEDS_INFORMATION: "再补充一点信息",
  NEEDS_KNOWLEDGE: "需要更多景点资料",
  TICKET_BUDGET_EXCEEDED: "门票费用超出预算",
};
async function restore() {
  loading.value = true;
  error.value = "";
  try {
    state.value = await requestJson(
      `/api/itineraries/${encodeURIComponent(props.conversationId)}`,
      { signal: controller.signal },
    );
  } catch (e) {
    if (e.status !== 404 && e.name !== "AbortError")
      error.value = "读取行程失败，请刷新重试";
  } finally {
    loading.value = false;
  }
}
async function plan() {
  if (!input.value.trim() || busy.value || loading.value) return;
  busy.value = true;
  emit("busy", true);
  error.value = "";
  try {
    state.value = await postJson(
      "/api/itineraries/plan",
      { conversationId: props.conversationId, message: input.value.trim() },
      { signal: controller.signal },
    );
    input.value = "";
  } catch (e) {
    if (e.name !== "AbortError")
      error.value =
        e.message +
        (e.status === 409
          ? "。请点击“读取最新行程”后重试。"
          : "。已保存的行程仍保留。");
  } finally {
    busy.value = false;
    emit("busy", false);
  }
}
onMounted(restore);
onBeforeUnmount(() => {
  controller.abort();
  emit("busy", false);
});
</script>

<template>
  <section class="content-page">
    <div class="section-heading">
      <span class="eyebrow">MAKE ROOM FOR THE JOURNEY</span>
      <h1>把期待，<span>写进行程。</span></h1>
      <p>告诉我你的目的地与偏好，一起安排不慌不忙的旅程。</p>
    </div>
    <div class="trip-grid">
      <div class="trip-editor card">
        <span class="card-kicker"><MapPin :size="16" />出游需求</span>
        <form @submit.prevent="plan">
          <label for="trip-input">描述出游需求</label
          ><textarea
            id="trip-input"
            v-model="input"
            rows="6"
            maxlength="2000"
            :disabled="busy || loading"
            placeholder="例如：明天去杭州玩两天，单人预算1000元，喜欢自然风景，慢节奏。"
          ></textarea>
          <p class="field-hint">
            请提供城市、出发日期、天数和单人预算。修改时可说“第二天全部改为室内活动”。
          </p>
          <button
            class="button primary full"
            :disabled="!input.trim() || busy || loading"
          >
            {{
              busy
                ? "正在整理行程与天气…"
                : state?.plan
                  ? "更新行程"
                  : "生成行程"
            }}<ArrowUpRight :size="16" />
          </button>
        </form>
        <button
          class="text-button"
          :disabled="busy || loading"
          @click="restore"
        >
          <RefreshCw :size="13" />读取最新行程
        </button>
        <div class="subtle-note">
          <BookOpen :size="16" />
          <p>
            行程依据已入库的结构化景点资料。演示景点需先导入；普通介绍文档无法直接用于行程计算。
          </p>
        </div>
      </div>
      <div class="trip-results">
        <div v-if="error" class="error" role="alert">{{ error }}</div>
        <p v-if="loading" class="muted" role="status">正在读取行程…</p>
        <div v-if="state" class="trip-state">
          <span class="status-pill">{{
            statusLabels[state.status] || state.status
          }}</span
          ><span class="muted">版本 {{ state.version }}</span>
        </div>
        <div v-if="state?.clarification" class="notice">
          {{ state.clarification }}
        </div>
        <div v-if="state?.requirements" class="requirement-chips">
          <span v-if="state.requirements.city"
            ><MapPin :size="13" />{{ state.requirements.city }}</span
          ><span v-if="state.requirements.startDate"
            ><CalendarDays :size="13" />{{
              state.requirements.startDate
            }}出发</span
          ><span v-if="state.requirements.days"
            >{{ state.requirements.days }}天</span
          ><span v-if="state.requirements.budget"
            ><Wallet :size="13" />单人预算 ¥{{
              state.requirements.budget
            }}</span
          ><span v-if="state.requirements.preferences">{{
            state.requirements.preferences
          }}</span>
        </div>
        <template v-if="state?.plan"
          ><div class="budget-card">
            <span>已知单人门票合计</span
            ><strong><small>¥</small>{{ state.plan.ticketSubtotal }}</strong>
            <p>{{ state.plan.budgetNote }}</p>
          </div>
          <article
            v-for="(day, i) in state.plan.days"
            :key="day.date"
            class="day-card card"
          >
            <header>
              <div>
                <span class="day-number"
                  >DAY {{ String(i + 1).padStart(2, "0") }}</span
                >
                <h2>{{ day.date }}</h2>
              </div>
              <div class="weather">
                <CloudSun :size="20" /><span v-if="day.weather?.available"
                  >{{ day.weather.condition
                  }}<small
                    >{{ day.weather.low }}° ～ {{ day.weather.high }}° ·
                    {{ day.weather.source }}</small
                  ></span
                ><span v-else
                  >未获取天气<small>{{ day.weather?.message }}</small></span
                >
              </div>
            </header>
            <div class="stops">
              <div
                v-for="(stop, index) in day.stops"
                :key="stop.documentId"
                class="stop"
              >
                <span class="stop-dot">{{ index + 1 }}</span>
                <div>
                  <span class="muted"
                    >{{ stop.period }} · 游览约{{ stop.visitHours }}小时</span
                  >
                  <h3>
                    {{ stop.name
                    }}<span v-if="stop.demo" class="demo-badge">演示</span>
                  </h3>
                  <p>门票 ¥{{ stop.ticketPrice }}／人</p>
                  <details>
                    <summary>查看资料来源</summary>
                    <p>{{ stop.source }}</p>
                    <small>资料编号：{{ stop.documentId }}</small>
                  </details>
                </div>
              </div>
            </div>
            <details v-if="day.rainAlternatives?.length" class="rain-options">
              <summary>雨天备选 · 未计入费用</summary>
              <p v-for="stop in day.rainAlternatives" :key="stop.documentId">
                {{ stop.name }} · ¥{{ stop.ticketPrice }}
                <span v-if="stop.demo">（演示）</span>
              </p>
            </details>
          </article>
          <ul class="warnings">
            <li v-for="warning in state.warnings" :key="warning">
              {{ warning }}
            </li>
          </ul></template
        >
        <div v-else-if="!loading" class="trip-empty">
          <div class="empty-map">
            <MapPin :size="36" :stroke-width="1.2" /><span></span
            ><ArrowRight :size="22" :stroke-width="1.2" />
          </div>
          <h2>好旅程，从一个想法开始</h2>
          <p>填写左侧需求，查看每天的景点安排、<br />天气预报与室内备选。</p>
          <div class="empty-steps">
            <span>01 明确期待</span><span>02 规划日程</span
            ><span>03 随心调整</span>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>
