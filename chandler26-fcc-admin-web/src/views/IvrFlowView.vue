<script setup lang="ts">
import { computed, ref } from 'vue';
import { useFlowEditor } from '../features/flows/composables/useFlowEditor';
import FlowCanvas from '../features/flows/components/FlowCanvas.vue';
import FlowNodeEditor from '../features/flows/components/FlowNodeEditor.vue';
import { readStagedFlow, newInboundFlow, type StagedFlow } from '../features/flows/model/stagedFlow';
import { flowApi } from '../api/flowApi';
import { errorText } from '../utils/feedback';
const {
  flows,
  selectedFlow,
  versions,
  selectedVersion,
  definition,
  version,
  loading,
  pending,
  error,
  outcome,
  dirty,
  reload,
  selectFlow,
  selectVersion,
  saveDraft,
  publish,
} = useFlowEditor();
const selectedStage = ref('');
const advanced = ref(false);
const creating = ref(false);
const createPending = ref(false);
const createKey = ref('');
const createName = ref('');
const graphError = ref('');
const graph = computed(() => {
  try {
    graphError.value = '';
    return readStagedFlow(definition.value);
  } catch (cause) {
    graphError.value = errorText(cause);
    return null;
  }
});
const editable = computed(
  () => !selectedFlow.value?.flowKey.startsWith('SYSTEM_') && !loading.value && !pending.value,
);
function updateGraph(value: StagedFlow) {
  definition.value = JSON.stringify(value, null, 2);
}
async function createFlow() {
  createPending.value = true;
  try {
    await flowApi.create(createKey.value, createName.value);
    creating.value = false;
    await reload();
  } catch (cause) {
    error.value = errorText(cause);
  } finally {
    createPending.value = false;
  }
}
</script>
<template>
  <section class="studio">
    <header class="toolbar">
      <div>
        <h1>IVR 流程编排</h1>
        <p>固定阶段 · 条件分支 · 版本发布</p>
      </div>
      <div class="actions">
        <el-button :loading="loading" :disabled="pending" @click="reload">刷新</el-button>
        <el-button :disabled="!editable || !definition" @click="saveDraft">保存草稿</el-button>
        <el-button
          type="primary"
          :disabled="!editable || dirty || version?.publishStatus !== 'DRAFT'"
          @click="publish"
          >发布版本</el-button
        >
      </div>
    </header>
    <div v-if="error || graphError" role="alert" class="message error">{{ error || graphError }}</div>
    <div v-if="outcome" role="status" class="message">{{ outcome }}</div>
    <div class="body">
      <aside class="directory">
        <div class="directory-head">
          <strong>业务流程</strong><el-button text type="primary" @click="creating = true">＋ 新建</el-button>
        </div>
        <p v-if="!flows.length" class="empty">{{ loading ? '加载中…' : '尚无流程，请先新建' }}</p>
        <button
          v-for="flow in flows"
          :key="flow.id"
          :disabled="loading || pending"
          :class="{ active: flow.flowKey === selectedFlow?.flowKey }"
          @click="
            selectFlow(flow);
            selectedStage = '';
          "
        >
          <strong>{{ flow.flowName }}</strong
          ><small>{{ flow.flowKey }}</small
          ><span>{{ flow.status === 'PUBLISHED' ? '已发布' : '草稿' }}</span>
        </button>
      </aside>
      <main v-if="selectedFlow" class="workspace">
        <div class="version-bar">
          <el-select
            :model-value="selectedVersion"
            :disabled="loading || pending"
            placeholder="尚无版本"
            style="width: 220px"
            @update:model-value="selectVersion"
          >
            <el-option
              v-for="item in versions"
              :key="item.version"
              :value="item.version"
              :label="item.version + ' · ' + item.publishStatus"
            />
          </el-select>
          <span v-if="dirty" class="dirty">有未保存修改</span>
          <el-button text @click="advanced = !advanced">{{ advanced ? '返回画布' : '高级定义' }}</el-button>
        </div>
        <div v-if="!definition" class="empty">
          <p>此流程尚无阶段定义。</p>
          <el-button type="primary" @click="updateGraph(newInboundFlow())">配置呼入阶段</el-button>
        </div>
        <textarea
          v-else-if="advanced"
          v-model="definition"
          :disabled="!editable"
          class="json-editor"
          aria-label="高级流程定义"
          spellcheck="false"
        />
        <div v-else-if="graph" class="canvas-layout">
          <FlowCanvas
            class="canvas"
            :flow="graph"
            :selected="selectedStage"
            @select="selectedStage = $event"
          />
          <FlowNodeEditor
            v-if="selectedStage && graph.routeMode === 'IVR'"
            :model-value="graph"
            :stage="selectedStage"
            :disabled="!editable"
            @update:model-value="updateGraph"
            @close="selectedStage = ''"
          />
        </div>
        <footer>点击节点配置参数。每通电话固定启动时的版本；发布不会改写正在执行的通话。</footer>
      </main>
      <div v-else class="empty">选择流程，查看阶段和版本。</div>
    </div>
    <el-dialog v-model="creating" title="新建呼入流程" width="min(460px, 92vw)">
      <el-form label-position="top"
        ><el-form-item label="流程代码"
          ><el-input v-model="createKey" maxlength="64" placeholder="例如 SERVICE_INBOUND" /></el-form-item
        ><el-form-item label="流程名称"><el-input v-model="createName" maxlength="128" /></el-form-item
      ></el-form>
      <template #footer
        ><el-button @click="creating = false">取消</el-button
        ><el-button
          type="primary"
          :loading="createPending"
          :disabled="!createKey.trim() || !createName.trim()"
          @click="createFlow"
          >创建</el-button
        ></template
      >
    </el-dialog>
  </section>
