// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import almasixTheme from '@almasix/starlight-theme';
import { buildSidebar } from './sidebar.mjs';

// Production: https://docs.konaframework.org (PUBLIC_BASE_PATH=/). GH Pages mirror uses PUBLIC_BASE_PATH=/kona/
const base = process.env.PUBLIC_BASE_PATH || '/';
const site = process.env.PUBLIC_SITE_URL || 'https://docs.konaframework.org';
const ogImage = new URL('og.png', site.endsWith('/') ? site : `${site}/`).href;
const ogImageAlt = 'Kona — Laravel-inspired, batteries-included web framework for the JVM';
const sidebar = await buildSidebar();

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
        light: './src/assets/kona-mark.svg',
        dark: './src/assets/kona-mark-dark.svg',
        alt: 'Kona',
        replacesTitle: true,
      },
      social: [
        {
          icon: 'github',
          label: 'GitHub',
          href: 'https://github.com/konaframework/kona',
        },
      ],
      plugins: [
        almasixTheme({
          github: 'konaframework/kona',
          lightbox: false,
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
            content: '#A67C52',
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
      ],
      sidebar,
    }),
  ],
});
