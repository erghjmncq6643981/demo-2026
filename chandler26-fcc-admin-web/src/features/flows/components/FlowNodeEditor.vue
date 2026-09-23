<script setup lang="ts">
import { computed } from 'vue';
import {
  stageLabels,
  updateDefaultRoute,
  type FlowTarget,
  type FlowValidationIssue,
  type StagedFlow,
} from '../model/stagedFlow';
const props = defineProps<{
  modelValue: StagedFlow;
  stage: string;
  disabled?: boolean;
  issues?: FlowValidationIssue[];
}>();
const emit = defineEmits<{ 'update:modelValue': [value: StagedFlow]; close: [] }>();
const flow = computed(() => props.modelValue);
const stageIssues = computed(() => (props.issues || []).filter((item) => item.stage === props.stage));
function change(mutator: (value: StagedFlow) => void) {
  const copy = JSON.parse(JSON.stringify(props.modelValue)) as StagedFlow;
  mutator(copy);
  emit('update:modelValue', copy);
}
function menu(key: 'enabled' | 'prompt' | 'timeoutSeconds', value: string | number | boolean) {
  change((f) => {
    Object.assign(f.menu!, { [key]: value });
  });
}
function defaultRoute(key: keyof FlowTarget, value: string | number) {
  emit('update:modelValue', updateDefaultRoute(props.modelValue, { [key]: value }));
}
</script>
<template>
  <aside class="node-editor" aria-label="节点配置">
    <header>
      <div>
        <small>阶段参数</small>
        <h3>{{ stageLabels[stage] }}</h3>
      </div>
      <el-button text @click="emit('close')">关闭</el-button>
    </header>
    <p class="hint">阶段动作固定。修改只作用于草稿，发布后供新通话使用。</p>
    <ul v-if="stageIssues.length" class="issues" role="alert">
      <li v-for="issue in stageIssues" :key="issue.field">{{ issue.message }}</li>
    </ul>
    <fieldset :disabled="disabled">
      <template v-if="stage === 'MENU'">
        <label class="check"
          ><input
            type="checkbox"
            :checked="flow.menu?.enabled"
            @change="menu('enabled', ($event.target as HTMLInputElement).checked)"
          />启用语音菜单（true / false）</label
        >
        <label
          >导航语音文案或预置音频路径<textarea
            :value="flow.menu?.prompt"
            rows="4"
            maxlength="1000"
            placeholder="例如：您好，请按 1 转人工；也可填写 /sounds/welcome.wav"
            @change="menu('prompt', ($event.target as HTMLTextAreaElement).value)"
        /></label>
        <label
          >等待按键（秒）<input
            type="number"
            min="3"
            max="60"
            :value="flow.menu?.timeoutSeconds"
            @change="menu('timeoutSeconds', ($event.target as HTMLInputElement).valueAsNumber)"
        /></label>
        <p class="hint">普通文案由 Sidecar 生成并复用 TTS 文件；绝对路径只用于 FreeSWITCH 已有的预置音频。</p>
      </template>
      <template v-else-if="stage === 'BRANCH'">
        <div v-for="(branch, index) in flow.branches" :key="index" class="branch-editor">
          <label
            >if 按键<input
              maxlength="1"
              :value="branch.digit"
              @change="
                change((f) => {
                  f.branches![index]!.digit = ($event.target as HTMLInputElement).value;
                })
              "
          /></label>
          <label
            >目标类型<select
              :value="branch.targetType"
              @change="
                change((f) => {
                  f.branches![index]!.targetType = ($event.target as HTMLSelectElement).value as
                    'AGENT' | 'GROUP';
                })
              "
            >
              <option value="AGENT">坐席工号</option>
              <option value="GROUP">技能组代码</option>
            </select></label
          >
          <label
            >目标<input
              :value="branch.target"
              @change="
                change((f) => {
                  f.branches![index]!.target = ($event.target as HTMLInputElement).value;
                })
              "
          /></label>
          <label
            >排队时限（秒）<input
              type="number"
              min="5"
              max="300"
              :value="branch.queueSeconds"
              @change="
                change((f) => {
                  f.branches![index]!.queueSeconds = ($event.target as HTMLInputElement).valueAsNumber;
                })
              "
          /></label>
          <el-button
            text
            type="danger"
            @click="
              change((f) => {
                f.branches!.splice(index, 1);
              })
            "
            >移除此分支</el-button
          >
        </div>
        <el-button
          :disabled="disabled || (flow.branches?.length || 0) >= 10"
          @click="
            change((f) => {
              f.branches!.push({ digit: '', targetType: 'GROUP', target: '', queueSeconds: 120 });
            })
          "
          >添加按键分支</el-button
        >
        <section class="else-editor" aria-label="else 默认路由">
          <strong>else · 未匹配按键</strong>
          <label
            >目标类型<select
              :value="flow.defaultRoute?.targetType"
              @change="defaultRoute('targetType', ($event.target as HTMLSelectElement).value)"
            >
              <option value="AGENT">坐席工号</option>
              <option value="GROUP">技能组代码</option>
            </select></label
          >
          <label
            >目标<input
              :value="flow.defaultRoute?.target"
              @change="defaultRoute('target', ($event.target as HTMLInputElement).value)"
          /></label>
          <label
            >排队时限（秒）<input
              type="number"
              min="5"
              max="300"
              :value="flow.defaultRoute?.queueSeconds"
              @change="defaultRoute('queueSeconds', ($event.target as HTMLInputElement).valueAsNumber)"
          /></label>
        </section>
        <p class="hint">每个按键只能出现一次；else 是所有未匹配按键的明确兜底分支。</p>
      </template>
      <template v-else-if="stage === 'ROUTE'">
        <label
          >默认目标类型<select
            :value="flow.defaultRoute?.targetType"
            @change="
              change((f) => {
                f.defaultRoute!.targetType = ($event.target as HTMLSelectElement).value as 'AGENT' | 'GROUP';
              })
            "
          >
            <option value="AGENT">坐席工号</option>
            <option value="GROUP">技能组代码</option>
          </select></label
        >
        <label
          >默认目标<input
            :value="flow.defaultRoute?.target"
            @change="
              change((f) => {
                f.defaultRoute!.target = ($event.target as HTMLInputElement).value;
              })
            "
        /></label>
        <label
          >最长排队（秒）<input
            type="number"
            min="5"
            max="300"
            :value="flow.defaultRoute?.queueSeconds"
            @change="
              change((f) => {
                f.defaultRoute!.queueSeconds = ($event.target as HTMLInputElement).valueAsNumber;
              })
            "
        /></label>
        <p class="hint">技能组按最长空闲分配；不能修改为未实现的分配算法。</p>
      </template>
      <template v-else-if="stage === 'END'">
        <label
          >未接通或菜单超时<select
            :value="flow.timeoutAction"
            @change="
              change((f) => {
                f.timeoutAction = ($event.target as HTMLSelectElement).value as 'CALLBACK' | 'HANGUP';
              })
            "
          >
            <option value="CALLBACK">记录漏话回拨待办并挂机</option>
            <option value="HANGUP">直接挂机</option>
          </select></label
        >
      </template>
      <p v-else class="hint">此阶段由运行端事件驱动，没有可编辑参数。DID 与流程的关联在号码管理中配置。</p>
    </fieldset>
  </aside>
