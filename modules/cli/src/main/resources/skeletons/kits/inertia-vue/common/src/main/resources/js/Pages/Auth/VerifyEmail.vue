<script setup>
import { useForm } from '@inertiajs/vue3';
import AuthLayout from '../../Layouts/AuthLayout.vue';
defineProps({ status: String });
const form = useForm({});
</script>
<template>
  <AuthLayout title="Verify email">
    <p class="mb-4 text-sm">Thanks for signing up! Please verify your email address.</p>
    <p v-if="status" class="mb-4 text-sm text-green-800 break-all">{{ status }}</p>
    <button class="w-full rounded bg-accent text-white py-2 font-medium" :disabled="form.processing" @click="form.post('/email/verification-notification')">Resend verification email</button>
  </AuthLayout>
</template>
