<script setup>
import { useForm } from '@inertiajs/vue3';
import AuthLayout from '../../Layouts/AuthLayout.vue';
import InputError from '../../Components/InputError.vue';
import TextLink from '../../Components/TextLink.vue';
const form = useForm({ password: '' });
const submit = () => form.post('/user/confirm-password');
</script>
<template>
  <AuthLayout title="Confirm password">
    <form class="space-y-4" @submit.prevent="submit">
      <div>
        <label class="block text-sm font-medium" for="password">Password</label>
        <input id="password" v-model="form.password" type="password" class="mt-1 w-full rounded border border-caramel/40 px-3 py-2" />
        <InputError :message="form.errors.password" />
      </div>
      <button class="w-full rounded bg-accent text-white py-2 font-medium" :disabled="form.processing">Continue</button>
    </form>
    <p class="mt-4 text-sm text-center"><TextLink href="/login">Back to sign in</TextLink></p>
  </AuthLayout>
</template>
