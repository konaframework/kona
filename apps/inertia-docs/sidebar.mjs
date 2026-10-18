/**
 * Starlight sidebar for Inertia docs.
 *
 * Progressive path: Getting started → Essentials → Digging deeper →
 * DX & testing → Examples.
 */
export async function buildSidebar() {
  return [
    {
      label: 'Getting started',
      items: [
        { label: 'Introduction', slug: 'guides/introduction' },
        { label: 'Requirements', slug: 'guides/requirements' },
        { label: 'Installation', slug: 'guides/installation' },
        { label: 'Your first page', slug: 'guides/first-page' },
        { label: 'Frontend clients', slug: 'guides/frontend' },
      ],
    },
    {
      label: 'Essentials',
      items: [
        { label: 'Rendering pages', slug: 'essentials/rendering' },
        { label: 'Requests & responses', slug: 'essentials/requests-responses' },
        { label: 'Shared data', slug: 'essentials/shared-data' },
        { label: 'Middleware', slug: 'essentials/middleware' },
        { label: 'Root template', slug: 'essentials/root-template' },
        { label: 'Configuration', slug: 'essentials/configuration' },
      ],
    },
    {
      label: 'Digging deeper',
      items: [
        { label: 'Asset versioning', slug: 'advanced/versioning' },
        { label: 'External redirects', slug: 'advanced/redirects' },
        { label: 'Vite & assets', slug: 'advanced/vite' },
        { label: 'Kotlin & Java API', slug: 'advanced/kotlin-java' },
        { label: 'Facade & manager', slug: 'advanced/facade-manager' },
      ],
    },
    {
      label: 'DX & testing',
      items: [
        { label: 'Starter kit', slug: 'dx/starter-kit' },
        { label: 'Testing', slug: 'dx/testing' },
      ],
    },
    {
      label: 'Examples',
      items: [
        { label: 'Welcome page', slug: 'examples/welcome' },
        { label: 'Shared flash props', slug: 'examples/shared-flash' },
        { label: 'Version conflict', slug: 'examples/version-conflict' },
        { label: 'Location redirect', slug: 'examples/location' },
      ],
    },
  ];
}
