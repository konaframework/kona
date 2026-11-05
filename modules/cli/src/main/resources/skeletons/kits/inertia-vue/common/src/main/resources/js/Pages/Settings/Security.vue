<script setup>
import { useForm } from '@inertiajs/vue3';
import AppLayout from '../../Layouts/AppLayout.vue';
import InputError from '../../Components/InputError.vue';
defineProps({ enabled: Boolean, confirmed: Boolean, qrCodeUrl: String, recoveryCodes: { type: Array, default: () => [] }, status: String });
const passwordForm = useForm({ current_password: '', password: '', password_confirmation: '' });
const codeForm = useForm({ code: '' });
const empty = useForm({});
</script>
<template>
  <AppLayout>
    <h1 class="text-2xl font-bold text-accent mb-6">Security</h1>
    <p v-if="status" class="mb-4 text-sm text-green-800">{{ status }}</p>
    <section class="max-w-md space-y-4 mb-12">
      <h2 class="font-semibold">Update password</h2>
      <form class="space-y-3" @submit.prevent="passwordForm.put('/settings/password')">
        <input v-model="passwordForm.current_password" type="password" placeholder="Current password" class="w-full rounded border border-caramel/40 px-3 py-2" />
        <InputError :message="passwordForm.errors.current_password" />
        <input v-model="passwordForm.password" type="password" placeholder="New password" class="w-full rounded border border-caramel/40 px-3 py-2" />
        <input v-model="passwordForm.password_confirmation" type="password" placeholder="Confirm password" class="w-full rounded border border-caramel/40 px-3 py-2" />
        <InputError :message="passwordForm.errors.password" />
        <button class="rounded bg-accent text-white px-4 py-2">Save password</button>
      </form>
    </section>
    <section class="max-w-lg space-y-4">
      <h2 class="font-semibold">Two-factor authentication</h2>
      <p class="text-sm opacity-80">{{ enabled ? 'Enabled' : (qrCodeUrl ? 'Pending confirmation' : 'Not enabled') }}</p>
      <div class="flex flex-wrap gap-2">
        <button class="rounded bg-accent text-white px-3 py-2 text-sm" @click="empty.post('/settings/two-factor-authentication')">Enable</button>
        <button class="rounded border border-caramel/50 px-3 py-2 text-sm" @click="empty.delete('/settings/two-factor-authentication')">Disable</button>
        <button class="rounded border border-caramel/50 px-3 py-2 text-sm" @click="empty.post('/settings/two-factor-recovery-codes')">Regenerate codes</button>
      </div>
      <p v-if="qrCodeUrl" class="text-xs break-all bg-white/60 p-3 rounded border border-caramel/20">{{ qrCodeUrl }}</p>
      <form v-if="qrCodeUrl && !confirmed" class="flex gap-2" @submit.prevent="codeForm.post('/settings/confirmed-two-factor-authentication')">
        <input v-model="codeForm.code" placeholder="Authenticator code" class="flex-1 rounded border border-caramel/40 px-3 py-2" />
        <button class="rounded bg-accent text-white px-3 py-2">Confirm</button>
      </form>
      <InputError :message="codeForm.errors.code" />
      <ul v-if="recoveryCodes?.length" class="text-sm font-mono grid grid-cols-2 gap-1">
        <li v-for="code in recoveryCodes" :key="code">{{ code }}</li>
      </ul>
    </section>
  </AppLayout>
</template>
