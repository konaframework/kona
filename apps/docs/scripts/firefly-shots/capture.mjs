#!/usr/bin/env node
/**
 * Capture Firefly docs screenshots from the sample Playground (light + dark).
 * Usage:
 *   PLAYGROUND_URL=http://127.0.0.1:4000 node capture.mjs
 * Requires a seeded firefly-app with demo@kona.test / password.
 */
import { chromium } from 'playwright';
import sharp from 'sharp';
import { readFileSync, mkdirSync, existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const root = join(__dirname, '../..');
const outDir = join(root, 'public/images/firefly');
const manifest = JSON.parse(readFileSync(join(__dirname, 'scenes.json'), 'utf8'));
const baseUrl = process.env.PLAYGROUND_URL || manifest.baseUrl;
const only = process.env.SCENE ? new Set(process.env.SCENE.split(',')) : null;
const pad = manifest.padding ?? 12;

mkdirSync(outDir, { recursive: true });

async function login(page) {
  await page.goto(`${baseUrl}${manifest.login.path}`, { waitUntil: 'networkidle' });
  const email = page.locator('input[name="email"], input[type="email"]').first();
  const password = page.locator('input[name="password"], input[type="password"]').first();
  if (await email.count()) {
    await email.fill(manifest.login.email);
    await password.fill(manifest.login.password);
    await Promise.all([
      page.waitForURL(/\/(admin|dashboard)/, { timeout: 20000 }).catch(() => null),
      page.locator('button[type="submit"], input[type="submit"]').first().click(),
    ]);
  }
}

async function setTheme(page, theme) {
  await page.evaluate((t) => {
    try {
      localStorage.setItem('ff-theme', t);
    } catch (_) {}
    const dark = t === 'dark' || (t === 'system' && matchMedia('(prefers-color-scheme: dark)').matches);
    document.documentElement.classList.toggle('dark', dark);
    document.documentElement.dataset.theme = dark ? 'dark' : 'light';
    document.querySelectorAll('[data-ff-theme-toggle]').forEach((b) => b.setAttribute('data-ff-theme', t));
  }, theme);
  await page.waitForTimeout(150);
}

async function runBefore(page, scene) {
  const steps = scene.before || [];
  for (const step of steps) {
    if (step === 'open-select') {
      const root = page.locator(scene.selector).first();
      await root.scrollIntoViewIfNeeded();
      const trigger = root.locator('[data-ff-combobox], button, .ff-select-trigger, [role="combobox"]').first();
      if (await trigger.count()) await trigger.click({ force: true });
      else await root.click({ force: true });
      await page.waitForTimeout(250);
    } else if (step === 'open-date') {
      const root = page.locator(scene.selector).first();
      await root.scrollIntoViewIfNeeded();
      const trigger = root.locator('button, input, .ff-date-trigger').first();
      if (await trigger.count()) await trigger.click({ force: true });
      await page.waitForTimeout(250);
    }
  }
}

async function captureElement(page, scene, theme) {
  let loc = page.locator(scene.selector);
  const visible = loc.locator('visible=true');
  if ((await visible.count()) > 0) loc = visible.first();
  else loc = loc.first();
  if (!(await page.locator(scene.selector).count())) {
    console.warn(`skip ${scene.name} (${theme}): missing ${scene.selector}`);
    return false;
  }
  try {
    await loc.scrollIntoViewIfNeeded({ timeout: 5000 });
  } catch (_) {
    // Still try a clipped page screenshot of the bounding box if any.
  }
  await runBefore(page, scene);
  // Prefer the open popover/calendar if present so open scenes include the panel.
  let target = loc;
  if ((scene.before || []).includes('open-select')) {
    const panel = page.locator('[role="listbox"], .ff-combobox-panel, .ff-select-panel').first();
    if (await panel.count()) {
      const boxA = await loc.boundingBox();
      const boxB = await panel.boundingBox();
      if (boxA && boxB) {
        const x = Math.min(boxA.x, boxB.x) - pad;
        const y = Math.min(boxA.y, boxB.y) - pad;
        const w = Math.max(boxA.x + boxA.width, boxB.x + boxB.width) - Math.min(boxA.x, boxB.x) + pad * 2;
        const h = Math.max(boxA.y + boxA.height, boxB.y + boxB.height) - Math.min(boxA.y, boxB.y) + pad * 2;
        const buf = await page.screenshot({
          type: 'png',
          clip: {
            x: Math.max(0, x),
            y: Math.max(0, y),
            width: Math.min(w, manifest.viewport.width),
            height: Math.min(h, 900),
          },
        });
        await writeTrimmed(buf, scene.name, theme);
        return true;
      }
    }
  }
  if ((scene.before || []).includes('open-date')) {
    const panel = page.locator('.ff-date-panel, .ff-picker-panel, [data-ff-date-panel]').first();
    if (await panel.count()) {
      const boxA = await loc.boundingBox();
      const boxB = await panel.boundingBox();
      if (boxA && boxB) {
        const x = Math.min(boxA.x, boxB.x) - pad;
        const y = Math.min(boxA.y, boxB.y) - pad;
        const w = Math.max(boxA.x + boxA.width, boxB.x + boxB.width) - Math.min(boxA.x, boxB.x) + pad * 2;
        const h = Math.max(boxA.y + boxA.height, boxB.y + boxB.height) - Math.min(boxA.y, boxB.y) + pad * 2;
        const buf = await page.screenshot({
          type: 'png',
          clip: {
            x: Math.max(0, x),
            y: Math.max(0, y),
            width: Math.min(w, manifest.viewport.width),
            height: Math.min(h, 900),
          },
        });
        await writeTrimmed(buf, scene.name, theme);
        return true;
      }
    }
  }
  if (scene.closest) {
    const wrap = loc.locator(`xpath=ancestor::*[contains(@class,'ff-table-wrap') or contains(@class,'ff-table')][1]`);
    if (await wrap.count()) target = wrap;
    else {
      const alt = page.locator(scene.closest).filter({ has: loc }).first();
      if (await alt.count()) target = alt;
    }
  }
  const buf = await target.screenshot({ type: 'png', timeout: 10000 });
  await writeTrimmed(buf, scene.name, theme);
  return true;
}

async function writeTrimmed(buf, name, theme) {
  const trimmed = await sharp(buf)
    .trim({ threshold: 8 })
    .extend({ top: pad, bottom: pad, left: pad, right: pad, background: { r: 0, g: 0, b: 0, alpha: 0 } })
    .png()
    .toBuffer();
  const path = join(outDir, `${name}-${theme}.png`);
  await sharp(trimmed).png().toFile(path);
  console.log('wrote', path.replace(root + '/', ''));
}

async function main() {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    viewport: manifest.viewport,
    deviceScaleFactor: 2,
  });
  const page = await context.newPage();
  await login(page);
  await page.goto(`${baseUrl}${manifest.page}`, { waitUntil: 'networkidle' });
  await page.waitForSelector('.ff-panel', { timeout: 30000 });
  // Expand collapsible editors section if present.
  const editors = page.locator('summary, .ff-section-summary').filter({ hasText: /Editors/i }).first();
  if (await editors.count()) {
    await editors.click().catch(() => null);
    await page.waitForTimeout(200);
  }

  const scenes = manifest.scenes.filter((s) => !only || only.has(s.name));
  for (const theme of ['light', 'dark']) {
    await setTheme(page, theme);
    await page.reload({ waitUntil: 'networkidle' });
    await page.waitForSelector('.ff-panel', { timeout: 30000 });
    const editors2 = page.locator('summary, .ff-section-summary').filter({ hasText: /Editors/i }).first();
    if (await editors2.count()) await editors2.click().catch(() => null);
    for (const scene of scenes) {
      try {
        await captureElement(page, scene, theme);
      } catch (err) {
        console.warn(`fail ${scene.name} (${theme}):`, err.message);
      }
      // Dismiss open panels between scenes.
      await page.keyboard.press('Escape').catch(() => null);
    }
  }
  await browser.close();
  // Refresh README table rows for new scenes.
  console.log(`done — ${scenes.length} scenes × light/dark → ${outDir}`);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
