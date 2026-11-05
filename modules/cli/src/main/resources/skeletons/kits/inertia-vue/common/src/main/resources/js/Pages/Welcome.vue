<script setup>
import { Link } from '@inertiajs/vue3';
defineProps({ title: { type: String, default: 'Welcome' }, message: { type: String, default: '' } });
</script>
<template>
  <main class="min-h-screen bg-foam text-ink p-8 max-w-xl">
    <h1 class="text-3xl font-bold text-accent">{{ title }}</h1>
    <p v-if="message" class="mt-4 opacity-90">{{ message }}</p>
    <div class="mt-8 flex gap-4 text-sm">
      <Link href="/login" class="text-caramel">Sign in</Link>
      <Link href="/register" class="text-caramel">Register</Link>
      <Link href="/dashboard" class="text-caramel">Dashboard</Link>
    </div>
  </main>
</template>
