<script setup>
import { useForm } from '@inertiajs/vue3';
import AuthLayout from '../../Layouts/AuthLayout.vue';
import InputError from '../../Components/InputError.vue';
import TextLink from '../../Components/TextLink.vue';
defineProps({ canResetPassword: Boolean, canRegister: Boolean, status: String });
const form = useForm({ email: '', password: '', remember: false });
const submit = () => form.post('/login');
</script>
<template>
  <AuthLayout title="Sign in">
    <p v-if="status" class="mb-4 text-sm text-green-800">{{ status }}</p>
    <form class="space-y-4" @submit.prevent="submit">
      <div>
        <label class="block text-sm font-medium" for="email">Email</label>
        <input id="email" v-model="form.email" type="email" required class="mt-1 w-full rounded border border-caramel/40 px-3 py-2" />
        <InputError :message="form.errors.email" />
      </div>
      <div>
        <label class="block text-sm font-medium" for="password">Password</label>
        <input id="password" v-model="form.password" type="password" required class="mt-1 w-full rounded border border-caramel/40 px-3 py-2" />
      </div>
      <label class="flex items-center gap-2 text-sm"><input v-model="form.remember" type="checkbox" /> Remember me</label>
      <button class="w-full rounded bg-accent text-white py-2 font-medium" :disabled="form.processing">Sign in</button>
    </form>
    <p class="mt-4 text-sm text-center">
      <TextLink v-if="canResetPassword" href="/forgot-password">Forgot password?</TextLink>
      <span v-if="canRegister"> · <TextLink href="/register">Create account</TextLink></span>
    </p>
  </AuthLayout>
</template>
