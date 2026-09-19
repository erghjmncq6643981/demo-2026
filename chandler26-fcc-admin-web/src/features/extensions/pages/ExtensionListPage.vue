<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { Link, Plus, RefreshCw, Search, Trash2, X } from 'lucide-vue-next';
import { extensionApi, type ExtensionVO } from '../api/extensionApi';
import { confirmAction, errorText, toastError, toastSuccess, toastWarning } from '../../../utils/feedback';

const rows = ref<ExtensionVO[]>([]);
const total = ref(0);
const loading = ref(false);
const loadError = ref('');
const mutating = ref(false);
const pageNum = ref(1);
const pageSize = ref(20);
const query = reactive({ extension: '', endpointType: '', onlineStatus: '' });

const showCreate = ref(false);
const createForm = reactive({ extension: '', password: '', endpointType: 'SIP' });
const showBind = ref(false);
const bindingTarget = ref<ExtensionVO | null>(null);
const bindingWorkNo = ref('');

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)));

async function loadExtensions(): Promise<void> {
  loading.value = true;
  loadError.value = '';
  try {
    const result = await extensionApi.list({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      extension: query.extension.trim() || undefined,
      endpointType: query.endpointType || undefined,
      onlineStatus: query.onlineStatus || undefined,
    });
    rows.value = result.list ?? [];
    total.value = result.total ?? 0;
  } catch (error) {
    rows.value = [];
    total.value = 0;
    loadError.value = errorText(error, '分机列表加载失败');
  } finally {
    loading.value = false;
  }
}

function search(): void {
  pageNum.value = 1;
  void loadExtensions();
}

function resetQuery(): void {
  query.extension = '';
  query.endpointType = '';
  query.onlineStatus = '';
  search();
}

function openCreate(): void {
  createForm.extension = '';
  createForm.password = '';
  createForm.endpointType = 'SIP';
  showCreate.value = true;
}

async function createExtension(): Promise<void> {
  const extension = createForm.extension.trim();
  const password = createForm.password.trim();
  if (!extension || !password) {
    toastWarning('分机号和注册密码均不能为空');
    return;
  }
  mutating.value = true;
  try {
    await extensionApi.create({ extension, password, endpointType: createForm.endpointType });
    showCreate.value = false;
    toastSuccess(`分机 ${extension} 已创建并提交下发`);
    await loadExtensions();
  } catch (error) {
    toastError(`创建失败：${errorText(error)}`);
  } finally {
    mutating.value = false;
  }
}

async function removeExtension(row: ExtensionVO): Promise<void> {
  const confirmed = await confirmAction(`删除分机 ${row.extension} 后将同步移除 FreeSWITCH 配置。确认继续吗？`, {
    title: '删除分机',
    confirmText: '确认删除',
    danger: true,
  });
  if (!confirmed) return;

  mutating.value = true;
  try {
    await extensionApi.delete(row.id);
    toastSuccess(`分机 ${row.extension} 已删除`);
    await loadExtensions();
  } catch (error) {
    toastError(`删除失败：${errorText(error)}`);
  } finally {
    mutating.value = false;
  }
}

function openBind(row: ExtensionVO): void {
  bindingTarget.value = row;
  bindingWorkNo.value = '';
  showBind.value = true;
}

async function bindAgent(): Promise<void> {
  const workNo = bindingWorkNo.value.trim();
  if (!bindingTarget.value || !workNo) {
    toastWarning('请输入坐席工号');
    return;
  }
  mutating.value = true;
  try {
    const result = await extensionApi.ivrBind({ extension: bindingTarget.value.extension, workNo });
    if (!result.success) throw new Error(result.promptMessage || '绑定未成功');
    showBind.value = false;
    toastSuccess(result.promptMessage || '分机绑定成功');
    await loadExtensions();
  } catch (error) {
    toastError(`绑定失败：${errorText(error)}`);
  } finally {
    mutating.value = false;
  }
}

