import { access, readdir, readFile } from 'node:fs/promises'
import { dirname, extname, join, normalize, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { assembleHtml, htmlTemplateFiles } from './build-html.mjs'

const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const publicRoot = join(projectRoot, 'public')
const templatesRoot = join(projectRoot, 'templates')
const sourceRoot = join(publicRoot, 'src')
const featureRoot = join(sourceRoot, 'features')
const businessDomains = new Set(['ai', 'identity', 'learning', 'reading', 'system', 'task', 'vocabulary'])
const MAX_MODULE_LINES = 1000
const MAX_CSS_LINES = 1000
const MAX_HTML_LINES = 1000

async function javascriptFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true })
  const nested = await Promise.all(entries.map((entry) => {
    const path = join(directory, entry.name)
    return entry.isDirectory() ? javascriptFiles(path) : extname(path) === '.js' ? [path] : []
  }))
  return nested.flat()
}

async function cssFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true })
  const nested = await Promise.all(entries.map((entry) => {
    const path = join(directory, entry.name)
    return entry.isDirectory() ? cssFiles(path) : extname(path) === '.css' ? [path] : []
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
  const cleanSpecifier = specifier.split('?')[0]
  if (cleanSpecifier.startsWith('/')) return join(publicRoot, cleanSpecifier.slice(1))
  if (cleanSpecifier.startsWith('.')) return resolve(dirname(importer), cleanSpecifier)
  return null
}

await validateBusinessDomainLayout()
const files = [...await javascriptFiles(sourceRoot), join(publicRoot, 'app.js')]
const missing = []
const oversized = []
for (const file of files) {
  const source = await readFile(file, 'utf8')
  const lineCount = source.split(/\r?\n/).length
  if (lineCount > MAX_MODULE_LINES) {
    oversized.push(`${relative(projectRoot, file)} (${lineCount} lines)`)
  }
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

const stylesRoot = join(publicRoot, 'styles')
const cssFileList = [join(publicRoot, 'styles.css'), ...await cssFiles(stylesRoot)]
const missingCssImports = []
const oversizedCss = []
for (const file of cssFileList) {
  const source = await readFile(file, 'utf8')
  const lineCount = source.split(/\r?\n/).length
  if (lineCount > MAX_CSS_LINES) {
    oversizedCss.push(`${relative(projectRoot, file)} (${lineCount} lines)`)
  }
  const cssImports = source.matchAll(/@import\s+['"]([^'"]+)['"]/g)
  for (const match of cssImports) {
    const target = resolveImport(file, match[1])
    if (!target) continue
    try {
      await access(normalize(target))
    } catch {
      missingCssImports.push(`${relative(projectRoot, file)} -> ${match[1]}`)
    }
  }
}

const templateFileList = await htmlTemplateFiles(templatesRoot)
const missingHtmlIncludes = []
const oversizedHtml = []
for (const file of templateFileList) {
  const source = await readFile(file, 'utf8')
  const lineCount = source.split(/\r?\n/).length
  if (lineCount > MAX_HTML_LINES) {
    oversizedHtml.push(`${relative(projectRoot, file)} (${lineCount} lines)`)
  }
  const includes = source.matchAll(/<!--\s*include:\s*([^\s]+)\s*-->/g)
  for (const match of includes) {
    const target = join(templatesRoot, match[1])
    try {
      await access(normalize(target))
    } catch {
      missingHtmlIncludes.push(`${relative(projectRoot, file)} -> ${match[1]}`)
    }
  }
}

let htmlOutOfSync = false
try {
  const assembledIndex = await assembleHtml()
  const currentIndex = await readFile(join(publicRoot, 'index.html'), 'utf8')
  if (assembledIndex.trim() !== currentIndex.trim()) {
    htmlOutOfSync = true
  }
} catch {
  htmlOutOfSync = true
}

if (
  missing.length ||
  oversized.length ||
  missingCssImports.length ||
  oversizedCss.length ||
  missingHtmlIncludes.length ||
  oversizedHtml.length ||
  htmlOutOfSync
) {
  if (missing.length) console.error(`Unresolved frontend modules:\n${missing.join('\n')}`)
  if (oversized.length) console.error(`Frontend modules exceed ${MAX_MODULE_LINES} lines; split by responsibility:\n${oversized.join('\n')}`)
  if (missingCssImports.length) console.error(`Unresolved CSS @import targets:\n${missingCssImports.join('\n')}`)
  if (oversizedCss.length) console.error(`CSS files exceed ${MAX_CSS_LINES} lines; split by responsibility:\n${oversizedCss.join('\n')}`)
  if (missingHtmlIncludes.length) console.error(`Unresolved HTML include targets:\n${missingHtmlIncludes.join('\n')}`)
  if (oversizedHtml.length) console.error(`HTML templates exceed ${MAX_HTML_LINES} lines; split by responsibility:\n${oversizedHtml.join('\n')}`)
  if (htmlOutOfSync) console.error('public/index.html is out of sync with templates/. Run "node scripts/build-html.mjs" to regenerate.')
  process.exitCode = 1
} else {
  console.log(`Validated ${files.length} JavaScript modules (limit ${MAX_MODULE_LINES} lines), ${cssFileList.length} CSS stylesheets (limit ${MAX_CSS_LINES} lines), and ${templateFileList.length} HTML templates (limit ${MAX_HTML_LINES} lines).`)
}
