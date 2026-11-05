import { build } from 'esbuild'
import { mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = dirname(fileURLToPath(import.meta.url))
mkdirSync(join(root, 'dist'), { recursive: true })

await build({
    entryPoints: [join(root, 'src/index.js')],
    bundle: true,
    format: 'esm',
    outfile: join(root, 'dist/echo.esm.js'),
    target: ['es2020'],
    legalComments: 'none',
    logLevel: 'warning',
})

await build({
    entryPoints: [join(root, 'src/index.js')],
    bundle: true,
    format: 'iife',
    globalName: 'KonaEcho',
    outfile: join(root, 'dist/echo.js'),
    target: ['es2020'],
    legalComments: 'none',
    logLevel: 'warning',
})

console.log('Built @konaframework/echo')
