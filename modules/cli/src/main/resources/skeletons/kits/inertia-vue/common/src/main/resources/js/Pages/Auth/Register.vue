<script setup>
import { useForm } from '@inertiajs/vue3';
import AuthLayout from '../../Layouts/AuthLayout.vue';
import InputError from '../../Components/InputError.vue';
import TextLink from '../../Components/TextLink.vue';
const form = useForm({ name: '', email: '', password: '', password_confirmation: '' });
const submit = () => form.post('/register');
</script>
<template>
  <AuthLayout title="Create account">
    <form class="space-y-4" @submit.prevent="submit">
      <div>
        <label class="block text-sm font-medium" for="name">Name</label>
        <input id="name" v-model="form.name" type="text" class="mt-1 w-full rounded border border-caramel/40 px-3 py-2" />
        <InputError :message="form.errors.name" />
      </div>
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
        <InputError :message="form.errors.password_confirmation" />
      </div>
      <button class="w-full rounded bg-accent text-white py-2 font-medium" :disabled="form.processing">Continue</button>
    </form>
    <p class="mt-4 text-sm text-center"><TextLink href="/login">Back to sign in</TextLink></p>
  </AuthLayout>
</template>
