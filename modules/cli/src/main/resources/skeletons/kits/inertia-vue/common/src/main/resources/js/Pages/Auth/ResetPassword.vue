<script setup>
import { useForm } from '@inertiajs/vue3';
import AuthLayout from '../../Layouts/AuthLayout.vue';
import InputError from '../../Components/InputError.vue';
const props = defineProps({ token: String, email: String });
const form = useForm({ token: props.token, email: props.email, password: '', password_confirmation: '' });
const submit = () => form.post('/reset-password');
</script>
<template>
  <AuthLayout title="Reset password">
    <form class="space-y-4" @submit.prevent="submit">
      <div>
        <label class="block text-sm font-medium" for="email">Email</label>
        <input id="email" v-model="form.email" type="email" class="mt-1 w-full rounded border border-caramel/40 px-3 py-2" />
        <InputError :message="form.errors.email" />
      </div>
      <div>
        <label class="block text-sm font-medium" for="password">Password</label>
        <input id="password" v-model="form.password" type="password" class="mt-1 w-full rounded border border-caramel/40 px-3 py-2" />
        <InputError :message="form.errors.password" />
      </div>
      <div>
        <label class="block text-sm font-medium" for="password_confirmation">Confirm password</label>
        <input id="password_confirmation" v-model="form.password_confirmation" type="password" class="mt-1 w-full rounded border border-caramel/40 px-3 py-2" />
      </div>
      <button class="w-full rounded bg-accent text-white py-2 font-medium" :disabled="form.processing">Reset password</button>
    </form>
  </AuthLayout>
</template>
