import assert from 'node:assert/strict'
import {open, stat} from 'node:fs/promises'
import {createRequire} from 'node:module'
import {argv, stdout} from 'node:process'
import {pathToFileURL, URL} from 'node:url'

const pdfPath = argv[2]
if (!pdfPath) throw new Error('用法：node pdf-range-transport-diagnostic.mjs <linearized-pdf>')
const rangeChunkSize = Number(argv[3] ?? 262_144)

const file = await stat(pdfPath)
const handle = await open(pdfPath, 'r')
const initialData = new Uint8Array(Math.min(rangeChunkSize, file.size))
await handle.read(initialData, 0, initialData.length, 0)

const requireFromPc = createRequire(new URL('../../pc-web/package.json', import.meta.url))
const pdfjsPath = requireFromPc.resolve('pdfjs-dist/legacy/build/pdf.mjs')
const pdfjs = await import(pathToFileURL(pdfjsPath).href)
const requests = []
let transferredBytes = initialData.length

class FileRangeTransport extends pdfjs.PDFDataRangeTransport {
  constructor() {
    super(file.size, initialData, true)
  }

  requestDataRange(begin, end) {
    const data = new Uint8Array(end - begin)
    void handle.read(data, 0, data.length, begin).then(({bytesRead}) => {
      const result = data.subarray(0, bytesRead)
      requests.push({begin, end: begin + bytesRead})
      transferredBytes += bytesRead
      this.onDataRange(begin, result)
    })
  }
}

const transport = new FileRangeTransport()
const loadingTask = pdfjs.getDocument({
  range: transport,
  length: file.size,
  disableRange: false,
  disableStream: true,
  disableAutoFetch: true,
  rangeChunkSize,
  useSystemFonts: true
})

try {
  const document = await loadingTask.promise
  assert.equal(document.numPages, 648)
  const documentBytes = transferredBytes
  const page = await document.getPage(1)
  const firstPageProxyBytes = transferredBytes
  await page.getOperatorList()
  page.cleanup()
  stdout.write(`${JSON.stringify({
    fileBytes: file.size,
    rangeChunkSize,
    documentBytes,
    firstPageProxyBytes,
    firstPageRenderBytes: transferredBytes,
    requestCount: requests.length,
    firstRanges: requests.slice(0, 5),
    lastRanges: requests.slice(-5)
  })}\n`)
} finally {
  await loadingTask.destroy()
  await handle.close()
}
