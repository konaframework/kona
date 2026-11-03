#!/usr/bin/env node
/**
 * Capture Firefly docs screenshots from the sample app (light + dark).
 *
 * Padding matches the surface the subject sits on: sample the parent
 * background (white card → white pad; panel surface → that color), then
 * extend the element crop. Never invent a theme cream ring.
 *
 * Usage:
 *   PLAYGROUND_URL=http://127.0.0.1:4001 node capture.mjs
 * Optional: SCENE=form-text-input,section-panel-chrome
 */
import { chromium } from 'playwright';
import sharp from 'sharp';
import { readFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const root = join(__dirname, '../..');
const outDir = join(root, 'public/images/firefly');
const manifest = JSON.parse(readFileSync(join(__dirname, 'scenes.json'), 'utf8'));
const baseUrl = process.env.PLAYGROUND_URL || manifest.baseUrl;
const only = process.env.SCENE ? new Set(process.env.SCENE.split(',')) : null;
/** CSS pixels of margin around the subject. */
const padCss = Number(process.env.SHOT_PADDING || manifest.padding || 28);
const deviceScale = 2;
const padPx = Math.round(padCss * deviceScale);

mkdirSync(outDir, { recursive: true });

async function login(page) {
  await page.goto(`${baseUrl}${manifest.login.path}`, { waitUntil: 'domcontentloaded', timeout: 60000 });
  const email = page.locator('input[name="email"], input[type="email"]').first();
  const password = page.locator('input[name="password"], input[type="password"]').first();
  if (await email.count()) {
    await email.fill(manifest.login.email);
    await password.fill(manifest.login.password);
    await Promise.all([
      page.waitForURL(/\/(admin|dashboard)/, { timeout: 20000 }).catch(() => null),
      page.locator('button[type="submit"], input[type="submit"]').first().click(),
    ]);
    await page.waitForTimeout(800);
  }
}

async function setTheme(page, theme) {
  await page.evaluate((t) => {
    try {
      localStorage.setItem('ff-theme', t);
    } catch (_) {}
    const dark = t === 'dark';
    document.documentElement.classList.toggle('dark', dark);
    document.documentElement.dataset.theme = dark ? 'dark' : 'light';
    document.querySelectorAll('[data-ff-theme-toggle]').forEach((b) => b.setAttribute('data-ff-theme', t));
  }, theme);
  await page.waitForTimeout(120);
}

function scenePath(scene) {
  return scene.page || manifest.page;
}

function pathMatches(currentUrl, path) {
  try {
    const u = new URL(currentUrl);
    const want = path.split('?')[0];
    return u.pathname === want || u.pathname === `${want}/`;
  } catch (_) {
    return false;
  }
}

async function gotoScenePage(page, scene) {
  const path = scenePath(scene);
  const url = `${baseUrl}${path}`;
  if (!pathMatches(page.url(), path)) {
    await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 90000 });
  } else if (scene.reload) {
    await page.reload({ waitUntil: 'domcontentloaded', timeout: 90000 }).catch(() => null);
  }
  await page.waitForTimeout(scene.waitMs || 600);
  if (scene.expand) {
    for (const label of scene.expand) {
      const el = page.locator('summary, .ff-section-summary, button').filter({ hasText: new RegExp(label, 'i') }).first();
      if (await el.count()) await el.click().catch(() => null);
    }
    await page.waitForTimeout(200);
  }
}

async function runBefore(page, scene) {
  for (const step of scene.before || []) {
    if (step === 'open-select') {
      const root = page.locator(scene.selector).first();
      await root.scrollIntoViewIfNeeded().catch(() => null);
      const trigger = root.locator('[data-ff-combobox], button, .ff-select-trigger, [role="combobox"]').first();
      if (await trigger.count()) await trigger.click({ force: true });
      else await root.click({ force: true });
      await page.waitForTimeout(280);
    } else if (step === 'open-date') {
      const root = page.locator(scene.selector).first();
      await root.scrollIntoViewIfNeeded().catch(() => null);
      const trigger = root.locator('button, input, .ff-date-trigger').first();
      if (await trigger.count()) await trigger.click({ force: true });
      await page.waitForTimeout(280);
    } else if (step === 'open-dialog') {
      const trigger = page.locator(scene.dialogTrigger || '[data-ff-dialog-open], [data-ff-action]').first();
      if (await trigger.count()) await trigger.click({ force: true });
      await page.waitForTimeout(350);
    } else if (typeof step === 'object' && step.click) {
      const el = page.locator(step.click).first();
      if (await el.count()) await el.click({ force: true });
      await page.waitForTimeout(step.waitMs || 250);
    }
  }
}

