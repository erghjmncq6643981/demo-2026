import { createServer } from 'node:http'
import { readFile } from 'node:fs/promises'
import { extname, join, normalize } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('.', import.meta.url))
const publicDir = join(root, 'public')
const port = Number(process.env.PORT || 5174)
const host = process.env.HOST || '127.0.0.1'

const contentTypes = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.webp': 'image/webp',
}

const server = createServer(async (req, res) => {
  try {
    const url = new URL(req.url || '/', `http://${req.headers.host}`)
    const pathname = url.pathname === '/' ? '/index.html' : decodeURIComponent(url.pathname)
    const filePath = normalize(join(publicDir, pathname))

    if (!filePath.startsWith(publicDir)) {
      res.writeHead(403)
      res.end('Forbidden')
      return
    }

    const content = await readFile(filePath)
    res.writeHead(200, {
      'content-type': contentTypes[extname(filePath)] || 'application/octet-stream',
      'cache-control': 'no-store',
    })
    res.end(content)
  } catch {
    const content = await readFile(join(publicDir, 'index.html'))
    res.writeHead(200, {
      'content-type': 'text/html; charset=utf-8',
      'cache-control': 'no-store',
    })
    res.end(content)
  }
})

server.listen(port, host, () => {
  console.log(`Motivation web: http://${host}:${port}`)
})
