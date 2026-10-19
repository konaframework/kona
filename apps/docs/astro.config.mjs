// @ts-check
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import almasixTheme from '@almasix/starlight-theme';
import starlightVersions from 'starlight-versions';
import { buildSidebar } from './sidebar.mjs';

// Production: https://docs.konaframework.org (PUBLIC_BASE_PATH=/). GH Pages mirror uses PUBLIC_BASE_PATH=/kona/
const base = process.env.PUBLIC_BASE_PATH || '/';
const site = process.env.PUBLIC_SITE_URL || 'https://docs.konaframework.org';
const ogImage = new URL('og.png', site.endsWith('/') ? site : `${site}/`).href;
const ogImageAlt = 'Kona — Laravel-inspired, batteries-included web framework for the JVM';
const sidebar = await buildSidebar();

/** Major release line from repo `kona.version` (e.g. 0.1.1 → `0.x`). */
function releaseLineLabel() {
  const gradleProps = readFileSync(
    join(dirname(fileURLToPath(import.meta.url)), '../../gradle.properties'),
    'utf8',
  );
  const version = gradleProps.match(/^kona\.version=(.+)$/m)?.[1]?.trim() ?? '0.0.0';
  const major = version.split('.')[0] || '0';
  return `${major}.x (Latest)`;
}

// https://astro.build/config
export default defineConfig({
  site,
  base,
  trailingSlash: 'ignore',
  integrations: [
    starlight({
      title: 'Kona',
      description:
        'Laravel-inspired, batteries-included web framework for the JVM. Kotlin-first, Java first-class.',
      favicon: '/favicon.svg',
      logo: {
        light: './src/assets/kona-logo.svg',
        dark: './src/assets/kona-logo-dark.svg',
        alt: 'Kona',
        replacesTitle: true,
      },
      defaultLocale: 'root',
      locales: {
        root: {
          label: 'English',
          lang: 'en',
        },
        es: {
          label: 'Español',
          lang: 'es',
        },
        fr: {
          label: 'Français',
          lang: 'fr',
        },
        'zh-cn': {
          label: '简体中文',
          lang: 'zh-CN',
        },
      },
      social: [
        {
          icon: 'github',
          label: 'GitHub',
          href: 'https://github.com/konaframework/kona',
        },
      ],
      plugins: [
        // Theme first (owns Header / ThemeSelect). Version select via headerExtras.
        almasixTheme({
          github: 'konaframework/kona',
          lightbox: false,
          headerExtras: './src/components/VersionSelect.astro',
        }),
        // Current (default, unversioned URLs) = published release line.
        // Archive `main` = in-progress tip. Seed with scripts/seed-versions.mjs
        starlightVersions({
          current: {
            label: releaseLineLabel(),
          },
          versions: [{ slug: 'main', label: 'Dev' }],
        }),
      ],
      customCss: ['./src/styles/custom.css'],
      components: {
        SiteTitle: './src/components/SiteTitle.astro',
      },
      editLink: {
        baseUrl: 'https://github.com/konaframework/kona/edit/main/apps/docs/',
      },
      head: [
        {
          tag: 'meta',
          attrs: {
            name: 'theme-color',
            content: '#DF8723',
          },
        },
        {
          tag: 'meta',
          attrs: {
            name: 'robots',
            content: 'index, follow, max-image-preview:large, max-snippet:-1, max-video-preview:-1',
          },
        },
        {
          tag: 'meta',
          attrs: {
            property: 'og:image',
            content: ogImage,
          },
        },
        {
          tag: 'meta',
          attrs: {
            property: 'og:image:alt',
            content: ogImageAlt,
          },
        },
        {
          tag: 'meta',
          attrs: {
            name: 'twitter:image',
            content: ogImage,
          },
        },
        {
          tag: 'script',
          attrs: {
            src: `${base === '/' ? '' : base.replace(/\/$/, '')}/scripts/gh-chip-hydrate.js`,
            defer: true,
          },
        },
      ],
      sidebar,
    }),
  ],
});
