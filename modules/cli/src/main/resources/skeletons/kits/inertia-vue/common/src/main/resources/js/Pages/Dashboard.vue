<script setup>
import AppLayout from '../Layouts/AppLayout.vue';
import { usePage } from '@inertiajs/vue3';
const page = usePage();
</script>
<template>
  <AppLayout>
    <h1 class="text-3xl font-bold text-accent">Dashboard</h1>
    <p class="mt-2 opacity-80">Signed in as {{ page.props.auth?.user?.email }}</p>
  </AppLayout>
</template>
