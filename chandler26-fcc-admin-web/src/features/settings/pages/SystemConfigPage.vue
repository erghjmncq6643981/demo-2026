<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue';
import { Pencil, Plus, RefreshCw, Trash2, X } from 'lucide-vue-next';
import { systemConfigApi, type ConfigScope, type ConfigValueType, type SystemConfigVO } from '../api/systemConfigApi';
import { confirmAction, errorText, toastError, toastSuccess, toastWarning } from '../../../utils/feedback';

const rows = ref<SystemConfigVO[]>([]);
const scope = ref<ConfigScope>('BACKEND');
const loading = ref(false);
const loadError = ref('');
const saving = ref(false);
const showEditor = ref(false);
const editingId = ref<string | null>(null);
const form = reactive({ propName: '', propValue: '', propType: 'STRING' as ConfigValueType, description: '' });

async function loadConfigs(): Promise<void> {
  loading.value = true;
  loadError.value = '';
  try {
    rows.value = await systemConfigApi.list(scope.value);
  } catch (error) {
    rows.value = [];
    loadError.value = errorText(error, '系统配置加载失败');
  } finally {
    loading.value = false;
  }
}

function openCreate(): void {
  editingId.value = null;
  form.propName = '';
  form.propValue = '';
  form.propType = 'STRING';
  form.description = '';
  showEditor.value = true;
}

function openEdit(row: SystemConfigVO): void {
  editingId.value = row.id;
  form.propName = row.propName;
  form.propValue = row.propValue;
  form.propType = row.propType;
  form.description = row.description || '';
  showEditor.value = true;
}

async function saveConfig(): Promise<void> {
  if (!form.propName.trim() || !form.propValue.trim()) {
    toastWarning('配置键和值均不能为空');
    return;
  }
  saving.value = true;
  try {
    await systemConfigApi.save({
      propName: form.propName.trim(),
      propValue: form.propValue,
      propType: form.propType,
      scope: scope.value,
      description: form.description.trim() || undefined,
    });
    showEditor.value = false;
    toastSuccess(editingId.value ? '系统配置已更新' : '系统配置已创建');
    await loadConfigs();
  } catch (error) {
    toastError(`保存失败：${errorText(error)}`);
  } finally {
    saving.value = false;
  }
}

async function removeConfig(row: SystemConfigVO): Promise<void> {
  const confirmed = await confirmAction(`确认删除配置 ${row.propName} 吗？`, { title: '删除系统配置', confirmText: '确认删除', danger: true });
  if (!confirmed) return;
  try {
    await systemConfigApi.delete(row.id);
    toastSuccess('系统配置已删除');
    await loadConfigs();
  } catch (error) {
    toastError(`删除失败：${errorText(error)}`);
  }
}

watch(scope, loadConfigs);
onMounted(loadConfigs);
</script>

