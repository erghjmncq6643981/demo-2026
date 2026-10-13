<template>
  <!-- 挂机后小结与工单登记抽屉 -->
  <div
    v-if="callStore.showSummaryDrawer"
    class="fixed inset-y-0 right-0 w-full max-w-96 overflow-y-auto bg-white shadow-2xl border-l border-slate-100 z-50 flex flex-col justify-between animate-in slide-in-from-right duration-300"
  >
    <div class="p-6 pb-0 flex-1 overflow-y-auto">
      <div class="flex items-center justify-between pb-3 border-b border-slate-100 mb-4">
        <div>
          <h3 class="font-extrabold text-sm text-slate-900">通话小结</h3>
          <p class="text-xs text-slate-400 mt-0.5">可稍后填写，不影响接听下一通</p>
        </div>
        <div class="flex items-center gap-2">
          <span class="text-xs text-slate-500">待填 {{ callStore.summaryCalls.length }} 通</span>
          <button
            @click="handleManualClose"
            class="text-slate-400 hover:text-slate-700 text-sm font-bold p-1 rounded-lg hover:bg-slate-100 transition-colors"
          >
            ✕
          </button>
        </div>
      </div>

      <div class="space-y-4 text-xs">
        <div>
          <label class="block font-bold text-slate-700 mb-1.5">通话号码</label>
          <input
            type="text"
            :value="callStore.summaryCall?.callerNumber || '未知号码'"
            readonly
            class="w-full bg-slate-50 border border-slate-200 rounded-xl px-3 py-2 font-mono text-slate-700 text-xs"
          />
        </div>

        <div>
          <label class="block font-bold text-slate-700 mb-1.5">业务类别</label>
          <select
            v-model="businessCategory"
            class="w-full bg-slate-50 border border-slate-200 rounded-xl px-3 py-2 text-slate-800 font-semibold focus:outline-none text-xs"
          >
            <option>业务咨询与产品解答</option>
            <option>售后服务与投诉处理</option>
            <option>签约续约与意向跟进</option>
            <option>其他服务诉求</option>
          </select>
        </div>

        <div>
          <label class="block font-bold text-slate-700 mb-1.5">意向评估</label>
          <div class="grid grid-cols-3 gap-2">
            <button
              type="button"
              @click="intentLevel = 'HIGH'"
              :class="[
                'py-2 rounded-xl font-bold border text-xs transition-all',
                intentLevel === 'HIGH' ? 'bg-emerald-50 text-emerald-700 border-emerald-300' : 'bg-slate-50 text-slate-600 border-slate-200'
              ]"
            >
              高意向
            </button>
            <button
              type="button"
              @click="intentLevel = 'MID'"
              :class="[
                'py-2 rounded-xl font-medium border text-xs transition-all',
                intentLevel === 'MID' ? 'bg-brand-50 text-brand-700 border-brand-300' : 'bg-slate-50 text-slate-600 border-slate-200'
              ]"
            >
              中等
            </button>
            <button
              type="button"
              @click="intentLevel = 'LOW'"
              :class="[
                'py-2 rounded-xl font-medium border text-xs transition-all',
                intentLevel === 'LOW' ? 'bg-slate-100 text-slate-700 border-slate-300' : 'bg-slate-50 text-slate-600 border-slate-200'
              ]"
            >
              无意向
            </button>
          </div>
        </div>

        <div>
          <label class="block font-bold text-slate-700 mb-1.5">跟进沟通纪要</label>
          <textarea
            v-model="notes"
            rows="4"
            placeholder="简要记录客户诉求、约定跟进时间或解决方案..."
            class="w-full bg-slate-50 border border-slate-200 rounded-xl p-3 text-slate-800 text-xs focus:outline-none focus:border-brand-500"
          ></textarea>
        </div>
      </div>
    </div>

    <div class="p-6 pt-4 border-t border-slate-100 flex gap-3 bg-white">
      <button
        @click="handleManualClose"
        class="flex-1 py-2.5 bg-slate-100 hover:bg-slate-200 text-slate-700 font-bold rounded-2xl text-xs transition-colors"
      >
        稍后处理
      </button>
      <button
        @click="submitSummary"
        :disabled="saving"
        class="flex-1 py-2.5 bg-brand-500 hover:bg-brand-600 text-white font-extrabold rounded-2xl text-xs shadow-pill transition-colors flex items-center justify-center gap-1.5"
      >
        {{ saving ? '正在保存…' : '保存小结' }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { useCallStore } from '../../stores/callStore';
import { useCdrStore } from '../../stores/cdrStore';

const callStore = useCallStore();
const cdrStore = useCdrStore();

const businessCategory = ref('业务咨询与产品解答');
const intentLevel = ref('UNASSESSED');
const notes = ref('');
const saving = ref(false);

watch(() => callStore.summaryCall?.callId, () => {
  businessCategory.value = '业务咨询与产品解答';
  intentLevel.value = 'UNASSESSED';
  notes.value = '';
});

function handleManualClose() {
  callStore.showSummaryDrawer = false;
}

async function submitSummary() {
  if (saving.value) return;
  saving.value = true;
  try {
    const saved = await callStore.saveSummary({
      category: businessCategory.value || '业务咨询与产品解答',
      intent: intentLevel.value || 'UNASSESSED',
      notes: notes.value,
    });
    if (saved) await cdrStore.loadRecords(1);
  } finally {
    saving.value = false;
  }
}

</script>
