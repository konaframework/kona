import { defineConfig } from '@playwright/test'

export default defineConfig({
    testDir: './playwright',
    timeout: 30_000,
    fullyParallel: true,
    reporter: 'list',
    use: {
        headless: true,
    },
})