<template>
  <section class="h-full min-h-0 flex flex-col gap-4">
    <header class="flex flex-wrap items-center justify-between gap-3">
      <div><h2 class="text-xl font-bold text-slate-900">系统变量</h2><p class="mt-1 text-sm text-slate-500">配置值直接读写后端配置表，不提供静态示例或浏览器内假保存。</p></div>
      <div class="flex gap-2"><button title="刷新配置" class="h-9 w-9 inline-flex items-center justify-center border border-slate-200 bg-white" @click="loadConfigs"><RefreshCw class="h-4 w-4" :class="loading ? 'animate-spin' : ''" /></button><button class="h-9 px-3 inline-flex items-center gap-2 bg-blue-600 text-sm font-semibold text-white" @click="openCreate"><Plus class="h-4 w-4" />新增配置</button></div>
    </header>

    <div class="flex items-center gap-2 border-y border-slate-200 py-3"><span class="text-sm font-semibold text-slate-600">作用域</span><select v-model="scope" class="h-9 w-44 border border-slate-300 bg-white px-3 text-sm"><option value="WEB">WEB</option><option value="BACKEND">BACKEND</option><option value="CLIENT">CLIENT</option><option value="SYSTEM">SYSTEM</option></select></div>
    <div v-if="loadError" class="flex items-center justify-between border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700"><span>{{ loadError }}</span><button class="font-semibold underline" @click="loadConfigs">重试</button></div>
    <div class="min-h-0 flex-1 overflow-auto border border-slate-200 bg-white">
      <table class="w-full min-w-[900px] text-left text-sm"><thead class="sticky top-0 bg-slate-50 text-xs text-slate-500"><tr><th class="px-4 py-3">配置键</th><th class="px-4 py-3">值</th><th class="px-4 py-3">类型</th><th class="px-4 py-3">说明</th><th class="px-4 py-3">最后更新</th><th class="px-4 py-3 text-right">操作</th></tr></thead>
        <tbody class="divide-y divide-slate-100"><tr v-if="loading"><td colspan="6" class="px-4 py-14 text-center text-slate-500">正在加载配置...</td></tr><tr v-else-if="rows.length === 0"><td colspan="6" class="px-4 py-14 text-center text-slate-500">当前作用域没有配置</td></tr>
          <tr v-for="row in rows" v-else :key="row.id" class="hover:bg-slate-50"><td class="px-4 py-3 font-mono font-semibold">{{ row.propName }}</td><td class="max-w-sm truncate px-4 py-3 font-mono text-xs" :title="row.propValue">{{ row.propValue }}</td><td class="px-4 py-3">{{ row.propType }}</td><td class="px-4 py-3 text-slate-600">{{ row.description || '-' }}</td><td class="px-4 py-3 text-xs text-slate-500">{{ row.updatedAt || '-' }}<span v-if="row.updatedBy" class="ml-1">· {{ row.updatedBy }}</span></td><td class="px-4 py-3"><div class="flex justify-end gap-1"><button title="编辑配置" class="h-8 w-8 inline-flex items-center justify-center text-blue-600" @click="openEdit(row)"><Pencil class="h-4 w-4" /></button><button title="删除配置" class="h-8 w-8 inline-flex items-center justify-center text-rose-600" @click="removeConfig(row)"><Trash2 class="h-4 w-4" /></button></div></td></tr>
        </tbody></table>
    </div>

    <div v-if="showEditor" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4"><form class="w-full max-w-lg bg-white p-5 shadow-xl" @submit.prevent="saveConfig">
      <div class="mb-5 flex items-center justify-between"><h3 class="font-bold text-slate-900">{{ editingId ? '编辑配置' : '新增配置' }}</h3><button type="button" title="关闭" @click="showEditor = false"><X class="h-5 w-5" /></button></div>
      <div class="space-y-4"><label class="block text-sm font-semibold">配置键<input v-model="form.propName" :disabled="Boolean(editingId)" class="mt-1 h-10 w-full border border-slate-300 px-3 disabled:bg-slate-100"></label><label class="block text-sm font-semibold">配置值<textarea v-model="form.propValue" rows="4" class="mt-1 w-full border border-slate-300 p-3 font-mono text-sm"></textarea></label><label class="block text-sm font-semibold">数据类型<select v-model="form.propType" class="mt-1 h-10 w-full border border-slate-300 px-3"><option value="STRING">STRING</option><option value="JSON">JSON</option><option value="INT">INT</option><option value="BOOLEAN">BOOLEAN</option></select></label><label class="block text-sm font-semibold">业务说明<input v-model="form.description" class="mt-1 h-10 w-full border border-slate-300 px-3"></label></div>
      <div class="mt-6 flex justify-end gap-2"><button type="button" class="h-9 px-4 border border-slate-300" @click="showEditor = false">取消</button><button :disabled="saving" class="h-9 px-4 bg-blue-600 font-semibold text-white disabled:opacity-50">{{ saving ? '保存中...' : '保存' }}</button></div>
    </form></div>
  </section>
</template>
