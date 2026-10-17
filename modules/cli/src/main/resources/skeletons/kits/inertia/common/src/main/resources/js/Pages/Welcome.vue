<script setup>
defineProps({
  title: { type: String, default: 'Welcome' },
  message: { type: String, default: '' },
});
</script>

<template>
  <main style="font-family: system-ui, sans-serif; padding: 2rem; max-width: 40rem;">
    <h1>{{ title }}</h1>
    <p v-if="message">{{ message }}</p>
  </main>
</template>
