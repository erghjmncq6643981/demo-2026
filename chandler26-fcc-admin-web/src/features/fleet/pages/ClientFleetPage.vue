<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { Plus, RefreshCw, X } from 'lucide-vue-next';
import { clientFleetApi, type ClientHardwareRecordVO, type ClientVersionVO } from '../api/clientFleetApi';
import { errorText, toastError, toastSuccess, toastWarning } from '../../../utils/feedback';

const versions = ref<ClientVersionVO[]>([]);
const hardware = ref<ClientHardwareRecordVO[]>([]);
const loading = ref(false);
const loadError = ref('');
const publishing = ref(false);
const showRelease = ref(false);
const platform = ref('');
const releaseForm = reactive({ version: '', platform: 'WINDOWS', downloadUrl: '', fileMd5: '', forceUpdate: false, releaseNotes: '' });

async function loadFleet(): Promise<void> {
  loading.value = true;
  loadError.value = '';
  try {
    [versions.value, hardware.value] = await Promise.all([
      clientFleetApi.listVersions(platform.value || undefined),
      clientFleetApi.listHardware(),
    ]);
  } catch (error) {
    versions.value = [];
    hardware.value = [];
    loadError.value = errorText(error, '客户端治理数据加载失败');
  } finally {
    loading.value = false;
  }
}

function openRelease(): void {
  Object.assign(releaseForm, { version: '', platform: 'WINDOWS', downloadUrl: '', fileMd5: '', forceUpdate: false, releaseNotes: '' });
  showRelease.value = true;
}

async function publishVersion(): Promise<void> {
  if (!releaseForm.version.trim() || !releaseForm.downloadUrl.trim()) {
    toastWarning('版本号和下载地址均不能为空');
    return;
  }
  publishing.value = true;
  try {
    await clientFleetApi.publishVersion({
      ...releaseForm,
      version: releaseForm.version.trim(),
      downloadUrl: releaseForm.downloadUrl.trim(),
      fileMd5: releaseForm.fileMd5.trim() || undefined,
      releaseNotes: releaseForm.releaseNotes.trim() || undefined,
    });
    showRelease.value = false;
    toastSuccess('客户端版本已发布');
    await loadFleet();
  } catch (error) {
    toastError(`发布失败：${errorText(error)}`);
  } finally {
    publishing.value = false;
  }
}

onMounted(loadFleet);
</script>

