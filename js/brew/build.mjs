import { build } from 'esbuild'
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { join, dirname, basename } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = dirname(fileURLToPath(import.meta.url))
// Monorepo: js/brew → modules/brew resources (sister layout was js/ → brew/)
const resources = join(root, '..', '..', 'modules', 'brew', 'src', 'main', 'resources', 'org', 'konaframework', 'brew')
const dist = join(root, 'dist')
const check = process.argv.includes('--check')

const shared = {
    entryPoints: [join(root, 'src', 'index.js')],
    bundle: true,
    target: ['es2020'],
    legalComments: 'none',
    logLevel: 'warning',
    write: false,
}

const results = await Promise.all([
    build({ ...shared, format: 'iife', outfile: join(resources, 'brew.js') }),
    build({ ...shared, format: 'iife', minify: true, sourcemap: 'linked', sourcesContent: false, outfile: join(resources, 'brew.min.js') }),
    build({ ...shared, format: 'esm', outfile: join(resources, 'brew.esm.js') }),
    // CSP-safe build: same minify bundle under a dedicated name (Alpine CSP apps load this).
    build({ ...shared, format: 'iife', minify: true, outfile: join(resources, 'brew.csp.min.js') }),
])

const files = results.flatMap(result => result.outputFiles)

if (check) {
    let stale = files.filter(file => {
        try {
            return readFileSync(file.path, 'utf8') !== file.text
        } catch {
            return true
        }
    })

    if (stale.length > 0) {
        process.stderr.write(`Brew bundles are stale (${stale.map(file => basename(file.path)).join(', ')}). Run \`npm run build\` in js/brew and commit the result.\n`)
        process.exit(1)
    }
} else {
    mkdirSync(resources, { recursive: true })
    mkdirSync(dist, { recursive: true })

    files.forEach(file => {
        writeFileSync(file.path, file.contents)
        writeFileSync(join(dist, basename(file.path)), file.contents)
    })
}
