/**
 * Pre-create starlight-versions archives for `0` and `main`.
 * The plugin only archives one *new* version per build; seeding both dirs
 * (plus versions/*.json) lets a single build succeed with both configured.
 *
 * Run from apps/docs: `node scripts/seed-versions.mjs`
 */
import { cpSync, existsSync, mkdirSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { buildSidebar } from '../sidebar.mjs';

const root = fileURLToPath(new URL('..', import.meta.url));
const docsDir = join(root, 'src/content/docs');
const versionsDir = join(root, 'src/content/versions');
const VERSION_SLUGS = ['0', 'main'];
const SKIP = new Set(['0', 'main', 'es', 'fr', 'zh-cn']);

const sidebar = await buildSidebar();

mkdirSync(versionsDir, { recursive: true });

for (const slug of VERSION_SLUGS) {
  const dest = join(docsDir, slug);
  if (existsSync(dest)) {
    console.log(`skip existing archive: ${slug}`);
  } else {
    mkdirSync(dest, { recursive: true });
    for (const name of readdirSync(docsDir)) {
      if (SKIP.has(name) || VERSION_SLUGS.includes(name)) continue;
      const src = join(docsDir, name);
      cpSync(src, join(dest, name), { recursive: true });
    }
    console.log(`seeded content archive: ${slug}`);
  }

  const cfgPath = join(versionsDir, `${slug}.json`);
  if (!existsSync(cfgPath)) {
    writeFileSync(
      cfgPath,
      JSON.stringify({ sidebar, excluded: [] }, null, 2) + '\n',
      'utf8',
    );
    console.log(`wrote ${cfgPath}`);
  }
}

// Drop accidental nested version dirs inside archives (if re-run after partial)
for (const slug of VERSION_SLUGS) {
  for (const nested of VERSION_SLUGS) {
    const nestedPath = join(docsDir, slug, nested);
    if (existsSync(nestedPath)) {
      rmSync(nestedPath, { recursive: true, force: true });
      console.log(`removed nested ${slug}/${nested}`);
    }
  }
}

console.log('done');
