<script setup>
import { ref, onBeforeUnmount } from "vue";
import {
  UploadCloud,
  Search,
  FileText,
  ArrowUpRight,
  BookOpen,
} from "lucide-vue-next";
import { requestJson } from "../api.js";
const emit = defineEmits(["busy"]);
const file = ref(null),
  category = ref("general"),
  query = ref(""),
  results = ref(null),
  busy = ref(false),
  error = ref(""),
  success = ref("");
const controller = new AbortController();
function pick(event) {
  file.value = event.target.files?.[0] || null;
  success.value = "";
  error.value = "";
}
async function upload() {
  if (!file.value || busy.value) return;
  if (file.value.size > 1024 * 1024) {
    error.value = "文件不能超过1MB，请拆分后上传。";
    return;
  }
  busy.value = true;
  emit("busy", true);
  error.value = "";
  success.value = "";
  try {
    const body = new FormData();
    body.append("file", file.value);
    body.append("category", category.value);
    const result = await requestJson("/api/knowledge/upload", {
      method: "POST",
      body,
      signal: controller.signal,
    });
    success.value = `上传成功：${result.filename}，已导入 ${result.chunks} 个文本块。`;
  } catch (e) {
    if (e.name !== "AbortError") error.value = e.message;
  } finally {
    busy.value = false;
    emit("busy", false);
  }
}
async function search() {
  if (!query.value.trim() || busy.value) return;
  busy.value = true;
  emit("busy", true);
  error.value = "";
  try {
    results.value = await requestJson(
      "/api/knowledge/search?query=" +
        encodeURIComponent(query.value.trim()) +
        "&limit=5",
      { signal: controller.signal },
    );
  } catch (e) {
    if (e.name !== "AbortError") error.value = e.message;
  } finally {
    busy.value = false;
    emit("busy", false);
  }
}
onBeforeUnmount(() => {
  controller.abort();
  emit("busy", false);
});
</script>

<template>
  <section class="content-page">
    <div class="section-heading">
      <span class="eyebrow">GOOD ANSWERS START HERE</span>
      <h1>有据可依，<span>才更安心。</span></h1>
      <p>把景区介绍与票务规则交给知识库，让每一次回答都有出处。</p>
    </div>
    <div v-if="error" class="error" role="alert">{{ error }}</div>
    <div v-if="success" class="success" role="status">{{ success }}</div>
    <div class="knowledge-grid">
      <div class="card upload-card">
        <span class="card-kicker"><BookOpen :size="16" />添加知识</span
        ><label class="file-drop"
          ><UploadCloud :size="33" :stroke-width="1.4" /><strong>{{
            file?.name || "选择一份旅行资料"
          }}</strong
          ><span>PDF、Word、Markdown 或文本 · 最大1MB</span
          ><input
            type="file"
            aria-label="选择知识文件"
            accept=".pdf,.doc,.docx,.md,.txt"
            :disabled="busy"
            @change="pick" /></label
        ><label for="category">资料分类</label
        ><select id="category" v-model="category" :disabled="busy">
          <option value="general">通用资料</option>
          <option value="opening-hours">开放时间</option>
          <option value="ticket-price">门票价格</option>
          <option value="refund-policy">退改规则</option></select
        ><button
          class="button primary full"
          :disabled="!file || busy"
          @click="upload"
        >
          {{ busy ? "正在处理…" : "上传并入库" }}<ArrowUpRight :size="16" />
        </button>
        <p class="field-hint">
          解析文档、分块并写入当前知识库。请勿上传个人身份、订单密钥等敏感信息。
        </p>
      </div>
      <div class="knowledge-search">
        <form class="search-form" @submit.prevent="search">
          <label for="knowledge-query">检索知识库</label>
          <div>
            <Search :size="18" /><input
              id="knowledge-query"
              v-model="query"
              placeholder="试试“学生票”或“退改规则”"
              maxlength="200"
            /><button
              class="button secondary"
              :disabled="!query.trim() || busy"
            >
              搜索资料
            </button>
          </div>
        </form>
        <div v-if="results === null" class="knowledge-empty">
          <FileText :size="36" :stroke-width="1.1" />
          <h2>资料里的答案，随时可寻</h2>
          <p>上传后搜索关键词，检查知识内容是否可检索。</p>
        </div>
        <p v-else-if="results.length === 0" class="notice">
          未找到相关资料。请换个关键词，或先上传文档。
        </p>
        <article
          v-for="(result, i) in results"
          :key="result.document.id || i"
          class="card knowledge-result"
        >
          <span class="eyebrow">资料 {{ String(i + 1).padStart(2, "0") }}</span>
          <h2>{{ result.document.title }}</h2>
          <p>{{ result.document.content }}</p>
          <span class="muted"
            >{{ result.document.category || "通用资料"
            }}<template v-if="result.document.effectiveDate">
              · 生效于 {{ result.document.effectiveDate }}</template
            ></span
          >
        </article>
        <div class="subtle-note">
          <FileText :size="18" />
          <p>
            这里上传的是客服问答资料。行程规划需要包含价格、游览时长与闭馆日的结构化景点资料，请使用项目提供的行程导入脚本。
          </p>
        </div>
      </div>
    </div>
  </section>
</template>
