<script setup>
import { ref } from 'vue';
import { useForm } from '@inertiajs/vue3';
import AuthLayout from '../../Layouts/AuthLayout.vue';
import InputError from '../../Components/InputError.vue';
const recovery = ref(false);
const form = useForm({ code: '', recovery_code: '' });
</script>
<template>
  <AuthLayout title="Two-factor challenge">
    <form class="space-y-4" @submit.prevent="form.post('/two-factor-challenge')">
      <div v-if="!recovery">
        <label class="block text-sm font-medium" for="code">Authentication code</label>
        <input id="code" v-model="form.code" type="text" inputmode="numeric" class="mt-1 w-full rounded border border-caramel/40 px-3 py-2" />
        <InputError :message="form.errors.code" />
      </div>
      <div v-else>
        <label class="block text-sm font-medium" for="recovery_code">Recovery code</label>
        <input id="recovery_code" v-model="form.recovery_code" type="text" class="mt-1 w-full rounded border border-caramel/40 px-3 py-2" />
      </div>
      <button class="w-full rounded bg-accent text-white py-2 font-medium" :disabled="form.processing">Continue</button>
      <button type="button" class="w-full text-sm text-caramel" @click="recovery = !recovery">
        {{ recovery ? 'Use an authentication code' : 'Use a recovery code' }}
      </button>
    </form>
  </AuthLayout>
</template>
