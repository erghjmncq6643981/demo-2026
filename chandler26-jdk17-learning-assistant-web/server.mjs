import { createServer } from 'node:http'
import { readFile } from 'node:fs/promises'
import { extname, join, normalize } from 'node:path'
import { fileURLToPath } from 'node:url'
import { buildHtml } from './scripts/build-html.mjs'

await buildHtml()

const root = fileURLToPath(new URL('.', import.meta.url))
const publicDir = join(root, 'public')
const port = Number(process.env.PORT || 5173)
const host = process.env.HOST || '127.0.0.1'

const types = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
}

const sentenceAudioCache = new Map()

async function handleSentenceAudio(req, res, url) {
  const text = (url.searchParams.get('text') || '').trim()
  const type = (url.searchParams.get('type') || 'us').toLowerCase()
  if (!text) {
    res.writeHead(400, { 'content-type': 'application/json; charset=utf-8' })
    res.end(JSON.stringify({ error: 'text is required' }))
    return
  }
  const cacheKey = `${type}:${text}`
  if (sentenceAudioCache.has(cacheKey)) {
    const buffer = sentenceAudioCache.get(cacheKey)
    res.writeHead(200, {
      'content-type': 'audio/mpeg',
      'content-length': buffer.byteLength,
      'cache-control': 'public, max-age=86400',
    })
    res.end(buffer)
    return
  }

  try {
    const lan = type === 'uk' ? 'uk' : 'en'
    const targetUrl = `https://fanyi.baidu.com/gettts?lan=${lan}&text=${encodeURIComponent(text)}&spd=3&source=web`
    const upstream = await fetch(targetUrl, {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36',
        'Referer': 'https://fanyi.baidu.com/',
      },
    })
    if (!upstream.ok) {
      res.writeHead(upstream.status, { 'content-type': 'application/json; charset=utf-8' })
      res.end(JSON.stringify({ error: 'upstream fetch failed' }))
      return
    }
    const arrayBuffer = await upstream.arrayBuffer()
    const buffer = Buffer.from(arrayBuffer)
    if (buffer.length > 500) {
      if (sentenceAudioCache.size >= 300) {
        const oldestKey = sentenceAudioCache.keys().next().value
        sentenceAudioCache.delete(oldestKey)
      }
      sentenceAudioCache.set(cacheKey, buffer)
    }
    res.writeHead(200, {
      'content-type': 'audio/mpeg',
      'content-length': buffer.byteLength,
      'cache-control': 'public, max-age=86400',
    })
    res.end(buffer)
  } catch (err) {
    res.writeHead(500, { 'content-type': 'application/json; charset=utf-8' })
    res.end(JSON.stringify({ error: err.message }))
  }
}

const server = createServer(async (req, res) => {
  let url
  try {
    url = new URL(req.url || '/', `http://${req.headers.host || `${host}:${port}`}`)
    if (url.pathname === '/api/sentence-audio') {
      await handleSentenceAudio(req, res, url)
      return
    }
    const pathname = url.pathname === '/' ? '/index.html' : decodeURIComponent(url.pathname)
    const filePath = normalize(join(publicDir, pathname))
    if (!filePath.startsWith(publicDir)) {
      res.writeHead(403)
      res.end('Forbidden')
      return
    }
    const content = await readFile(filePath)
    res.writeHead(200, {
      'content-type': types[extname(filePath)] || 'application/octet-stream',
      'cache-control': 'no-store',
    })
    res.end(content)
  } catch (error) {
    const pathname = url?.pathname || req.url || '/'
    const ext = extname(pathname)
    if (ext && ext !== '.html') {
      console.warn(`[404 Not Found] ${pathname} (${error.message})`)
      res.writeHead(404, { 'content-type': 'text/plain; charset=utf-8' })
      res.end(`Not Found: ${pathname}`)
      return
    }
    try {
      const content = await readFile(join(publicDir, 'index.html'))
      res.writeHead(200, { 'content-type': 'text/html; charset=utf-8', 'cache-control': 'no-store' })
      res.end(content)
    } catch {
      res.writeHead(500, { 'content-type': 'text/plain; charset=utf-8' })
      res.end('Internal Server Error')
    }
  }
})

server.listen(port, host, () => {
  console.log(`Learning assistant web: http://${host}:${port}`)
})
