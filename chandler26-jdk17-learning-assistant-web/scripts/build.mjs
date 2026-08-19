import { cp, mkdir, rm, stat } from 'node:fs/promises'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import './check-modules.mjs'

if (process.exitCode) process.exit(process.exitCode)

const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const source = join(projectRoot, 'public')
const target = join(projectRoot, 'dist')
await rm(target, { recursive: true, force: true })
await mkdir(target, { recursive: true })
await cp(source, target, { recursive: true })
const index = await stat(join(target, 'index.html'))
if (!index.isFile()) throw new Error('Frontend build is missing index.html')
console.log(`Static frontend built at ${target}`)
