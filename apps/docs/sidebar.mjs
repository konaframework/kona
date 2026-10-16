/**
 * Starlight sidebar for Kona docs.
 *
 * Progressive path (simple → deeper):
 * Getting started → The basics → Crema → Digging deeper → Security → Database →
 * Frontend → Testing → Packages & tooling.
 *
 * Prefer regrouping here over mass page moves so existing slugs stay stable.
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
      label: 'The basics',
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
      label: 'Crema',
      items: [
        { label: 'Getting started', slug: 'docs/crema' },
        { label: 'View helper', slug: 'docs/crema/view-helper' },
        { label: 'Rendering & data', slug: 'docs/crema/rendering' },
        { label: 'Layouts & inheritance', slug: 'docs/crema/layouts' },
        { label: 'Control structures', slug: 'docs/crema/control-structures' },
        { label: 'Includes & partials', slug: 'docs/crema/includes' },
        { label: 'Components & slots', slug: 'docs/crema/components' },
        { label: 'Stacks', slug: 'docs/crema/stacks' },
        { label: 'Forms', slug: 'docs/crema/forms' },
        { label: 'Directive reference', slug: 'docs/crema/directives' },
      ],
    },
    {
      label: 'Digging deeper',
      items: [
        { label: 'Application & providers', slug: 'docs/concepts/application' },
        { label: 'Container', slug: 'docs/concepts/container' },
        { label: 'Config & env', slug: 'docs/concepts/config' },
        {
          label: 'grind',
          items: [
            { label: 'Overview', slug: 'docs/grind' },
            { label: 'Creating commands', slug: 'docs/grind/creating-commands' },
            { label: 'Running commands', slug: 'docs/grind/running-commands' },
            { label: 'Console IO & prompts', slug: 'docs/grind/console' },
            { label: 'Built-in commands', slug: 'docs/grind/commands' },
            { label: 'steam', slug: 'docs/steam' },
          ],
        },
        {
          label: 'Events',
          items: [
            { label: 'Overview', slug: 'docs/events' },
            { label: 'Dispatching & listeners', slug: 'docs/events/dispatching' },
            { label: 'Testing', slug: 'docs/events/testing' },
          ],
        },
        {
          label: 'Queues',
          items: [
            { label: 'Overview', slug: 'docs/queue' },
            { label: 'Jobs & dispatch', slug: 'docs/queue/jobs' },
            { label: 'Workers', slug: 'docs/queue/workers' },
            { label: 'Failed jobs', slug: 'docs/queue/failed-jobs' },
          ],
        },
        { label: 'Mail', slug: 'docs/mail' },
        { label: 'Notifications', slug: 'docs/notifications' },
        {
          label: 'Filesystem',
          items: [
            { label: 'Getting started', slug: 'docs/filesystem' },
            { label: 'Configuration', slug: 'docs/filesystem/configuration' },
            { label: 'File operations', slug: 'docs/filesystem/file-operations' },
            { label: 'Public disk & URLs', slug: 'docs/filesystem/urls' },
            { label: 'Testing', slug: 'docs/filesystem/testing' },
          ],
        },
        { label: 'Cache', slug: 'docs/cache' },
        { label: 'Redis', slug: 'docs/cache/redis' },
        { label: 'drip (HTTP client)', slug: 'docs/drip' },
        { label: 'Processes', slug: 'docs/processes' },
        { label: 'Concurrency', slug: 'docs/concurrency' },
        { label: 'Broadcasting', slug: 'docs/broadcasting' },
        { label: 'Search', slug: 'docs/search' },
        { label: 'Task scheduling', slug: 'docs/scheduling' },
        { label: 'Support utilities', slug: 'docs/support' },
      ],
    },
    {
      label: 'Security',
      items: [
        { label: 'Overview', slug: 'docs/security' },
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
      label: 'Database',
      items: [
        { label: 'Cadence overview', slug: 'docs/cadence' },
        { label: 'Database connections', slug: 'docs/cadence/database' },
        { label: 'Models', slug: 'docs/cadence/models' },
        { label: 'Query builder', slug: 'docs/cadence/query-builder' },
        { label: 'Relations', slug: 'docs/cadence/relations' },
        { label: 'Migrations', slug: 'docs/cadence/migrations' },
        { label: 'Seeders & factories', slug: 'docs/cadence/seeders-factories' },
        {
          label: 'Serialization, collections & events',
          slug: 'docs/cadence/serialization-events',
        },
      ],
    },
    {
      label: 'Frontend',
      items: [
        { label: 'Brew', slug: 'docs/brew' },
        { label: 'Inertia', slug: 'docs/inertia' },
        { label: 'Vite / asset bundling', slug: 'docs/assets/vite' },
        { label: 'Starter kits', slug: 'docs/starter-kits' },
      ],
    },
    {
      label: 'Testing',
      items: [
        { label: 'Getting started', slug: 'docs/testing' },
        { label: 'HTTP tests', slug: 'docs/testing/http-console' },
        { label: 'Console tests', slug: 'docs/testing/console' },
        { label: 'Database & fakes', slug: 'docs/testing/database-fakes' },
      ],
    },
    {
      label: 'Packages & tooling',
      items: [
        {
          label: 'Packages',
          items: [
            { label: 'Package development', slug: 'docs/packages' },
            { label: 'Installing packages', slug: 'docs/packages/install' },
          ],
        },
        {
          label: 'Language / editor',
          items: [
            { label: 'Getting started', slug: 'docs/language' },
            { label: 'Crema grammar', slug: 'docs/language/grammar' },
            { label: 'Language server (LSP)', slug: 'docs/language/lsp' },
            { label: 'VS Code & Cursor', slug: 'docs/language/vscode' },
            { label: 'IntelliJ', slug: 'docs/language/intellij' },
          ],
        },
      ],
    },
  ];
}
