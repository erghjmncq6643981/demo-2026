<script setup lang="ts">
import { onMounted } from 'vue';
import { useDialJobs } from './useDialJobs';
const { rows, selected, number, mode, maxAttempts, page, error, busy, load, create, detail, control } = useDialJobs();
onMounted(load);
</script>
<template>
 <section class="bg-white border rounded-2xl p-5 overflow-auto">
  <h2 class="font-bold mb-3">自动外呼任务</h2>
  <p class="text-sm text-slate-600 mb-4">渐进式外呼需要坐席先示闲并接听话机；通知型播放管理员配置的语音，客户按 1 确认。任务遵守服务端呼叫时段与频控。</p>
  <form class="flex flex-wrap gap-3 items-end mb-4" @submit.prevent="create">
   <label>客户号码<input v-model="number" required class="block border rounded p-2" /></label>
   <label>模式<select v-model="mode" class="block border rounded p-2"><option value="PROGRESSIVE">渐进式坐席外呼</option><option value="NOTIFICATION">通知型外呼</option></select></label>
   <label>最多尝试<input v-model.number="maxAttempts" type="number" min="1" max="3" class="block border rounded p-2 w-24" /></label>
   <button :disabled="busy" class="bg-brand-600 text-white rounded p-2">创建任务</button>
   <button type="button" :disabled="busy" @click="load" class="border rounded p-2">刷新</button>
  </form>
  <p v-if="error" role="alert" class="text-red-700">{{ error }}</p><p v-if="busy" role="status">正在处理…</p>
  <table class="w-full text-left text-sm"><thead><tr><th>号码</th><th>模式</th><th>状态</th><th>操作</th></tr></thead><tbody>
   <tr v-for="job in rows" :key="job.id" class="border-t"><td class="py-3">{{ job.number }}</td><td>{{ job.mode === 'NOTIFICATION' ? '通知' : '渐进式' }}</td><td>{{ job.status }}</td><td class="space-x-3">
    <button :disabled="busy" @click="detail(job.id)">逐次结果</button>
    <button v-if="job.status === 'PENDING'" :disabled="busy" @click="control(job.id,'PAUSE')">暂停</button>
    <button v-if="job.status === 'PAUSED'" :disabled="busy" @click="control(job.id,'RESUME')">继续</button>
    <button v-if="['PENDING','PAUSED','RUNNING'].includes(job.status)" :disabled="busy" @click="control(job.id,'CANCEL')">取消后续拨号</button>
   </td></tr>
  </tbody></table>
  <p v-if="!rows.length && !busy">暂无任务。</p>
  <div class="flex gap-4 my-4"><button :disabled="busy || page === 1" @click="page--; load()">上一页</button><span>第 {{ page }} 页</span><button :disabled="busy || rows.length < 50" @click="page++; load()">下一页</button></div>
  <div v-if="selected" class="border rounded p-3"><h3 class="font-bold">任务 {{ selected.id }} 的尝试记录</h3><p v-for="attempt in selected.attempts" :key="attempt.id">第 {{ attempt.attemptNo }} 次：{{ attempt.status }} · {{ attempt.result || '等待最终结果' }}</p><p v-if="!selected.attempts?.length">尚未派发。</p></div>
 </section>
</template>
