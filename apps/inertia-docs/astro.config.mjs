// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import almasixTheme from '@almasix/starlight-theme';
import { buildSidebar } from './sidebar.mjs';

// Production: https://inertia.konaframework.org (PUBLIC_BASE_PATH=/). GH Pages mirror: PUBLIC_BASE_PATH=/kona-inertia/
const base = process.env.PUBLIC_BASE_PATH || '/';
const site = process.env.PUBLIC_SITE_URL || 'https://inertia.konaframework.org';
const origin = site.replace(/\/$/, '');
const normalizedBase = base === '/' ? '/' : `/${base.replace(/^\/|\/$/g, '')}/`;
const ogImage = `${origin}${normalizedBase}og.svg`;
const ogImageAlt = 'Inertia for Kona — server-driven page props for modern frontends';
const sidebar = await buildSidebar();

export default defineConfig({
  site,
  base,
  trailingSlash: 'ignore',
  integrations: [
    starlight({
      title: 'Inertia',
      description:
        'Inertia for Kona — serve modern single-page frontends with server-driven page props. Kotlin-first, Java first-class.',
      favicon: '/favicon.svg',
      logo: {
        light: './src/assets/inertia-logo.svg',
        dark: './src/assets/inertia-logo-dark.svg',
        alt: 'Inertia',
        replacesTitle: true,
      },
      social: [
        {
          icon: 'github',
          label: 'GitHub',
          href: 'https://github.com/konaframework/kona-inertia',
        },
      ],
      plugins: [
        almasixTheme({
          github: 'konaframework/kona-inertia',
          lightbox: false,
        }),
      ],
      customCss: ['./src/styles/custom.css'],
      components: {
        SiteTitle: './src/components/SiteTitle.astro',
      },
      expressiveCode: {
        themes: ['monokai'],
        styleOverrides: {
          codeBackground: '#272822',
          codeForeground: '#f8f8f2',
          frames: {
            editorBackground: '#272822',
            terminalBackground: '#272822',
          },
        },
      },
      editLink: {
        baseUrl: 'https://github.com/konaframework/kona-inertia/edit/main/apps/docs/',
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
            name: 'twitter:card',
            content: 'summary_large_image',
          },
        },
      ],
      sidebar,
    }),
  ],
});