function clipFromBoxes(boxes, viewport, edgePad) {
  const valid = boxes.filter(Boolean);
  if (!valid.length) return null;
  let minX = Infinity;
  let minY = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;
  for (const b of valid) {
    minX = Math.min(minX, b.x);
    minY = Math.min(minY, b.y);
    maxX = Math.max(maxX, b.x + b.width);
    maxY = Math.max(maxY, b.y + b.height);
  }
  const x = Math.max(0, minX - edgePad);
  const y = Math.max(0, minY - edgePad);
  const width = Math.min(viewport.width - x, maxX - minX + edgePad * 2);
  const height = Math.min(viewport.height - y, maxY - minY + edgePad * 2);
  if (width < 4 || height < 4) return null;
  return { x, y, width, height };
}

/**
 * Color of the surface behind the subject.
 * Prefer painted ancestors (.ff-section / cards) over the page body cream.
 * Fall back to a 1×1 page sample just inside the nearest opaque parent.
 */
async function sampleSurface(page, loc) {
  const fromCss = await loc.evaluate((el) => {
    const parse = (bg) => {
      if (!bg || bg === 'transparent' || bg === 'rgba(0, 0, 0, 0)') return null;
      const m = bg.match(/rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)(?:\s*,\s*([\d.]+))?\s*\)/i);
      if (!m) return null;
      const a = m[4] == null ? 1 : Number(m[4]);
      if (a < 0.5) return null;
      return {
        r: Math.round(Number(m[1])),
        g: Math.round(Number(m[2])),
        b: Math.round(Number(m[3])),
        alpha: 255,
      };
    };
    const prefer = (n) => {
      const cls = (n.className || '').toString();
      return /ff-section(?!-)|ff-card|ff-widget-card|ff-table-card|ff-panel-card/.test(cls);
    };
    let preferred = null;
    let first = null;
    let n = el;
    while (n && n.nodeType === 1) {
      const bg = parse(getComputedStyle(n).backgroundColor);
      if (bg) {
        if (!first) first = bg;
        if (prefer(n)) {
          preferred = bg;
          break;
        }
      }
      n = n.parentElement;
    }
    return preferred || first;
  });
  if (fromCss) return fromCss;

  // Pixel sample: one CSS pixel above the subject (still on the same card when inset).
  const box = await loc.boundingBox();
  if (!box) return { r: 255, g: 255, b: 255, alpha: 255 };
  const x = Math.min(manifest.viewport.width - 1, Math.max(0, box.x + Math.min(8, box.width / 2)));
  const y = Math.min(manifest.viewport.height - 1, Math.max(0, box.y - 2));
  const buf = await page.screenshot({
    type: 'png',
    clip: { x, y, width: 1, height: 1 },
  });
  const { data } = await sharp(buf).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
  return { r: data[0], g: data[1], b: data[2], alpha: 255 };
}

