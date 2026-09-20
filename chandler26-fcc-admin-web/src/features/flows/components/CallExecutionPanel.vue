<script setup lang="ts">
import { ref, computed, toRef } from 'vue';
import { useCallExecution } from '../composables/useCallExecution';
import { executionLabels } from '../model/stagedFlow';
import FlowCanvas from './FlowCanvas.vue';
const props = defineProps<{ callId: string }>();
const { flow, executions, error, loading, cursor, status, version, load } = useCallExecution(
  toRef(props, 'callId'),
);
const selected = ref('');
const attempts = computed(() => executions.value.filter((row) => row.stepKey === selected.value));
</script>
<template>
  <section class="execution-panel">
    <header>
      <span
        >{{ version || '通话执行记录' }} · {{ status === 'ENDED' ? '已结束' : '运行状态以数据库为准' }}</span
      ><el-button :loading="loading" @click="load()">刷新事实</el-button>
    </header>
    <p v-if="error" role="alert" class="error">
      {{ error }} <el-button text @click="load()">重试</el-button>
    </p>
    <p v-else-if="!flow" class="empty">
      {{ loading ? '读取执行快照…' : '这通电话没有持久化流程记录，不推测历史执行路径。' }}
    </p>
    <div v-else class="execution-body">
      <FlowCanvas
        :flow="flow"
        :executions="executions"
        :selected="selected"
        readonly
        class="canvas"
        @select="selected = $event"
      />
      <aside v-if="selected">
        <h3>{{ selected }} · 执行尝试</h3>
        <p v-if="!attempts.length" class="empty">未经过此阶段，或记录尚未加载。</p>
        <article v-for="attempt in attempts" :key="attempt.id">
          <strong
            >第 {{ attempt.attemptNo }} 次 · {{ executionLabels[attempt.status] || attempt.status }}</strong
          >
          <dl>
            <dt>开始</dt>
            <dd>{{ attempt.startedAt }}</dd>
            <dt>结束</dt>
            <dd>{{ attempt.endedAt || '等待后续事件' }}</dd>
            <dt>耗时</dt>
            <dd>{{ attempt.durationMs == null ? '—' : `${attempt.durationMs} ms` }}</dd>
            <dt>事件</dt>
            <dd>{{ attempt.eventId || '内部阶段推进' }}</dd>
            <dt>指令</dt>
            <dd>{{ attempt.commandId || '—' }}</dd>
          </dl>
          <pre>{{ attempt.output || attempt.input || '{}' }}</pre>
        </article>
      </aside>
    </div>
    <el-button v-if="cursor" :loading="loading" @click="load(true)">加载更多阶段记录</el-button>
  </section>
</template>
<style scoped>
.execution-panel {
  min-height: 300px;
  overflow: auto;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
}
header {
  padding: 10px 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  font-size: 12px;
  flex-wrap: wrap;
}
.execution-body {
  display: flex;
  max-height: 58vh;
  overflow: auto;
}
.canvas {
  flex: 1;
  min-width: 0;
}
aside {
  width: 280px;
  max-width: 100%;
  padding: 14px;
  border-left: 1px solid #e2e8f0;
  overflow: auto;
  font-size: 12px;
  flex-shrink: 0;
}
article {
  padding: 12px 0;
  border-bottom: 1px solid #e2e8f0;
}
dl {
  margin-top: 10px;
}
dt {
  color: #94a3b8;
  margin-top: 6px;
}
dd,
pre {
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}
pre {
  margin-top: 10px;
  font-size: 11px;
  background: #f8fafc;
  padding: 8px;
}
.empty,
.error {
  padding: 20px;
  color: #718096;
  font-size: 12px;
}
.error {
  color: #b42318;
}
@media (max-width: 700px) {
  .execution-body {
    flex-direction: column;
  }
  aside {
    width: 100%;
  }
  .canvas {
    min-height: 450px;
  }
}
</style>
