/**
 * Starlight sidebar for Brew docs.
 *
 * Progressive path: Getting started → Essentials → Digging deeper →
 * Alpine & JavaScript → DX & testing → Examples.
 */
export async function buildSidebar() {
  return [
    {
      label: 'Getting started',
      items: [
        { label: 'Introduction', slug: 'guides/introduction' },
        { label: 'Requirements', slug: 'guides/requirements' },
        { label: 'Installation', slug: 'guides/installation' },
        { label: 'Your first component', slug: 'guides/first-component' },
      ],
    },
    {
      label: 'Essentials',
      items: [
        { label: 'Components', slug: 'essentials/components' },
        { label: 'Properties', slug: 'essentials/properties' },
        { label: 'Actions', slug: 'essentials/actions' },
        { label: 'Templates', slug: 'essentials/templates' },
        { label: 'Forms & validation', slug: 'essentials/forms' },
        { label: 'Lifecycle', slug: 'essentials/lifecycle' },
      ],
    },
    {
      label: 'Digging deeper',
      items: [
        { label: 'Nesting, islands & lazy', slug: 'advanced/nesting' },
        { label: 'Pages, navigate & URL', slug: 'advanced/pages' },
        { label: 'File uploads', slug: 'advanced/uploads' },
        { label: 'Events', slug: 'advanced/events' },
      ],
    },
    {
      label: 'Alpine & JavaScript',
      items: [
        { label: 'Alpine.js coupling', slug: 'js/alpine' },
        { label: 'JavaScript runtime', slug: 'js/runtime' },
      ],
    },
    {
      label: 'DX & testing',
      items: [
        { label: 'grind commands', slug: 'dx/grind' },
        { label: 'Testing', slug: 'dx/testing' },
      ],
    },
    {
      label: 'Examples',
      items: [
        { label: 'Counter', slug: 'examples/counter' },
        { label: 'Live search', slug: 'examples/live-search' },
        { label: 'Validated form', slug: 'examples/form' },
        { label: 'Nested components', slug: 'examples/nested' },
        { label: 'Full-page list', slug: 'examples/crud' },
        { label: 'File upload', slug: 'examples/upload' },
        { label: 'Alpine UI patterns', slug: 'examples/alpine-ui' },
      ],
    },
  ];
}
