<script setup lang="ts">
import type { FlowDefinitionVO } from '../../../api/flowApi';
import { flowTemplateLabel } from '../model/stagedFlow';

defineProps<{
  flows: FlowDefinitionVO[];
  selectedFlowKey?: string;
  loading: boolean;
  pending: boolean;
  page: number;
  total: number;
}>();
defineEmits<{
  create: [];
  select: [flow: FlowDefinitionVO];
  page: [page: number];
}>();
</script>

<template>
  <aside class="directory">
    <div class="directory-head">
      <strong>业务流程</strong>
      <el-button text type="primary" @click="$emit('create')">＋ 新建</el-button>
    </div>
    <p v-if="!flows.length" class="empty">
      {{ loading ? '加载中…' : '尚无流程，请先新建' }}
    </p>
    <button
      v-for="flow in flows"
      :key="flow.id"
      type="button"
      :disabled="loading || pending"
      :class="{ active: flow.flowKey === selectedFlowKey }"
      @click="$emit('select', flow)"
    >
      <strong>{{ flow.flowName }}</strong>
      <small>{{ flow.flowKey }}</small>
      <span>{{ flowTemplateLabel(flow.modelType) }} · {{ flow.status === 'PUBLISHED' ? '已发布' : '草稿' }}</span>
    </button>
    <el-pagination
      v-if="total > 20"
      small
      layout="prev, next"
      :current-page="page"
      :page-size="20"
      :total="total"
      :disabled="loading || pending"
      @current-change="$emit('page', $event)"
    />
  </aside>
</template>

<style scoped>
.directory { width: 230px; flex-shrink: 0; padding: 14px; border-right: 1px solid #e2e8f0; overflow: auto; }
.directory-head { display: flex; align-items: center; justify-content: space-between; font-size: 12px; }
.directory > button { display: block; width: 100%; margin-top: 10px; border: 1px solid #e2e8f0; border-radius: 9px; padding: 12px; text-align: left; font-size: 12px; }
.directory > button.active { border-color: #8da6ed; background: #eef3ff; }
.directory small { display: block; margin: 7px 0; overflow-wrap: anywhere; color: #8793a3; }
.directory span { color: #6d7e98; font-size: 11px; }
.empty { padding: 30px 0; color: #94a3b8; font-size: 13px; }
@media (max-width: 900px) { .directory { width: 180px; } }
@media (max-width: 600px) { .directory { width: 100%; max-height: 190px; border-bottom: 1px solid #e2e8f0; } }
</style>
