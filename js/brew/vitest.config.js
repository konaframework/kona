import { defineConfig } from 'vitest/config'

export default defineConfig({
    test: {
        environment: 'jsdom',
        environmentOptions: { jsdom: { pretendToBeVisual: true, url: 'http://localhost/' } },
        include: ['test/**/*.test.js'],
        benchmark: { include: ['bench/**/*.bench.js'] },
        coverage: {
            provider: 'v8',
            include: ['src/**/*.js'],
            reporter: ['text', 'json-summary', 'lcov'],
            thresholds: { lines: 100, branches: 100, functions: 100, statements: 100 },
        },
    },
})
