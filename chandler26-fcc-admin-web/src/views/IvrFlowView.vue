<script setup lang="ts">
import { computed, ref } from "vue";
import { CheckCircle2, Plus, Unlink } from "lucide-vue-next";
import { useFlowEditor } from "../features/flows/composables/useFlowEditor";
import { useFlowDidBindings } from "../features/flows/composables/useFlowDidBindings";
import FlowCanvas from "../features/flows/components/FlowCanvas.vue";
import FlowNodeEditor from "../features/flows/components/FlowNodeEditor.vue";
import FlowDirectory from "../features/flows/components/FlowDirectory.vue";
import {
  readStagedFlow,
  newInboundFlow,
  canEditFlowVersion,
  flowVersionStatusLabel,
  type StagedFlow,
} from "../features/flows/model/stagedFlow";
import { errorText } from "../utils/feedback";
const {
  flows,
  flowPage,
  flowTotal,
  selectedFlow,
  versions,
  versionPage,
  versionTotal,
  selectedVersion,
  definition,
  version,
  loading,
  pending,
  validating,
  createPending,
  error,
  outcome,
  dirty,
  workingCopy,
  validationIssues,
  validated,
  modelNodes,
  reload,
  selectFlow,
  selectFlowPage,
  selectVersion,
  selectVersionPage,
  createFlow: createFlowModel,
  beginDraft,
  saveDraft,
  validateDraft,
  publish,
} = useFlowEditor();
const {
  boundDids,
  availableDids,
  loadingDids,
  didPending,
  didError,
  bindingDialog,
  selectedDidId,
  openBindingDialog,
  bindDid,
  unbindDid,
} = useFlowDidBindings(selectedFlow);
const selectedStage = ref("");
const advanced = ref(false);
const creating = ref(false);
const createKey = ref("");
const createName = ref("");
const graphError = ref("");
const graph = computed(() => {
  try {
    graphError.value = "";
    return readStagedFlow(definition.value);
  } catch (cause) {
    graphError.value = errorText(cause);
    return null;
  }
});
const editable = computed(
  () =>
    !!selectedFlow.value &&
    canEditFlowVersion(
      selectedFlow.value.system,
      version.value?.publishStatus,
      workingCopy.value,
    ) &&
    !loading.value &&
    !pending.value,
);
const draftVersion = computed(() =>
  versions.value.find((item) => item.publishStatus === "DRAFT"),
);
const versionState = computed(() => {
  if (workingCopy.value) return "新草稿 · 尚未保存";
  if (!version.value) return "尚未创建版本";
  return `${version.value.version} · ${flowVersionStatusLabel(version.value.publishStatus)}`;
});
function startFirstDraft() {
  beginDraft(JSON.stringify(newInboundFlow(), null, 2));
  selectedStage.value = "MENU";
}
function copyAsDraft() {
  if (!definition.value) return;
  beginDraft(definition.value);
  selectedStage.value = "MENU";
}
async function openExistingDraft() {
  if (draftVersion.value) await selectVersion(draftVersion.value.version);
}
function updateGraph(value: StagedFlow) {
  definition.value = JSON.stringify(value, null, 2);
}
async function createFlow() {
  const created = await createFlowModel(createKey.value, createName.value);
  if (created) {
    creating.value = false;
    createKey.value = "";
    createName.value = "";
    startFirstDraft();
  }
}
</script>
<template>
  <section class="studio">
    <header class="toolbar">
      <div>
        <h1>IVR 流程编排</h1>
        <p>DID 入口 · 导航语音 · if/else 路由 · 草稿校验与发布</p>
      </div>
      <div class="actions">
        <el-button :loading="loading" :disabled="pending" @click="reload()"
          >刷新</el-button
        >
        <el-button
          :loading="validating"
          :disabled="!editable || !definition || validating"
          @click="validateDraft"
          >校验流程</el-button
        >
        <el-button :disabled="!editable || !definition || !dirty" @click="saveDraft"
          >保存草稿</el-button
        >
        <el-button
          type="primary"
          :disabled="
            !editable ||
            dirty ||
            !validated ||
            version?.publishStatus !== 'DRAFT' ||
            !boundDids.length
          "
          @click="publish"
          >发布版本</el-button
        >
      </div>
    </header>
    <div v-if="error || graphError || didError" role="alert" class="message error">
      {{ error || graphError || didError }}
    </div>
    <div v-if="outcome" role="status" class="message">{{ outcome }}</div>
    <div v-if="validationIssues.length" class="validation-errors" role="alert">
      <strong>当前有 {{ validationIssues.length }} 项配置需要处理</strong>
      <button
        v-for="issue in validationIssues"
        :key="issue.field + issue.message"
        type="button"
        @click="selectedStage = issue.stage"
      >
        {{ issue.message }}
      </button>
    </div>
    <div class="body">
      <FlowDirectory
        :flows="flows"
        :selected-flow-key="selectedFlow?.flowKey"
        :loading="loading"
        :pending="pending"
        :page="flowPage"
        :total="flowTotal"
        @create="creating = true"
        @select="
          selectFlow($event);
          selectedStage = '';
        "
        @page="selectFlowPage"
      />
      <main v-if="selectedFlow" class="workspace">
        <div class="version-bar">
          <el-select
            :model-value="selectedVersion"
            :disabled="loading || pending || workingCopy || !versions.length"
            placeholder="尚未创建版本"
            style="width: 220px"
            @update:model-value="selectVersion"
          >
            <el-option
              v-for="item in versions"
              :key="item.version"
              :value="item.version"
              :label="item.version + ' · ' + flowVersionStatusLabel(item.publishStatus)"
            />
          </el-select>
          <el-pagination
            v-if="versionTotal > 20"
            small
            layout="prev, next"
            :current-page="versionPage"
            :page-size="20"
            :total="versionTotal"
            :disabled="loading || pending"
            @current-change="selectVersionPage"
          />
          <span class="version-state">{{ versionState }}</span>
          <span v-if="dirty" class="dirty">有未保存修改</span>
          <span v-else-if="validated" class="validated">
            <CheckCircle2 :size="14" /> 已通过模型校验
          </span>
          <el-button
            v-if="
              version?.publishStatus === 'PUBLISHED' &&
              !selectedFlow.system &&
              !draftVersion
            "
            text
            type="primary"
            @click="copyAsDraft"
            >基于此版本新建草稿</el-button
          >
          <el-button
            v-else-if="
              version?.publishStatus !== 'DRAFT' &&
              !selectedFlow.system &&
              draftVersion
            "
            text
            type="primary"
            @click="openExistingDraft"
            >返回现有草稿</el-button
          >
          <el-button v-if="definition" text @click="advanced = !advanced">{{
            advanced ? "返回画布" : "JSON 预览"
          }}</el-button>
        </div>
        <section class="entry-bindings" aria-label="呼入被叫号码">
          <div class="entry-copy">
            <strong>被叫号码</strong>
            <span>来电按 DID 选择此流程，并固定当时的已发布版本</span>
          </div>
          <div class="did-list" v-loading="loadingDids">
            <span v-if="!boundDids.length" class="empty-inline">尚未绑定 DID</span>
            <span v-for="did in boundDids" :key="did.id" class="did-number">
              {{ did.phoneNumber }}
              <el-tooltip content="解除绑定" placement="top">
                <el-button
                  v-if="!selectedFlow.system"
                  text
                  circle
                  :icon="Unlink"
                  :disabled="didPending"
                  aria-label="解除被叫号码绑定"
                  @click="unbindDid(did)"
                />
              </el-tooltip>
            </span>
            <el-button
              v-if="!selectedFlow.system"
              :icon="Plus"
              :disabled="didPending"
              @click="openBindingDialog"
              >绑定号码</el-button
            >
          </div>
        </section>
        <div v-if="!definition" class="empty">
          <p>此流程尚未创建版本。</p>
          <el-button
            v-if="!selectedFlow.system"
            type="primary"
            @click="startFirstDraft"
            >创建首个草稿版本</el-button
          >
        </div>
        <textarea
          v-else-if="advanced"
          :value="definition"
          readonly
          class="json-editor"
          aria-label="流程定义 JSON 预览"
          spellcheck="false"
        />
        <div v-else-if="graph" class="canvas-layout">
          <FlowCanvas
            class="canvas"
            :flow="graph"
            :nodes="modelNodes"
            :selected="selectedStage"
            @select="selectedStage = $event"
          />
          <FlowNodeEditor
            v-if="selectedStage && graph.routeMode === 'IVR'"
            :model-value="graph"
            :stage="selectedStage"
            :disabled="!editable"
            :issues="validationIssues"
            @update:model-value="updateGraph"
            @close="selectedStage = ''"
          />
        </div>
        <footer>
          动作与阶段由运行时固定，画布维护导航文案、按键 if 分支、else 兜底路由和超时结果。每通电话固定启动时的版本。
        </footer>
      </main>
      <div v-else class="empty">选择流程，查看阶段和版本。</div>
    </div>
    <el-dialog v-model="creating" title="新建呼入流程" width="min(460px, 92vw)">
      <el-form label-position="top"
        ><el-form-item label="流程代码"
          ><el-input
            v-model="createKey"
            maxlength="64"
            placeholder="例如 SERVICE_INBOUND" /></el-form-item
        ><el-form-item label="流程名称"
          ><el-input v-model="createName" maxlength="128" /></el-form-item
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
    <el-dialog
      v-model="bindingDialog"
      title="绑定呼入被叫号码"
      width="min(460px, 92vw)"
    >
      <el-form label-position="top">
        <el-form-item label="未绑定 DID">
          <el-select
            v-model="selectedDidId"
            filterable
            placeholder="选择已录入的被叫号码"
            style="width: 100%"
          >
            <el-option
              v-for="did in availableDids"
              :key="did.id"
              :label="
                did.routeKey
                  ? `${did.phoneNumber} · 当前 ${did.routeKey}`
                  : did.phoneNumber
              "
              :value="did.id"
            />
          </el-select>
        </el-form-item>
        <p v-if="!availableDids.length" class="dialog-hint">
          没有可绑定号码，请先在通信资源中录入 DID。
        </p>
      </el-form>
      <template #footer>
        <el-button @click="bindingDialog = false">取消</el-button>
        <el-button
          type="primary"
          :loading="didPending"
          :disabled="!selectedDidId"
          @click="bindDid"
          >确认绑定</el-button
        >
      </template>
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
.version-state {
  color: #475569;
  font-size: 12px;
}
.entry-bindings {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 18px;
  border-bottom: 1px solid #e2e8f0;
  background: #f8fafc;
}
.entry-copy {
  display: grid;
  gap: 3px;
  font-size: 12px;
}
.entry-copy span,
.empty-inline,
.dialog-hint {
  color: #718096;
  font-size: 12px;
}
.did-list {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  min-height: 32px;
}
.did-number {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding-left: 9px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  background: #fff;
  color: #334155;
  font-size: 12px;
}
.did-number :deep(.el-button) {
  width: 28px;
  height: 28px;
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
.validation-errors {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 9px 20px;
  border-bottom: 1px solid #fecaca;
  background: #fff7f7;
  color: #b42318;
  font-size: 12px;
}
.validation-errors button {
  border: 1px solid #fecaca;
  border-radius: 999px;
  background: #fff;
  padding: 4px 8px;
}
.validated {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #047857;
  font-size: 12px;
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
  .workspace {
    min-height: 650px;
  }
  .entry-bindings {
    align-items: flex-start;
    flex-direction: column;
  }
  .did-list {
    justify-content: flex-start;
  }
}
</style>
