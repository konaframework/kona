<script setup>
import { Link } from '@inertiajs/vue3';
defineProps({ href: { type: String, required: true } });
</script>
<template><Link :href="href" class="text-caramel underline-offset-2 hover:underline"><slot /></Link></template>