<template>
  <section class="h-full min-h-0 flex flex-col gap-4">
    <header class="flex flex-wrap items-center justify-between gap-3"><div><h2 class="text-xl font-bold text-slate-900">客户端管理</h2><p class="mt-1 text-sm text-slate-500">版本发布与硬件审计均为数据库事实，不展示预置终端或伪发布历史。</p></div><div class="flex gap-2"><button title="刷新客户端数据" class="h-9 w-9 inline-flex items-center justify-center border border-slate-200 bg-white" @click="loadFleet"><RefreshCw class="h-4 w-4" :class="loading ? 'animate-spin' : ''" /></button><button class="h-9 px-3 inline-flex items-center gap-2 bg-blue-600 text-sm font-semibold text-white" @click="openRelease"><Plus class="h-4 w-4" />发布版本</button></div></header>
    <div class="flex items-center gap-2 border-y border-slate-200 py-3"><span class="text-sm font-semibold text-slate-600">平台</span><select v-model="platform" class="h-9 w-44 border border-slate-300 bg-white px-3 text-sm" @change="loadFleet"><option value="">全部</option><option value="WINDOWS">WINDOWS</option><option value="MAC">MAC</option><option value="LINUX">LINUX</option><option value="WEB">WEB</option></select></div>
    <div v-if="loadError" class="flex items-center justify-between border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700"><span>{{ loadError }}</span><button class="font-semibold underline" @click="loadFleet">重试</button></div>
    <div class="min-h-0 flex-1 overflow-y-auto space-y-6">
      <div><h3 class="mb-2 text-sm font-bold text-slate-800">版本发布记录</h3><div class="overflow-auto border border-slate-200 bg-white"><table class="w-full min-w-[850px] text-left text-sm"><thead class="bg-slate-50 text-xs text-slate-500"><tr><th class="px-4 py-3">版本</th><th class="px-4 py-3">平台</th><th class="px-4 py-3">状态</th><th class="px-4 py-3">强制升级</th><th class="px-4 py-3">下载地址</th><th class="px-4 py-3">发布时间</th></tr></thead><tbody class="divide-y divide-slate-100"><tr v-if="loading"><td colspan="6" class="px-4 py-10 text-center text-slate-500">正在加载...</td></tr><tr v-else-if="versions.length === 0"><td colspan="6" class="px-4 py-10 text-center text-slate-500">暂无版本发布记录</td></tr><tr v-for="row in versions" v-else :key="row.id"><td class="px-4 py-3 font-mono font-semibold">{{ row.version }}</td><td class="px-4 py-3">{{ row.platform }}</td><td class="px-4 py-3">{{ row.status }}</td><td class="px-4 py-3">{{ row.forceUpdate ? '是' : '否' }}</td><td class="max-w-sm truncate px-4 py-3 font-mono text-xs" :title="row.downloadUrl">{{ row.downloadUrl }}</td><td class="px-4 py-3 text-slate-500">{{ row.releasedAt || '-' }}</td></tr></tbody></table></div></div>
      <div><h3 class="mb-2 text-sm font-bold text-slate-800">终端硬件审计</h3><div class="overflow-auto border border-slate-200 bg-white"><table class="w-full min-w-[850px] text-left text-sm"><thead class="bg-slate-50 text-xs text-slate-500"><tr><th class="px-4 py-3">坐席工号</th><th class="px-4 py-3">客户端版本</th><th class="px-4 py-3">操作系统</th><th class="px-4 py-3">网络地址</th><th class="px-4 py-3">MAC</th><th class="px-4 py-3">登录时间</th></tr></thead><tbody class="divide-y divide-slate-100"><tr v-if="hardware.length === 0"><td colspan="6" class="px-4 py-10 text-center text-slate-500">暂无终端审计记录</td></tr><tr v-for="row in hardware" v-else :key="row.id"><td class="px-4 py-3 font-mono font-semibold">{{ row.workNum }}</td><td class="px-4 py-3">{{ row.clientVersion || '-' }}</td><td class="px-4 py-3">{{ row.os || '-' }}</td><td class="px-4 py-3 font-mono text-xs">{{ row.ipAddr || '-' }}</td><td class="px-4 py-3 font-mono text-xs">{{ row.macAddr || '-' }}</td><td class="px-4 py-3 text-slate-500">{{ row.loginTime || '-' }}</td></tr></tbody></table></div></div>
    </div>

    <div v-if="showRelease" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4"><form class="w-full max-w-lg bg-white p-5 shadow-xl" @submit.prevent="publishVersion"><div class="mb-5 flex items-center justify-between"><h3 class="font-bold text-slate-900">发布客户端版本</h3><button type="button" title="关闭" @click="showRelease = false"><X class="h-5 w-5" /></button></div><div class="grid grid-cols-2 gap-4"><label class="text-sm font-semibold">版本号<input v-model="releaseForm.version" class="mt-1 h-10 w-full border border-slate-300 px-3"></label><label class="text-sm font-semibold">平台<select v-model="releaseForm.platform" class="mt-1 h-10 w-full border border-slate-300 px-3"><option value="WINDOWS">WINDOWS</option><option value="MAC">MAC</option><option value="LINUX">LINUX</option><option value="WEB">WEB</option></select></label><label class="col-span-2 text-sm font-semibold">下载地址<input v-model="releaseForm.downloadUrl" class="mt-1 h-10 w-full border border-slate-300 px-3"></label><label class="col-span-2 text-sm font-semibold">MD5<input v-model="releaseForm.fileMd5" class="mt-1 h-10 w-full border border-slate-300 px-3 font-mono"></label><label class="col-span-2 text-sm font-semibold">发布说明<textarea v-model="releaseForm.releaseNotes" rows="3" class="mt-1 w-full border border-slate-300 p-3"></textarea></label><label class="col-span-2 flex items-center gap-2 text-sm"><input v-model="releaseForm.forceUpdate" type="checkbox">要求强制升级</label></div><div class="mt-6 flex justify-end gap-2"><button type="button" class="h-9 px-4 border border-slate-300" @click="showRelease = false">取消</button><button :disabled="publishing" class="h-9 px-4 bg-blue-600 font-semibold text-white disabled:opacity-50">{{ publishing ? '发布中...' : '发布' }}</button></div></form></div>
  </section>
</template>
