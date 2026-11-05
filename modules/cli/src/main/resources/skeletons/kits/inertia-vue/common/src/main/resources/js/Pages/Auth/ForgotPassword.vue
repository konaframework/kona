<script setup>
import { useForm } from '@inertiajs/vue3';
import AuthLayout from '../../Layouts/AuthLayout.vue';
import InputError from '../../Components/InputError.vue';
import TextLink from '../../Components/TextLink.vue';
const form = useForm({ email: '' });
const submit = () => form.post('/forgot-password');
</script>
<template>
  <AuthLayout title="Forgot password">
    <p v-if="$page.props.status" class="mb-4 text-sm text-green-800">{{ $page.props.status }}</p>
    <form class="space-y-4" @submit.prevent="submit">
      <div>
        <label class="block text-sm font-medium" for="email">Email</label>
        <input id="email" v-model="form.email" type="email" class="mt-1 w-full rounded border border-caramel/40 px-3 py-2" />
        <InputError :message="form.errors.email" />
      </div>
      <button class="w-full rounded bg-accent text-white py-2 font-medium" :disabled="form.processing">Continue</button>
    </form>
    <p class="mt-4 text-sm text-center"><TextLink href="/login">Back to sign in</TextLink></p>
  </AuthLayout>
</template>
