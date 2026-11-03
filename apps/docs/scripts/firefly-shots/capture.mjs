#!/usr/bin/env node
/**
 * Capture Firefly docs screenshots from the sample app (light + dark).
 * Usage:
 *   PLAYGROUND_URL=http://127.0.0.1:4001 node capture.mjs
 * Optional: SCENE=form-text-input,panel-chrome
 * Requires seeded firefly-app (demo@kona.test / password).
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
/** Outer padding around the subject, in CSS pixels (scaled for deviceScaleFactor). */
const padCss = Number(process.env.SHOT_PADDING || manifest.padding || 64);
const deviceScale = 2;
/** Sharp operate on device pixels. */
const pad = Math.round(padCss * deviceScale);

mkdirSync(outDir, { recursive: true });

const THEME_PAD = {
  light: { r: 248, g: 245, b: 240, alpha: 255 },
  dark: { r: 15, g: 23, b: 42, alpha: 255 },
};

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
    // Exact pathname match (avoid /admin matching /admin/project).
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

function clipFromBoxes(boxes, viewport, edgePad = 0) {
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
  // Only a tiny page-edge pad for open menus; real margin is added in writePadded
  // so neighboring form fields are not pulled into the crop.
  const x = Math.max(0, minX - edgePad);
  const y = Math.max(0, minY - edgePad);
  const width = Math.min(viewport.width - x, maxX - minX + edgePad * 2);
  const height = Math.min(viewport.height - y, maxY - minY + edgePad * 2);
  if (width < 4 || height < 4) return null;
  return { x, y, width, height };
}

async function captureElement(page, scene, theme) {
  const fullPage = scene.fullPage === true;
  if (fullPage) {
    const buf = await page.screenshot({ type: 'png', fullPage: false });
    await writePadded(buf, scene.name, theme);
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

  // Prefer the field chrome (label + control) so pads frame a complete input.
  if (!scene.closest && !scene.fullBleed) {
    const field = loc.locator(
      "xpath=ancestor-or-self::*[contains(@class,'ff-field') or contains(@class,'ff-component') or contains(@class,'ff-entry') or contains(@class,'ff-column') or @data-ff-docs][1]",
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
    const wrap = loc.locator(`xpath=ancestor::*[contains(@class,'ff-table-wrap') or contains(@class,'ff-table') or contains(@class,'ff-panel') or contains(@class,'ff-page')][1]`);
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

  // Paint a solid ring via box-shadow (included in element screenshots) so the
  // subject never sits flush against the PNG edge without swallowing neighbors.
  const ring = THEME_PAD[theme] || THEME_PAD.light;
  const ringCss = `rgba(${ring.r},${ring.g},${ring.b},1)`;
  await loc.evaluate(
    (el, { pad, color }) => {
      el.setAttribute('data-ff-shot-target', '1');
      el.style.setProperty('box-shadow', `0 0 0 ${pad}px ${color}`, 'important');
      el.style.setProperty('border-radius', getComputedStyle(el).borderRadius || '0.5rem', 'important');
    },
    { pad: padCss, color: ringCss },
  );

  // Component fields: element screenshot + solid pad (avoids neighboring fields).
  // Open menus / section composites: page clip + solid pad.
  let buf;
  try {
    if (!openUi && !scene.closest && !scene.fullBleed && scene.compose !== 'clip') {
      buf = await loc.screenshot({ type: 'png', timeout: 10000 });
    } else {
      const clip = clipFromBoxes(boxes, manifest.viewport, openUi ? 8 : padCss);
      if (!clip) {
        console.warn(`skip ${scene.name} (${theme}): empty box`);
        return false;
      }
      const maxH = scene.maxHeight || 1100;
      clip.height = Math.min(clip.height, maxH);
      buf = await page.screenshot({ type: 'png', clip });
    }
  } finally {
    await loc.evaluate((el) => {
      el.removeAttribute('data-ff-shot-target');
      el.style.removeProperty('box-shadow');
    }).catch(() => null);
  }
  await writePadded(buf, scene.name, theme);
  return true;
}

/**
 * Keep the clipped region intact and add a solid theme margin so controls
 * never sit flush against the PNG border.
 */
async function writePadded(buf, name, theme) {
  const bg = THEME_PAD[theme] || THEME_PAD.light;
  const out = await sharp(buf)
    .extend({
      top: pad,
      bottom: pad,
      left: pad,
      right: pad,
      background: bg,
    })
    .png()
    .toBuffer();
  const path = join(outDir, `${name}-${theme}.png`);
  await sharp(out).png().toFile(path);
  console.log('wrote', path.replace(root + '/', ''));
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
    deviceScaleFactor: 2,
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
