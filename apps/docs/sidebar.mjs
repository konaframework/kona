/**
 * Starlight sidebar for Kona docs.
 * Expand groups as milestones land; keep guides ahead of reference.
 */
export async function buildSidebar() {
  return [
    {
      label: 'Getting started',
      items: [
        { label: 'Introduction', slug: 'docs/guides/introduction' },
        { label: 'Installation', slug: 'docs/guides/installation' },
        { label: 'Your first app', slug: 'docs/guides/first-app' },
        { label: 'Kotlin and Java', slug: 'docs/guides/kotlin-java' },
      ],
    },
    {
      label: 'Concepts',
      items: [
        { label: 'Application & providers', slug: 'docs/concepts/application' },
        { label: 'Container', slug: 'docs/concepts/container' },
        { label: 'Config & env', slug: 'docs/concepts/config' },
      ],
    },
    {
      label: 'HTTP',
      items: [
        { label: 'Routing', slug: 'docs/http/routing' },
        { label: 'Request & response', slug: 'docs/http/request-response' },
        { label: 'Middleware', slug: 'docs/http/middleware' },
        { label: 'Validation', slug: 'docs/http/validation' },
        { label: 'Exceptions', slug: 'docs/http/exceptions' },
        { label: 'Kernel & server', slug: 'docs/http/kernel-server' },
      ],
    },
    {
      label: 'Security',
      items: [
        { label: 'Session', slug: 'docs/security/session' },
        { label: 'Authentication', slug: 'docs/security/authentication' },
        { label: 'Authorization', slug: 'docs/security/authorization' },
        { label: 'CSRF protection', slug: 'docs/security/csrf' },
        { label: 'Encryption', slug: 'docs/security/encryption' },
        { label: 'CORS & security headers', slug: 'docs/security/cors-headers' },
        { label: 'Rate limiting', slug: 'docs/security/rate-limiting' },
      ],
    },
    {
      label: 'Extract',
      items: [
        { label: 'Overview', slug: 'docs/extract' },
        { label: 'Models', slug: 'docs/extract/models' },
        { label: 'Query builder', slug: 'docs/extract/query-builder' },
        { label: 'Relations', slug: 'docs/extract/relations' },
        { label: 'Migrations', slug: 'docs/extract/migrations' },
        { label: 'Seeders & factories', slug: 'docs/extract/seeders-factories' },
        { label: 'Serialization & events', slug: 'docs/extract/serialization-events' },
      ],
    },
    {
      label: 'Cache & Redis',
      items: [
        { label: 'Cache', slug: 'docs/cache' },
        { label: 'Redis', slug: 'docs/cache/redis' },
      ],
    },
    {
      label: 'Support',
      items: [
        { label: 'Utilities', slug: 'docs/support' },
      ],
    },
    {
      label: 'Security reference',
      items: [
        { label: 'Overview', slug: 'docs/security' },
        { label: 'Encryption, CORS, rate limit', slug: 'docs/security/encryption-cors-rate-limit' },
      ],
    },
    {
      label: 'grind',
      items: [
        { label: 'Overview', slug: 'docs/grind' },
        { label: 'new / serve / make', slug: 'docs/grind/commands' },
      ],
    },
  ];
}
