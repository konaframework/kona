<script setup>
import { useForm } from '@inertiajs/vue3';
import AppLayout from '../../Layouts/AppLayout.vue';
const props = defineProps({ appearance: { type: String, default: 'system' } });
const form = useForm({ appearance: props.appearance });
</script>
<template>
  <AppLayout>
    <h1 class="text-2xl font-bold text-accent mb-6">Appearance</h1>
    <form class="space-y-3 max-w-sm" @submit.prevent="form.patch('/settings/appearance')">
      <label v-for="opt in ['light','dark','system']" :key="opt" class="flex items-center gap-2">
        <input v-model="form.appearance" type="radio" :value="opt" /> {{ opt }}
      </label>
      <button class="rounded bg-accent text-white px-4 py-2">Save</button>
    </form>
  </AppLayout>
</template>
