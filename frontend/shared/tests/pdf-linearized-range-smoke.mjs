import assert from 'node:assert/strict'
import {createReadStream} from 'node:fs'
import {stat} from 'node:fs/promises'
import {createServer} from 'node:http'
import {createRequire} from 'node:module'
import {argv, stdout} from 'node:process'
import {setTimeout as delay} from 'node:timers/promises'
import {pathToFileURL, URL} from 'node:url'

const pdfPath = argv[2]
if (!pdfPath) throw new Error('用法：node pdf-linearized-range-smoke.mjs <linearized-pdf>')

const file = await stat(pdfPath)
const requests = []
let transferredBytes = 0
let fullTransferredBytes = 0
let rangeTransferredBytes = 0
let inFlight = 0
let maxInFlight = 0

const server = createServer((request, response) => {
  const authorization = request.headers.authorization
  const range = request.headers.range
  inFlight += 1
  maxInFlight = Math.max(maxInFlight, inFlight)
  response.on('close', () => {
    inFlight = Math.max(0, inFlight - 1)
  })
  assert.equal(authorization, 'Bearer range-smoke-token')
  if (!range) {
    requests.push({start: 0, end: file.size - 1, status: 200})
    response.writeHead(200, {
      'Accept-Ranges': 'bytes',
      'Content-Length': file.size,
      'Content-Type': 'application/pdf'
    })
    const stream = createReadStream(pdfPath)
    stream.on('data', (chunk) => {
      transferredBytes += chunk.length
      fullTransferredBytes += chunk.length
    })
    response.on('close', () => stream.destroy())
    stream.pipe(response)
    return
  }
  assert.match(range, /^bytes=\d+-\d*$/)

  const match = /^bytes=(\d+)-(\d*)$/.exec(range)
  const start = Number(match[1])
  const requestedEnd = match[2] ? Number(match[2]) : file.size - 1
  const end = Math.min(requestedEnd, file.size - 1)
  requests.push({start, end, status: 206})
  response.writeHead(206, {
    'Accept-Ranges': 'bytes',
    'Content-Length': end - start + 1,
    'Content-Range': `bytes ${start}-${end}/${file.size}`,
    'Content-Type': 'application/pdf'
  })
  const stream = createReadStream(pdfPath, {start, end})
  stream.on('data', (chunk) => {
    transferredBytes += chunk.length
    rangeTransferredBytes += chunk.length
  })
  stream.pipe(response)
})

await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
const address = server.address()
assert.equal(typeof address, 'object')

const requireFromPc = createRequire(new URL('../../pc-web/package.json', import.meta.url))
const pdfjsPath = requireFromPc.resolve('pdfjs-dist/legacy/build/pdf.mjs')
const pdfjs = await import(pathToFileURL(pdfjsPath).href)
const {createPdfjsConcurrentRangeAdapter} = await import('../src/libs/pdf-concurrent-range.ts')
const adapter = createPdfjsConcurrentRangeAdapter(pdfjs)
const loadingTask = adapter.getDocument({
  url: `http://127.0.0.1:${address.port}/preview.pdf`,
  httpHeaders: {Authorization: 'Bearer range-smoke-token'},
  disableRange: false,
  disableStream: true,
  disableAutoFetch: true,
  rangeChunkSize: 262_144,
  useSystemFonts: true
})

try {
  const document = await loadingTask.promise
  assert.equal(document.numPages, 648)
  const documentBytes = transferredBytes
  const firstPage = await document.getPage(1)
  const firstPageProxyBytes = transferredBytes
  await firstPage.getOperatorList()
  firstPage.cleanup()
  const firstPageRenderBytes = transferredBytes
  assert.ok(firstPageRenderBytes < file.size, `首屏传输了 ${firstPageRenderBytes}/${file.size} bytes`)
  assert.ok(requests.length > 0)
  assert.ok(maxInFlight >= 2, `Range 应并行发出，实际峰值 ${maxInFlight}`)

  let previous = -1
  while (previous !== requests.length) {
    previous = requests.length
    await delay(150)
  }
  const settledBytes = transferredBytes
  await delay(500)
  assert.equal(transferredBytes, settledBytes, '空闲阶段不应继续自动预取')

  const targetPage = await document.getPage(300)
  const page300ProxyBytes = transferredBytes
  await targetPage.getOperatorList()
  targetPage.cleanup()
  assert.ok(transferredBytes < file.size, `跳页后传输了 ${transferredBytes}/${file.size} bytes`)
  assert.ok(requests.some((request) => request.status === 206), '应观察到 Range/206 请求')

  stdout.write(`${JSON.stringify({
    fileBytes: file.size,
    pageCount: document.numPages,
    documentBytes,
    firstPageProxyBytes,
    firstPageRenderBytes,
    page300ProxyBytes,
    page300RenderBytes: transferredBytes,
    fullTransferredBytes,
    rangeTransferredBytes,
    maxInFlight,
    requestCount: requests.length,
    fullRequests: requests.filter((request) => request.status === 200).length,
    rangeRequests: requests.filter((request) => request.status === 206).length,
    firstRanges: requests.filter((request) => request.status === 206).slice(0, 5),
    lastRanges: requests.filter((request) => request.status === 206).slice(-5)
  })}\n`)
} finally {
  await loadingTask.destroy()
  await new Promise((resolve, reject) => server.close((error) => error ? reject(error) : resolve()))
}