</template>
<style scoped>
.node-editor {
  background: white;
  padding: 20px;
  overflow-y: auto;
  border-left: 1px solid #e2e8f0;
  width: 340px;
  max-width: 100%;
  flex-shrink: 0;
}
header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
h3 {
  font-size: 15px;
  font-weight: 700;
}
small,
.hint {
  color: #718096;
  font-size: 12px;
}
.hint {
  line-height: 1.7;
  margin: 14px 0;
}
label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  margin: 14px 0;
}
input:not([type='checkbox']),
textarea,
select {
  display: block;
  width: 100%;
  margin-top: 7px;
  border: 1px solid #d9e1ec;
  border-radius: 7px;
  padding: 9px;
  background: #fff;
  font-size: 12px;
}
textarea {
  resize: vertical;
}
.check {
  display: flex;
  gap: 7px;
}
.branch-editor {
  padding: 0 12px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  margin-bottom: 12px;
}
.else-editor {
  padding: 12px;
  border-left: 3px solid #64748b;
  background: #f8fafc;
  margin-top: 16px;
}
.else-editor > strong {
  font-size: 12px;
}
fieldset:disabled {
  opacity: 0.55;
}
.issues {
  display: grid;
  gap: 5px;
  margin: 12px 0;
  border: 1px solid #fecaca;
  border-radius: 8px;
  background: #fff1f2;
  padding: 10px 12px 10px 28px;
  color: #b42318;
  font-size: 12px;
}
</style>
