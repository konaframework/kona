<script setup>
import { Link, usePage } from '@inertiajs/vue3';
const page = usePage();
</script>
<template>
  <div class="min-h-screen bg-foam text-ink">
    <header class="border-b border-caramel/30 bg-white/70 backdrop-blur">
      <div class="max-w-5xl mx-auto px-4 py-3 flex items-center justify-between gap-4">
        <Link href="/dashboard" class="font-bold text-accent">{{ page.props.name }}</Link>
        <nav class="flex items-center gap-4 text-sm">
          <Link href="/settings/profile" class="hover:text-caramel">Profile</Link>
          <Link href="/settings/security" class="hover:text-caramel">Security</Link>
          <Link href="/settings/appearance" class="hover:text-caramel">Appearance</Link>
          <Link href="/logout" method="post" as="button" class="text-caramel">Log out</Link>
        </nav>
      </div>
    </header>
    <main class="max-w-5xl mx-auto px-4 py-8"><slot /></main>
  </div>
</template>
