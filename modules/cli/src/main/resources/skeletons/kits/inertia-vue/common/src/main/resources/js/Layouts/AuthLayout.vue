<script setup>
defineProps({ title: { type: String, default: '' } });
</script>
<template>
  <div class="min-h-screen bg-foam text-ink flex items-center justify-center p-6">
    <div class="w-full max-w-md">
      <h1 v-if="title" class="text-2xl font-bold text-accent mb-6 text-center">{{ title }}</h1>
      <slot />
    </div>
  </div>
</template>