</template>
<style scoped>
.studio {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: #fff;
  color: #334155;
}
.toolbar {
  padding: 18px 22px;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
}
.toolbar h1 {
  font-size: 18px;
  font-weight: 700;
}
.toolbar p {
  font-size: 12px;
  color: #8793a3;
  margin-top: 5px;
}
.actions {
  display: flex;
  align-items: center;
}
.body {
  display: flex;
  flex: 1;
  min-height: 0;
}
.directory {
  width: 230px;
  flex-shrink: 0;
  padding: 14px;
  border-right: 1px solid #e2e8f0;
  overflow: auto;
}
.directory-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
}
.directory > button {
  display: block;
  width: 100%;
  text-align: left;
  border: 1px solid #e2e8f0;
  border-radius: 9px;
  padding: 12px;
  margin-top: 10px;
  font-size: 12px;
}
.directory > button.active {
  background: #eef3ff;
  border-color: #8da6ed;
}
.directory small {
  display: block;
  overflow-wrap: anywhere;
  color: #8793a3;
  margin: 7px 0;
}
.directory span {
  color: #6d7e98;
  font-size: 11px;
}
.workspace {
  min-width: 0;
  display: flex;
  flex: 1;
  flex-direction: column;
}
.version-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 18px;
  border-bottom: 1px solid #e2e8f0;
  flex-wrap: wrap;
}
.canvas-layout {
  display: flex;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
.canvas {
  flex: 1;
  min-width: 0;
}
.empty {
  padding: 30px;
  color: #94a3b8;
  font-size: 13px;
}
.dirty {
  color: #b47b22;
  font-size: 12px;
}
.message {
  padding: 10px 20px;
  font-size: 12px;
  background: #eff6ff;
}
.error {
  color: #b42318;
  background: #fff1f0;
}
footer {
  padding: 10px 18px;
  font-size: 11px;
  color: #8793a3;
  border-top: 1px solid #e2e8f0;
}
.json-editor {
  flex: 1;
  min-height: 350px;
  padding: 20px;
  font-family: monospace;
  font-size: 12px;
}
@media (max-width: 900px) {
  .directory {
    width: 180px;
  }
  .canvas-layout {
    overflow: auto;
    flex-direction: column;
  }
  .canvas {
    min-height: 500px;
  }
  .canvas-layout :deep(.node-editor) {
    width: 100%;
    border-top: 1px solid #e2e8f0;
  }
}
@media (max-width: 600px) {
  .body {
    flex-direction: column;
    overflow: auto;
  }
  .directory {
    width: 100%;
    max-height: 190px;
    border-bottom: 1px solid #e2e8f0;
  }
  .workspace {
    min-height: 650px;
  }
}
</style>
