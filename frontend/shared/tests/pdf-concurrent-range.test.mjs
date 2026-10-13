import assert from 'node:assert/strict';
import test from 'node:test';
import {
  createPdfjsConcurrentRangeAdapter,
  parsePdfContentRangeTotal,
  PDF_RANGE_LOOKAHEAD_CHUNKS,
  PDF_RANGE_MAX_CONCURRENT,
  PdfConcurrentRangeLoader,
  pdfLastChunkRange,
  pdfLookaheadByteRanges
} from '../src/libs/pdf-concurrent-range.ts';
import {PDF_RANGE_CHUNK_SIZE} from '../src/libs/pdf-page-session.ts';

const CHUNK = 8;
const FILE_SIZE = 64;

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function createFakePdfFetch(fileSize = FILE_SIZE, latencyMs = 25) {
  let inFlight = 0;
  let maxInFlight = 0;
  const requests = [];
  const fetchImpl = async (_url, init = {}) => {
    const range = init.headers?.Range;
    const match = /^bytes=(\d+)-(\d+)$/.exec(range ?? '');
    assert.ok(match, `缺少 Range：${range}`);
    const begin = Number(match[1]);
    const last = Math.min(Number(match[2]), fileSize - 1);
    inFlight += 1;
    maxInFlight = Math.max(maxInFlight, inFlight);
    requests.push({begin, last, authorization: init.headers?.Authorization});
    await delay(latencyMs);
    inFlight -= 1;
    const bytes = new Uint8Array(last - begin + 1);
    return new Response(bytes, {
      status: 206,
      headers: {
        'Content-Range': `bytes ${begin}-${last}/${fileSize}`,
        'Content-Type': 'application/pdf'
      }
    });
  };
  return {
    fetchImpl,
    requests,
    maxInFlight: () => maxInFlight,
    inFlight: () => inFlight
  };
}

test('解析 Content-Range 总长度，并规划尾块与后续切片', () => {
  assert.equal(PDF_RANGE_CHUNK_SIZE, 262_144);
  assert.equal(PDF_RANGE_MAX_CONCURRENT, 4);
  assert.equal(PDF_RANGE_LOOKAHEAD_CHUNKS, 3);
  assert.equal(parsePdfContentRangeTotal('bytes 0-7/64'), 64);
  assert.equal(parsePdfContentRangeTotal('bytes 56-63/64'), 64);
  assert.equal(parsePdfContentRangeTotal(null), undefined);
  assert.deepEqual(pdfLastChunkRange(64, 8), {begin: 56, end: 64});
  assert.equal(pdfLastChunkRange(8, 8), undefined);
  assert.deepEqual(pdfLookaheadByteRanges(8, 64, 8, 3), [
    {begin: 8, end: 16},
    {begin: 16, end: 24},
    {begin: 24, end: 32}
  ]);
  assert.equal(pdfLookaheadByteRanges(0, 262_144 * 20, 262_144, PDF_RANGE_LOOKAHEAD_CHUNKS).length, 3);
});

test('探测首块后并行预取尾块和后续切片，空闲后停止', async () => {
  const fake = createFakePdfFetch();
  const loader = new PdfConcurrentRangeLoader({
    url: '/preview.pdf',
    httpHeaders: {Authorization: 'Bearer token'},
    chunkSize: CHUNK,
    maxConcurrent: PDF_RANGE_MAX_CONCURRENT,
    lookaheadChunks: PDF_RANGE_LOOKAHEAD_CHUNKS,
    fetchImpl: fake.fetchImpl
  });

  const probed = await loader.probe();
  assert.equal(probed.length, FILE_SIZE);
  assert.equal(probed.initialData.byteLength, CHUNK);
  await delay(40);
  assert.ok(fake.maxInFlight() >= 2, `探测后应并行发出请求，实际峰值 ${fake.maxInFlight()}`);
  assert.ok(fake.requests.some((request) => request.begin === 56));
  assert.equal(fake.requests[0].authorization, 'Bearer token');

  const settled = fake.requests.length;
  await delay(80);
  assert.equal(fake.requests.length, settled, '空闲后不得继续预取');
  loader.abort();
});

test('PDF.js 再要已预取切片时不重复发请求，并立即交付', async () => {
  const fake = createFakePdfFetch();
  const loader = new PdfConcurrentRangeLoader({
    url: '/preview.pdf',
    chunkSize: CHUNK,
    fetchImpl: fake.fetchImpl
  });
  await loader.probe();
  await delay(80);
  const cachedRangeRequests = fake.requests.filter((request) => request.begin === 8 && request.last === 15);
  assert.equal(cachedRangeRequests.length, 1);
  const delivered = [];
  loader.requestRange(8, 16, (begin, chunk) => {
    delivered.push({begin, size: chunk.byteLength});
  });
  await delay(10);
  assert.deepEqual(delivered, [{begin: 8, size: CHUNK}]);
  assert.equal(fake.requests.filter((request) => request.begin === 8 && request.last === 15).length, 1);
  loader.abort();
});

test('销毁会取消未完成的 Range 请求', async () => {
  const fake = createFakePdfFetch(FILE_SIZE, 80);
  const loader = new PdfConcurrentRangeLoader({
    url: '/preview.pdf',
    chunkSize: CHUNK,
    fetchImpl: fake.fetchImpl
  });
  const probing = loader.probe();
  await delay(10);
  loader.abort();
  await assert.rejects(probing, /abort|AbortError|已取消/i);
});

test('并发适配器把 URL 换成 RangeTransport，并把 PDF.js 后续区间交给加载器', async () => {
  const fake = createFakePdfFetch();
  const transports = [];

  class FakeRangeTransport {
    constructor(length, initialData) {
      this.length = length;
      this.initialData = initialData;
      this.delivered = [];
      transports.push(this);
    }

    onDataRange(begin, chunk) {
      this.delivered.push({begin, size: chunk.byteLength});
    }

    abort() {
      this.aborted = true;
    }
  }

  const documentOptions = [];
  const adapter = createPdfjsConcurrentRangeAdapter({
    PDFDataRangeTransport: FakeRangeTransport,
    getDocument(options) {
      documentOptions.push(options);
      return {
        promise: Promise.resolve({
          numPages: 2, async getPage() {
            return {};
          }
        })
      };
    }
  }, fake.fetchImpl);

  const task = adapter.getDocument({
    url: '/preview.pdf',
    httpHeaders: {Authorization: 'Bearer token'},
    disableRange: false,
    disableStream: true,
    disableAutoFetch: true,
    rangeChunkSize: CHUNK,
    useSystemFonts: true
  });
  const document = await task.promise;
  assert.equal(document.numPages, 2);
  assert.equal(documentOptions[0].range, transports[0]);
  assert.equal(transports[0].length, FILE_SIZE);

  await delay(40);
  transports[0].requestDataRange(8, 16);
  await delay(20);
  assert.equal(transports[0].delivered.some((item) => item.begin === 8), true);
  assert.ok(fake.maxInFlight() >= 2);
  await task.destroy?.();
});
