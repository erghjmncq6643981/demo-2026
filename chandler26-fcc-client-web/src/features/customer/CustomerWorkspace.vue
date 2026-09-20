<script setup lang="ts">
import { onMounted } from 'vue';
import { useCustomers } from './useCustomers';
const { rows, selected, page, busy, error, load, detail, save } = useCustomers();
onMounted(load);
</script>
<template>
  <section class="p-5 bg-white rounded-2xl border border-slate-200 overflow-auto">
    <div class="flex justify-between items-center mb-4"><h2 class="font-bold">我的客户</h2><button :disabled="busy" @click="selected = { name: '', phoneNumber: '' }">新增客户</button></div>
    <p v-if="error" role="alert" class="text-red-700 mb-3">{{ error }}</p>
    <p v-if="busy" role="status">正在处理…</p>
    <div class="grid lg:grid-cols-2 gap-6">
      <div>
        <ul><li v-for="row in rows" :key="row.id" class="border-b py-3"><button :disabled="busy" class="text-left w-full" @click="detail(row.id!)">{{ row.name }} · {{ row.phoneNumber }}<span class="block text-slate-500">{{ row.companyName || '未填写单位' }}</span></button></li></ul>
        <p v-if="!rows.length && !busy">暂无客户资料。</p>
        <div class="flex gap-4 mt-4"><button :disabled="busy || page === 1" @click="page--; load()">上一页</button><span>第 {{ page }} 页</span><button :disabled="busy || rows.length < 50" @click="page++; load()">下一页</button></div>
      </div>
      <form class="flex flex-col gap-3" @submit.prevent="save">
        <label>姓名<input v-model="selected.name" required maxlength="128" class="block border rounded p-2 w-full" /></label>
        <label>电话号码<input v-model="selected.phoneNumber" required maxlength="32" class="block border rounded p-2 w-full" /></label>
        <label>单位<input v-model="selected.companyName" maxlength="255" class="block border rounded p-2 w-full" /></label>
        <label>备注<textarea v-model="selected.notes" maxlength="4000" rows="4" class="block border rounded p-2 w-full" /></label>
        <button :disabled="busy" class="bg-brand-600 text-white rounded p-2">保存客户资料</button>
      </form>
    </div>
  </section>
</template>
