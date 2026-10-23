import { build } from 'esbuild'
import { mkdirSync, readFileSync, writeFileSync, copyFileSync } from 'node:fs'
import { join, dirname, basename } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = dirname(fileURLToPath(import.meta.url))
const resources = join(root, '..', '..', 'modules', 'wire', 'src', 'main', 'resources', 'org', 'konaframework', 'wire')
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
    build({ ...shared, format: 'iife', outfile: join(resources, 'wire.js') }),
    build({ ...shared, format: 'iife', minify: true, sourcemap: 'linked', sourcesContent: false, outfile: join(resources, 'wire.min.js') }),
    build({ ...shared, format: 'esm', outfile: join(resources, 'wire.esm.js') }),
    build({ ...shared, format: 'iife', minify: true, outfile: join(resources, 'wire.csp.min.js') }),
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
        process.stderr.write(`Wire bundles are stale (${stale.map(file => basename(file.path)).join(', ')}). Run \`npm run build\` in js/wire and commit the result.\n`)
        process.exit(1)
    }
} else {
    mkdirSync(resources, { recursive: true })
    mkdirSync(dist, { recursive: true })

    files.forEach(file => {
        writeFileSync(file.path, file.contents)
        writeFileSync(join(dist, basename(file.path)), file.contents)
    })
    const aliases = [
        ['wire.js', 'brew.js'],
        ['wire.min.js', 'brew.min.js'],
        ['wire.min.js.map', 'brew.min.js.map'],
        ['wire.esm.js', 'brew.esm.js'],
        ['wire.csp.min.js', 'brew.csp.min.js'],
    ]
    aliases.forEach(([from, to]) => {
        const src = join(resources, from)
        try {
            copyFileSync(src, join(resources, to))
            copyFileSync(src, join(dist, to))
        } catch {
            // map file may be missing until minify emit
        }
    })
}