async function changePage(delta: number): Promise<void> {
  const next = Math.min(totalPages.value, Math.max(1, pageNum.value + delta));
  if (next === pageNum.value) return;
  pageNum.value = next;
  await loadExtensions();
}

onMounted(loadExtensions);
</script>

<template>
  <section class="h-full min-h-0 flex flex-col gap-4">
    <header class="flex flex-wrap items-center justify-between gap-3">
      <div><h2 class="text-xl font-bold text-slate-900">分机管理</h2><p class="mt-1 text-sm text-slate-500">分机配置、注册状态与坐席绑定均来自管理端实时接口。</p></div>
      <div class="flex items-center gap-2">
        <button title="刷新分机列表" class="h-9 w-9 inline-flex items-center justify-center rounded-md border border-slate-200 bg-white text-slate-600 hover:bg-slate-50" @click="loadExtensions"><RefreshCw class="h-4 w-4" :class="loading ? 'animate-spin' : ''" /></button>
        <button class="h-9 px-3 inline-flex items-center gap-2 rounded-md bg-blue-600 text-sm font-semibold text-white hover:bg-blue-700" @click="openCreate"><Plus class="h-4 w-4" />新增分机</button>
      </div>
    </header>

    <div class="flex flex-wrap items-end gap-3 border-y border-slate-200 bg-white py-3">
      <label class="text-xs font-semibold text-slate-600">分机号<input v-model="query.extension" class="mt-1 block h-9 w-44 rounded-md border border-slate-300 px-3 text-sm" @keyup.enter="search"></label>
      <label class="text-xs font-semibold text-slate-600">终端类型<select v-model="query.endpointType" class="mt-1 block h-9 w-40 rounded-md border border-slate-300 px-2 text-sm"><option value="">全部</option><option value="SIP">SIP</option><option value="WEBRTC">WebRTC</option></select></label>
      <label class="text-xs font-semibold text-slate-600">注册状态<select v-model="query.onlineStatus" class="mt-1 block h-9 w-40 rounded-md border border-slate-300 px-2 text-sm"><option value="">全部</option><option value="ONLINE">在线</option><option value="OFFLINE">离线</option></select></label>
      <button class="h-9 px-3 inline-flex items-center gap-2 rounded-md bg-slate-900 text-sm font-semibold text-white" @click="search"><Search class="h-4 w-4" />查询</button>
      <button class="h-9 px-3 rounded-md border border-slate-300 text-sm text-slate-600" @click="resetQuery">重置</button>
    </div>

    <div v-if="loadError" class="flex items-center justify-between border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700"><span>{{ loadError }}</span><button class="font-semibold underline" @click="loadExtensions">重试</button></div>

    <div class="min-h-0 flex-1 overflow-auto border border-slate-200 bg-white">
      <table class="w-full min-w-[900px] text-left text-sm">
        <thead class="sticky top-0 bg-slate-50 text-xs text-slate-500"><tr><th class="px-4 py-3">分机号</th><th class="px-4 py-3">终端类型</th><th class="px-4 py-3">注册状态</th><th class="px-4 py-3">绑定坐席</th><th class="px-4 py-3">注册地址</th><th class="px-4 py-3 text-right">操作</th></tr></thead>
        <tbody class="divide-y divide-slate-100">
          <tr v-if="loading"><td colspan="6" class="px-4 py-14 text-center text-slate-500">正在加载分机...</td></tr>
          <tr v-else-if="rows.length === 0"><td colspan="6" class="px-4 py-14 text-center text-slate-500">没有符合条件的分机</td></tr>
          <tr v-for="row in rows" v-else :key="row.id" class="hover:bg-slate-50">
            <td class="px-4 py-3 font-mono font-semibold text-slate-900">{{ row.extension }}</td><td class="px-4 py-3">{{ row.endpointType }}</td>
            <td class="px-4 py-3"><span :class="row.onlineStatus === 'ONLINE' ? 'text-emerald-700' : 'text-slate-500'">{{ row.onlineStatus === 'ONLINE' ? '在线' : '离线' }}</span></td>
            <td class="px-4 py-3">{{ row.boundAgentName || '未绑定' }}<span v-if="row.boundAgentWorkNo" class="ml-1 font-mono text-xs text-slate-400">{{ row.boundAgentWorkNo }}</span></td>
            <td class="max-w-xs truncate px-4 py-3 font-mono text-xs text-slate-500" :title="row.registeredContact || row.registeredIp || ''">{{ row.registeredContact || row.registeredIp || '-' }}</td>
            <td class="px-4 py-3"><div class="flex justify-end gap-1"><button title="绑定坐席" class="h-8 w-8 inline-flex items-center justify-center text-blue-600 hover:bg-blue-50" @click="openBind(row)"><Link class="h-4 w-4" /></button><button title="删除分机" class="h-8 w-8 inline-flex items-center justify-center text-rose-600 hover:bg-rose-50" @click="removeExtension(row)"><Trash2 class="h-4 w-4" /></button></div></td>
          </tr>
        </tbody>
      </table>
    </div>

    <footer class="flex items-center justify-between text-sm text-slate-500"><span>共 {{ total }} 条</span><div class="flex items-center gap-2"><button class="h-8 px-3 border border-slate-300 disabled:opacity-40" :disabled="pageNum <= 1" @click="changePage(-1)">上一页</button><span>{{ pageNum }} / {{ totalPages }}</span><button class="h-8 px-3 border border-slate-300 disabled:opacity-40" :disabled="pageNum >= totalPages" @click="changePage(1)">下一页</button></div></footer>

    <div v-if="showCreate" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4"><form class="w-full max-w-md bg-white p-5 shadow-xl" @submit.prevent="createExtension">
      <div class="mb-5 flex items-center justify-between"><h3 class="font-bold text-slate-900">新增分机</h3><button type="button" title="关闭" @click="showCreate = false"><X class="h-5 w-5" /></button></div>
      <div class="space-y-4"><label class="block text-sm font-semibold">分机号<input v-model="createForm.extension" autocomplete="off" class="mt-1 h-10 w-full rounded-md border border-slate-300 px-3"></label><label class="block text-sm font-semibold">注册密码<input v-model="createForm.password" type="password" autocomplete="new-password" class="mt-1 h-10 w-full rounded-md border border-slate-300 px-3"></label><label class="block text-sm font-semibold">终端类型<select v-model="createForm.endpointType" class="mt-1 h-10 w-full rounded-md border border-slate-300 px-3"><option value="SIP">SIP</option><option value="WEBRTC">WebRTC</option></select></label></div>
      <div class="mt-6 flex justify-end gap-2"><button type="button" class="h-9 px-4 border border-slate-300" @click="showCreate = false">取消</button><button :disabled="mutating" class="h-9 px-4 bg-blue-600 font-semibold text-white disabled:opacity-50">{{ mutating ? '提交中...' : '创建' }}</button></div>
    </form></div>

    <div v-if="showBind && bindingTarget" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4"><form class="w-full max-w-md bg-white p-5 shadow-xl" @submit.prevent="bindAgent">
      <div class="mb-4 flex items-center justify-between"><h3 class="font-bold text-slate-900">绑定坐席</h3><button type="button" title="关闭" @click="showBind = false"><X class="h-5 w-5" /></button></div><p class="mb-4 text-sm text-slate-500">目标分机：<span class="font-mono text-slate-900">{{ bindingTarget.extension }}</span></p><label class="block text-sm font-semibold">坐席工号<input v-model="bindingWorkNo" autocomplete="off" class="mt-1 h-10 w-full rounded-md border border-slate-300 px-3"></label>
      <div class="mt-6 flex justify-end gap-2"><button type="button" class="h-9 px-4 border border-slate-300" @click="showBind = false">取消</button><button :disabled="mutating" class="h-9 px-4 bg-blue-600 font-semibold text-white disabled:opacity-50">{{ mutating ? '绑定中...' : '确认绑定' }}</button></div>
    </form></div>
  </section>
</template>
