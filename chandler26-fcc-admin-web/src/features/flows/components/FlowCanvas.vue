<script setup lang="ts">
import { stageLabels, executionLabels, type StagedFlow, type StageExecution } from '../model/stagedFlow';
const props = withDefaults(
  defineProps<{ flow: StagedFlow; executions?: StageExecution[]; selected?: string; readonly?: boolean }>(),
  { executions: () => [] },
);
defineEmits<{ select: [stage: string] }>();
const latest = (stage: string) => {
  const rows = props.executions.filter((item) => item.stepKey === stage);
  return rows[rows.length - 1];
};
</script>

<template>
  <div class="flow-canvas" aria-label="通话阶段画布">
    <template v-for="(stage, index) in flow.stages" :key="stage">
      <div v-if="index" class="connector" aria-hidden="true">↓</div>
      <button
        type="button"
        class="stage"
        :class="[latest(stage)?.status?.toLowerCase(), { selected: selected === stage }]"
        @click="$emit('select', stage)"
      >
        <div class="stage-head">
          <span class="number">{{ index + 1 }}</span
          ><strong>{{ stageLabels[stage] || stage }}</strong>
          <span class="status">{{
            latest(stage)
              ? executionLabels[latest(stage)!.status] || latest(stage)!.status
              : readonly
                ? '未经过'
                : '固定动作'
          }}</span>
        </div>
        <p class="stage-key">{{ stage }}</p>
        <p v-if="stage === 'ENTRY'">DID 匹配已发布版本 → 固定本次通话流程</p>
        <p v-if="stage === 'MENU'">
          {{
            flow.menu?.enabled
              ? `${flow.menu.prompt || '请选择提示音'} · 等待 ${flow.menu.timeoutSeconds} 秒`
              : '菜单关闭 · false 分支直达路由'
          }}
        </p>
        <div v-if="stage === 'BRANCH'" class="branches">
          <span v-for="branch in flow.branches" :key="branch.digit"
            >if 按 {{ branch.digit }} → {{ branch.targetType === 'AGENT' ? '坐席' : '技能组' }}
            {{ branch.target || '未配置' }}</span
          >
          <span>else → {{ flow.defaultRoute?.target || '未配置默认目标' }}</span>
        </div>
        <p v-if="stage === 'ROUTE'">
          {{ flow.defaultRoute?.targetType === 'GROUP' ? '技能组' : '坐席' }}
          {{ flow.defaultRoute?.target || '按选中分支分配' }} · 最长
          {{ flow.defaultRoute?.queueSeconds || 120 }} 秒
        </p>
        <p v-if="stage === 'BRIDGE'">等待真实桥接事件后进入通话阶段</p>
        <p v-if="stage === 'END'">
          {{ flow.timeoutAction === 'HANGUP' ? '挂机结束' : '未接通时记录漏话回拨待办' }}
        </p>
        <p v-if="latest(stage)" class="execution">
          第 {{ latest(stage)!.attemptNo }} 次 · {{ latest(stage)!.startedAt }}
          <span v-if="latest(stage)!.durationMs != null">· {{ latest(stage)!.durationMs }} ms</span>
        </p>
      </button>
    </template>
  </div>
</template>

<style scoped>
.flow-canvas {
  padding: 28px 16px;
  overflow: auto;
  background-color: #f7f9fc;
  background-image: radial-gradient(#d8e0ec 1px, transparent 1px);
  background-size: 18px 18px;
  display: flex;
  flex-direction: column;
  align-items: center;
  min-height: 100%;
}
.stage {
  background: white;
  border: 1px solid #dbe2ec;
  border-radius: 12px;
  padding: 16px;
  text-align: left;
  width: min(100%, 560px);
  box-shadow: 0 2px 5px #14284a06;
  color: #344257;
  flex-shrink: 0;
}
.stage:hover,
.stage.selected {
  border-color: #537bea;
  box-shadow: 0 0 0 2px #537bea18;
}
.stage-head {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
}
.number {
  background: #eef2ff;
  color: #4c63c6;
  border-radius: 6px;
  padding: 3px 7px;
}
.status {
  margin-left: auto;
  font-size: 11px;
  color: #798697;
  white-space: nowrap;
}
.stage p {
  font-size: 12px;
  margin: 8px 0 0;
  overflow-wrap: anywhere;
}
.stage-key {
  color: #94a0af;
  font-family: monospace;
}
.connector {
  color: #9ba9bb;
  line-height: 30px;
}
.branches {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}
.branches span {
  font-size: 11px;
  padding: 6px 9px;
  background: #f0f4fa;
  border-radius: 6px;
}
.stage.succeeded {
  border-left: 4px solid #28a879;
}
.stage.waiting {
  border-left: 4px solid #517be9;
}
.stage.interrupted,
.stage.failed,
.stage.unknown {
  border-left: 4px solid #d89036;
}
.execution {
  color: #64748b;
}
</style>
