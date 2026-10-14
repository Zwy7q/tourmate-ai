<script setup>
import { ref, onMounted, onBeforeUnmount } from "vue";
import {
  Compass,
  MessageSquare,
  Map,
  BookOpen,
  Plus,
  ArrowUpRight,
  Leaf,
  Circle,
} from "lucide-vue-next";
import ChatPanel from "./components/ChatPanel.vue";
import TripPanel from "./components/TripPanel.vue";
import KnowledgePanel from "./components/KnowledgePanel.vue";
import { requestJson } from "./api.js";

function stored(key) {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}
function persist(key, value) {
  try {
    localStorage.setItem(key, value);
  } catch {
    /* Private mode may disable storage. */
  }
}
const conversationId = ref(
  stored("tourmate.conversation") || crypto.randomUUID(),
);
persist("tourmate.conversation", conversationId.value);
const initialTab = stored("tourmate.tab");
const tab = ref(
  ["chat", "trip", "knowledge"].includes(initialTab) ? initialTab : "chat",
);
const busy = ref(false),
  online = ref(null);
const healthController = new AbortController();
const tabs = [
  {
    id: "chat",
    title: "智能客服",
    icon: MessageSquare,
    description: "有问题，随时问",
  },
  { id: "trip", title: "行程助手", icon: Map, description: "让出发更从容" },
  {
    id: "knowledge",
    title: "知识库",
    icon: BookOpen,
    description: "让回答有据可依",
  },
];
function navigate(id) {
  if (!busy.value) {
    tab.value = id;
    persist("tourmate.tab", id);
  }
}
function newSession() {
  if (!busy.value) {
    conversationId.value = crypto.randomUUID();
    persist("tourmate.conversation", conversationId.value);
  }
}
onMounted(async () => {
  try {
    const health = await requestJson("/api/health", {
      signal: healthController.signal,
    });
    online.value = health?.status === "UP";
  } catch (error) {
    if (error.name !== "AbortError") online.value = false;
  }
});
onBeforeUnmount(() => healthController.abort());
</script>

<template>
  <div class="workspace">
    <aside class="sidebar">
      <a class="brand" href="/" aria-label="TourMate 首页"
        ><span class="brand-mark"><Compass :size="25" /></span
        ><span>TourMate<span class="brand-caption">旅途有伴</span></span></a
      >
      <div class="workspace-label">你的旅行工作台</div>
      <nav aria-label="主导航">
        <button
          v-for="item in tabs"
          :key="item.id"
          :class="['nav-item', { active: tab === item.id }]"
          :disabled="busy"
          @click="navigate(item.id)"
          :aria-label="item.title"
          :aria-current="tab === item.id ? 'page' : undefined"
        >
          <component :is="item.icon" :size="19" /><span
            >{{ item.title }}<small>{{ item.description }}</small></span
          ><ArrowUpRight v-if="tab === item.id" :size="15" />
        </button>
      </nav>
      <button class="new-session" :disabled="busy" @click="newSession">
        <Plus :size="17" />新会话
      </button>
      <div class="sidebar-bottom">
        <div class="nature-note">
          <Leaf :size="22" />
          <p>好好计划，<br />也给惊喜留点位置。</p>
          <span>TAKE THE SCENIC ROUTE</span>
        </div>
        <div class="service-state">
          <Circle
            :size="7"
            :fill="online === true ? '#4a947c' : '#b2a891'"
            :stroke-width="0"
          />{{
            online === null
              ? "正在连接服务"
              : online
                ? "后端服务已连接"
                : "后端暂未连接"
          }}
        </div>
      </div>
    </aside>
    <main>
      <header class="topbar">
        <span>探索 · 计划 · 出发</span
        ><span class="local-badge">个人旅行工作台</span>
      </header>
      <ChatPanel
        v-if="tab === 'chat'"
        :key="'chat-' + conversationId"
        :conversation-id="conversationId"
        @busy="busy = $event"
      />
      <TripPanel
        v-else-if="tab === 'trip'"
        :key="'trip-' + conversationId"
        :conversation-id="conversationId"
        @busy="busy = $event"
      />
      <KnowledgePanel v-else @busy="busy = $event" />
      <footer class="page-footer">
        TourMate AI <span>·</span> 为下一次出发，准备一点灵感。
      </footer>
    </main>
  </div>
</template>
