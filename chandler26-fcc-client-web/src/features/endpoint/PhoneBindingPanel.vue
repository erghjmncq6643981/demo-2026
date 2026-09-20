<script setup lang="ts">
import { usePhoneBinding } from './usePhoneBinding';
const { extension, code, status, error, busy, create, refresh } = usePhoneBinding();
</script>
<template>
  <details class="mx-6 mb-2 bg-white rounded-xl border p-3 text-sm">
    <summary class="cursor-pointer font-bold">绑定实体话机</summary>
    <p class="mt-3">填写话机分机号，获取验证码后在该话机拨打 0000，按提示输入八位验证码。有效期为两分钟。</p>
    <form class="flex flex-wrap gap-3 mt-3" @submit.prevent="create"><label>分机号 <input v-model="extension" required pattern="[0-9]{2,20}" class="border rounded p-2" /></label><button :disabled="busy" class="border rounded px-3">获取验证码</button><button type="button" :disabled="busy" @click="refresh" class="border rounded px-3">检查绑定结果</button></form>
    <p v-if="code" class="mt-3 font-mono text-xl">{{ code }}</p>
    <p v-if="status === 'CONSUMED'" role="status" class="text-green-700 mt-3">话机绑定成功，接听终端已刷新。</p>
    <p v-if="status === 'EXPIRED'" role="status" class="mt-3">验证码已过期，请重新获取。</p>
    <p v-if="error" role="alert" class="mt-3 text-red-700">{{ error }}</p>
  </details>
</template>
