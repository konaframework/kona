/**
 * Pre-create starlight-versions archives listed in VERSION_SLUGS.
 * The plugin only archives one *new* version per build; seeding dirs
 * (plus versions/*.json) lets a build succeed with those slugs configured.
 *
 * Default archive: `main` (Dev tip). When freezing a prior major (e.g. after
 * 1.0 ships), add that major slug here and in astro.config.mjs, then re-run.
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
/** Dev tip archive. Add frozen majors (e.g. `'0'`) when a new major becomes Latest. */
const VERSION_SLUGS = ['main'];
const SKIP = new Set(['main', 'es', 'fr', 'zh-cn']);

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
