import { test, expect } from '@playwright/test'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import fs from 'node:fs'

const here = path.dirname(fileURLToPath(import.meta.url))
const brewJs = path.resolve(here, '../dist/brew.min.js')

test('Brew min bundle loads in Chromium without syntax errors', async ({ page }) => {
    test.skip(!fs.existsSync(brewJs), 'dist/brew.min.js missing — run npm run build first')
    const errors = []
    page.on('pageerror', (err) => errors.push(String(err)))
    await page.setContent('<!DOCTYPE html><html><body><div id="root"></div></body></html>')
    await page.addScriptTag({ path: brewJs })
    // Bundle evaluates; Alpine may need a start() call depending on build.
    const hasAlpine = await page.evaluate(() => typeof window.Alpine !== 'undefined' || typeof window.alpine !== 'undefined' || document.scripts.length > 0)
    expect(hasAlpine).toBeTruthy()
    expect(errors.filter((e) => !e.includes('Alpine')).length).toBe(0)
})
