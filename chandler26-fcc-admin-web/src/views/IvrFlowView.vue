<script setup lang="ts">
import { useFlowEditor } from '../features/flows/composables/useFlowEditor';
const { flows, selectedFlow, versions, selectedVersion, definition, version,
  loading, pending, error, outcome, dirty, reload, selectFlow, selectVersion, saveDraft, publish } = useFlowEditor();
</script>

<template>
  <section class="flex h-full min-h-0 flex-col bg-slate-50 text-slate-800">
    <header class="flex flex-wrap items-center justify-between gap-3 border-b bg-white p-4">
      <h1 class="font-semibold">呼叫流程与版本</h1>
      <div class="flex flex-wrap gap-2">
        <el-button :loading="loading" :disabled="pending" @click="reload">刷新</el-button>
        <el-button disabled title="仿真引擎尚未接入">流程仿真未接入</el-button>
      </div>
    </header>
    <div v-if="error" role="alert" class="border-b border-red-200 bg-red-50 p-3 text-sm text-red-700">
      {{ error }} <el-button link :disabled="loading || pending" @click="reload">重试</el-button>
    </div>
    <div v-if="outcome" role="status" class="border-b border-blue-200 bg-blue-50 p-3 text-sm text-blue-800">{{ outcome }}</div>
    <div class="grid min-h-0 flex-1 grid-cols-1 overflow-auto md:grid-cols-[240px_1fr]">
      <aside class="overflow-auto border-r bg-white p-3">
        <p v-if="loading && !flows.length" class="p-3 text-sm text-slate-500">加载流程中...</p>
        <p v-else-if="!flows.length" class="p-3 text-sm text-slate-500">{{ error ? '流程列表不可用' : '尚无已配置流程' }}</p>
        <button v-for="flow in flows" :key="flow.id" :disabled="loading || pending"
          class="mb-2 w-full rounded border p-3 text-left disabled:opacity-50"
          :class="selectedFlow?.flowKey === flow.flowKey ? 'border-blue-400 bg-blue-50' : 'border-slate-200'"
          @click="selectFlow(flow)">
          <span class="block break-words text-sm font-semibold">{{ flow.flowName }}</span>
          <span class="mt-1 block break-all font-mono text-xs text-slate-500">{{ flow.flowKey }}</span>
        </button>
      </aside>
      <main v-if="selectedFlow" class="flex min-h-[450px] min-w-0 flex-col gap-4 p-4">
        <div class="flex flex-wrap items-center gap-3">
          <label for="flow-version" class="text-sm font-medium">版本</label>
          <el-select id="flow-version" :model-value="selectedVersion" :disabled="loading || pending" class="w-64" @update:model-value="selectVersion">
            <el-option v-for="item in versions" :key="item.version" :value="item.version" :label="item.version + ' · ' + item.publishStatus" />
          </el-select>
          <span v-if="dirty" class="text-xs text-amber-700">有未保存修改</span>
        </div>
        <p class="text-sm text-slate-500">保存将创建或更新草稿。当前服务端仅支持 DID_DIRECT，目标为 didDirectConfig.workNo（坐席工号）；不支持的路由与字段会被拒绝。发布后仍需确认运行时激活。</p>
        <label for="flow-definition" class="text-sm font-medium">流程定义 JSON</label>
        <textarea id="flow-definition" v-model="definition" :disabled="loading || pending"
          spellcheck="false" class="min-h-72 flex-1 resize-y rounded border border-slate-300 bg-white p-3 font-mono text-sm focus:border-blue-500 focus:outline-none"
          placeholder="输入流程定义 JSON 对象" />
        <footer class="flex flex-wrap items-center gap-3">
          <el-button type="primary" :disabled="loading || pending || !definition.trim()" @click="saveDraft">保存草稿</el-button>
          <el-button :disabled="loading || pending || dirty || version?.publishStatus !== 'DRAFT'" @click="publish">确认发布草稿</el-button>
          <span v-if="pending" role="status" class="text-sm text-slate-500">正在处理...</span>
        </footer>
      </main>
    </div>
  </section>
</template>
