import { readdir, readFile, writeFile } from 'node:fs/promises'
import { dirname, extname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const templatesRoot = join(projectRoot, 'templates')
const publicRoot = join(projectRoot, 'public')
const indexTemplatePath = join(templatesRoot, 'index.html')
const targetIndexPath = join(publicRoot, 'index.html')

async function replaceAsync(str, regex, asyncFn) {
  const matches = []
  str.replace(regex, (match, p1, offset) => {
    matches.push({ match, p1, offset })
    return match
  })
  let result = ''
  let lastIndex = 0
  for (const { match, p1, offset } of matches) {
    result += str.slice(lastIndex, offset)
    result += await asyncFn(match, p1)
    lastIndex = offset + match.length
  }
  result += str.slice(lastIndex)
  return result
}

export async function htmlTemplateFiles(directory = templatesRoot) {
  const entries = await readdir(directory, { withFileTypes: true })
  const nested = await Promise.all(entries.map((entry) => {
    const path = join(directory, entry.name)
    return entry.isDirectory() ? htmlTemplateFiles(path) : extname(path) === '.html' ? [path] : []
  }))
  return nested.flat()
}

export async function assembleHtml() {
  const templateSource = await readFile(indexTemplatePath, 'utf8')
  const assembled = await replaceAsync(templateSource, /<!--\s*include:\s*([^\s]+)\s*-->/g, async (_, includePath) => {
    const fullPath = join(templatesRoot, includePath)
    const content = await readFile(fullPath, 'utf8')
    return content.trimEnd()
  })
  return assembled.endsWith('\n') ? assembled : `${assembled}\n`
}

export async function buildHtml() {
  const content = await assembleHtml()
  await writeFile(targetIndexPath, content, 'utf8')
  return content
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  await buildHtml()
  console.log('Compiled templates/index.html -> public/index.html')
}
