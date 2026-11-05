<script setup>
import { useForm, usePage } from '@inertiajs/vue3';
import AppLayout from '../../Layouts/AppLayout.vue';
import InputError from '../../Components/InputError.vue';
const page = usePage();
defineProps({ mustVerifyEmail: Boolean, status: String });
const form = useForm({ name: page.props.auth.user.name, email: page.props.auth.user.email });
const del = useForm({ password: '' });
</script>
<template>
  <AppLayout>
    <h1 class="text-2xl font-bold text-accent mb-6">Profile</h1>
    <p v-if="status" class="mb-4 text-sm text-green-800">{{ status }}</p>
    <form class="space-y-4 max-w-md" @submit.prevent="form.patch('/settings/profile')">
      <div>
        <label class="block text-sm font-medium" for="name">Name</label>
        <input id="name" v-model="form.name" class="mt-1 w-full rounded border border-caramel/40 px-3 py-2" />
        <InputError :message="form.errors.name" />
      </div>
      <div>
        <label class="block text-sm font-medium" for="email">Email</label>
        <input id="email" v-model="form.email" type="email" class="mt-1 w-full rounded border border-caramel/40 px-3 py-2" />
        <InputError :message="form.errors.email" />
      </div>
      <p v-if="mustVerifyEmail" class="text-sm text-amber-800">Your email address is unverified.</p>
      <button class="rounded bg-accent text-white px-4 py-2" :disabled="form.processing">Save</button>
    </form>
    <form class="mt-12 space-y-4 max-w-md" @submit.prevent="() => { if (confirm('Delete your account?')) del.delete('/settings/profile'); }">
      <h2 class="font-semibold text-red-800">Delete account</h2>
      <input v-model="del.password" type="password" placeholder="Password" class="w-full rounded border border-caramel/40 px-3 py-2" />
      <InputError :message="del.errors.password" />
      <button class="rounded border border-red-700 text-red-700 px-4 py-2" :disabled="del.processing">Delete account</button>
    </form>
  </AppLayout>
</template>
