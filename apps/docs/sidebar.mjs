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
        { label: 'Controllers', slug: 'docs/http/controllers' },
        { label: 'Request & response', slug: 'docs/http/request-response' },
        { label: 'Middleware', slug: 'docs/http/middleware' },
        { label: 'Validation', slug: 'docs/http/validation' },
        { label: 'API Resources', slug: 'docs/http/api-resources' },
        { label: 'URL generation', slug: 'docs/http/url-generation' },
        { label: 'Exceptions', slug: 'docs/http/exceptions' },
        { label: 'Logging', slug: 'docs/http/logging' },
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
      label: 'Crema',
      items: [
        { label: 'Getting started', slug: 'docs/crema' },
        { label: 'Rendering', slug: 'docs/crema/rendering' },
        { label: 'Layouts', slug: 'docs/crema/layouts' },
        { label: 'Control structures', slug: 'docs/crema/control-structures' },
        { label: 'Includes', slug: 'docs/crema/includes' },
        { label: 'Components', slug: 'docs/crema/components' },
        { label: 'Stacks', slug: 'docs/crema/stacks' },
      ],
    },
    {
      label: 'Brew',
      items: [
        { label: 'Getting started', slug: 'docs/brew' },
        { label: 'Components', slug: 'docs/brew/components' },
        { label: 'Events', slug: 'docs/brew/events' },
        { label: 'Forms', slug: 'docs/brew/forms' },
      ],
    },
    {
      label: 'Inertia & assets',
      items: [
        { label: 'Inertia', slug: 'docs/inertia' },
        { label: 'Vite / asset bundling', slug: 'docs/assets/vite' },
        { label: 'Starter kits', slug: 'docs/starter-kits' },
      ],
    },
    {
      label: 'Language / Editor',
      items: [
        { label: 'Getting started', slug: 'docs/language' },
        { label: 'Crema grammar', slug: 'docs/language/grammar' },
        { label: 'Language server (LSP)', slug: 'docs/language/lsp' },
        { label: 'VS Code & Cursor', slug: 'docs/language/vscode' },
        { label: 'IntelliJ', slug: 'docs/language/intellij' },
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
      label: 'Async & storage',
      items: [
        { label: 'Events', slug: 'docs/events' },
        { label: 'Queues', slug: 'docs/queue' },
        { label: 'Mail', slug: 'docs/mail' },
        { label: 'Notifications', slug: 'docs/notifications' },
        { label: 'Filesystem', slug: 'docs/filesystem' },
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
        { label: 'Console depth', slug: 'docs/grind/console' },
        { label: 'steam', slug: 'docs/steam' },
      ],
    },
    {
      label: 'Scheduling & packages',
      items: [
        { label: 'Task scheduling', slug: 'docs/scheduling' },
        { label: 'Packages', slug: 'docs/packages' },
      ],
    },
    {
      label: 'HTTP client & jobs',
      items: [
        { label: 'drip (HTTP client)', slug: 'docs/drip' },
        { label: 'Processes', slug: 'docs/processes' },
        { label: 'Concurrency', slug: 'docs/concurrency' },
      ],
    },
    {
      label: 'Testing',
      items: [
        { label: 'Getting started', slug: 'docs/testing' },
        { label: 'HTTP & console', slug: 'docs/testing/http-console' },
        { label: 'Database & fakes', slug: 'docs/testing/database-fakes' },
      ],
    },
    {
      label: 'Realtime & search',
      items: [
        { label: 'Broadcasting', slug: 'docs/broadcasting' },
        { label: 'Search', slug: 'docs/search' },
      ],
    },
  ];
}