async function captureElement(page, scene, theme) {
  if (scene.fullPage === true) {
    const buf = await page.screenshot({ type: 'png', fullPage: false });
    await writeShot(buf, scene.name, theme, null);
    return true;
  }

  let loc = page.locator(scene.selector);
  if (!(await loc.count())) {
    console.warn(`skip ${scene.name} (${theme}): missing ${scene.selector}`);
    return false;
  }
  const visible = loc.locator('visible=true');
  loc = (await visible.count()) > 0 ? visible.first() : loc.first();

  try {
    await loc.scrollIntoViewIfNeeded({ timeout: 5000 });
  } catch (_) {}
  await runBefore(page, scene);

  // Prefer the nearest field/entry/column chrome (ff-component controls or ff-field).
  if (!scene.closest && !scene.fullBleed) {
    const field = loc.locator(
      "xpath=ancestor-or-self::*[contains(@class,'ff-text-input') or contains(@class,'ff-field') or contains(@class,'ff-entry') or contains(@class,'ff-column') or contains(@class,'ff-component')][1]",
    );
    if (await field.count()) loc = field.first();
  }

  const boxes = [await loc.boundingBox()];
  const openUi =
    (scene.before || []).includes('open-select') ||
    (scene.before || []).includes('open-date') ||
    (scene.before || []).includes('open-dialog');
  if ((scene.before || []).includes('open-select')) {
    const panel = page.locator('[role="listbox"], .ff-combobox-panel, .ff-select-panel').first();
    if (await panel.count()) boxes.push(await panel.boundingBox());
  }
  if ((scene.before || []).includes('open-date')) {
    const panel = page.locator('.ff-date-panel, .ff-picker-panel, [data-ff-date-panel]').first();
    if (await panel.count()) boxes.push(await panel.boundingBox());
  }
  if ((scene.before || []).includes('open-dialog')) {
    const panel = page.locator('.ff-dialog, [data-ff-dialog], [role="dialog"]').first();
    if (await panel.count()) boxes.push(await panel.boundingBox());
  }
  if (scene.closest) {
    const wrap = loc.locator(
      `xpath=ancestor::*[contains(@class,'ff-table-wrap') or contains(@class,'ff-table') or contains(@class,'ff-panel') or contains(@class,'ff-page')][1]`,
    );
    if (await wrap.count()) {
      boxes.length = 0;
      boxes.push(await wrap.first().boundingBox());
    } else {
      const alt = page.locator(scene.closest).filter({ has: loc }).first();
      if (await alt.count()) {
        boxes.length = 0;
        boxes.push(await alt.boundingBox());
      }
    }
  }

  const surface = await sampleSurface(page, loc);
  const edgePad = scene.pad != null ? Number(scene.pad) : padCss;

  let buf;
  if (openUi || scene.closest || scene.compose === 'clip' || scene.fullBleed) {
    // Large/composited shots: clip real page pixels around the subject.
    const clip = clipFromBoxes(boxes, manifest.viewport, edgePad);
    if (!clip) {
      console.warn(`skip ${scene.name} (${theme}): empty box`);
      return false;
    }
    clip.height = Math.min(clip.height, scene.maxHeight || 1100);
    buf = await page.screenshot({ type: 'png', clip });
    await writeShot(buf, scene.name, theme, null);
  } else {
    // Field shots: capture the control, then pad with its real surface color
    // (white card → white) so neighbors are not pulled into the crop.
    buf = await loc.screenshot({ type: 'png', timeout: 10000 });
    await writeShot(buf, scene.name, theme, surface);
  }
  return true;
}

async function writeShot(buf, name, theme, surface) {
  let out = buf;
  if (surface) {
    out = await sharp(buf)
      .extend({
        top: padPx,
        bottom: padPx,
        left: padPx,
        right: padPx,
        background: surface,
      })
      .png()
      .toBuffer();
  }
  const path = join(outDir, `${name}-${theme}.png`);
  await sharp(out).png().toFile(path);
  console.log('wrote', path.replace(root + '/', ''), surface ? `pad=${JSON.stringify(surface)}` : 'page-clip');
}

async function injectSwatchCss(page) {
  await page.addStyleTag({
    content: `
      .ff-panel .ff-color-column { display:inline-flex; align-items:center; gap:.35rem; min-height:1.5rem; }
      .ff-panel .ff-color-column > span.ff-color {
        display:inline-block; width:1.25rem; height:1.25rem; border-radius:9999px;
        border:1px solid color-mix(in srgb, var(--ff-card-border) 85%, transparent); flex-shrink:0;
      }
      .ff-panel .ff-colors { display:inline-flex; flex-wrap:wrap; gap:.35rem; align-items:center; }
      .ff-panel .ff-swatch {
        display:inline-block; width:1.5rem; height:1.5rem; border-radius:.4rem;
        border:1px solid var(--ff-card-border);
      }
    `,
  });
}

async function main() {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    viewport: manifest.viewport,
    deviceScaleFactor: deviceScale,
  });
  const page = await context.newPage();
  await login(page);

  const scenes = manifest.scenes.filter((s) => !only || only.has(s.name));
  for (const theme of ['light', 'dark']) {
    await setTheme(page, theme);
    let lastPage = null;
    for (const scene of scenes) {
      try {
        const path = scenePath(scene);
        if (path !== lastPage || scene.expand || scene.reload) {
          await gotoScenePage(page, scene);
          await injectSwatchCss(page);
          lastPage = path;
        }
        await setTheme(page, theme);
        await captureElement(page, scene, theme);
      } catch (err) {
        console.warn(`fail ${scene.name} (${theme}):`, err.message);
      }
      await page.keyboard.press('Escape').catch(() => null);
    }
  }
  await browser.close();
  console.log(`done — ${scenes.length} scenes × light/dark → ${outDir}`);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
