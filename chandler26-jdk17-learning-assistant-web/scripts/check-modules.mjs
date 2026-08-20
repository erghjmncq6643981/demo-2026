import { access, readdir, readFile } from 'node:fs/promises'
import { dirname, extname, join, normalize, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const publicRoot = join(projectRoot, 'public')
const sourceRoot = join(publicRoot, 'src')
const featureRoot = join(sourceRoot, 'features')
const businessDomains = new Set(['ai', 'identity', 'learning', 'reading', 'system', 'task', 'vocabulary'])

async function javascriptFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true })
  const nested = await Promise.all(entries.map((entry) => {
    const path = join(directory, entry.name)
    return entry.isDirectory() ? javascriptFiles(path) : extname(path) === '.js' ? [path] : []
  }))
  return nested.flat()
}

async function validateBusinessDomainLayout() {
  const entries = await readdir(featureRoot, { withFileTypes: true })
  const unexpected = entries
    .filter((entry) => entry.isDirectory() && !businessDomains.has(entry.name))
    .map((entry) => entry.name)
  const filesAtFeatureRoot = entries
    .filter((entry) => entry.isFile() && extname(entry.name) === '.js')
    .map((entry) => entry.name)
  if (unexpected.length || filesAtFeatureRoot.length) {
    const details = [
      unexpected.length ? `unexpected domains: ${unexpected.join(', ')}` : '',
      filesAtFeatureRoot.length ? `files at feature root: ${filesAtFeatureRoot.join(', ')}` : '',
    ].filter(Boolean).join('; ')
    throw new Error(`Invalid frontend business-domain layout: ${details}`)
  }
}

function resolveImport(importer, specifier) {
  if (specifier.startsWith('/')) return join(publicRoot, specifier.slice(1))
  if (specifier.startsWith('.')) return resolve(dirname(importer), specifier)
  return null
}

await validateBusinessDomainLayout()
const files = [...await javascriptFiles(sourceRoot), join(publicRoot, 'app.js')]
const missing = []
for (const file of files) {
  const source = await readFile(file, 'utf8')
  const imports = source.matchAll(/(?:import|export)\s+(?:[^'";]+?\s+from\s+)?['"]([^'"]+)['"]/g)
  for (const match of imports) {
    const target = resolveImport(file, match[1])
    if (!target) continue
    try {
      await access(normalize(target))
    } catch {
      missing.push(`${relative(projectRoot, file)} -> ${match[1]}`)
    }
  }
}

if (missing.length) {
  console.error(`Unresolved frontend modules:\n${missing.join('\n')}`)
  process.exitCode = 1
} else {
  console.log(`Validated ${files.length} JavaScript modules.`)
}
