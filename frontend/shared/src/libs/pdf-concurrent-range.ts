/** 默认切片与 `PDF_RANGE_CHUNK_SIZE` 保持一致。 */
const DEFAULT_RANGE_CHUNK_SIZE = 262_144;

interface PdfDocumentLike {
  numPages: number;
}

interface PdfLoadingTaskLike<TDocument> {
  promise: Promise<TDocument>;

  destroy?(): Promise<void> | void;

  onPassword?: (updatePassword: (password: string) => void, reason: number) => void;
}

interface PdfDocumentAdapter<TDocument> {
  getDocument(options: {
    url?: string;
    data?: Uint8Array;
    httpHeaders?: Record<string, string>;
    disableRange: false;
    disableStream: true;
    disableAutoFetch: true;
    rangeChunkSize: number;
    useSystemFonts: true;
  }): PdfLoadingTaskLike<TDocument>;
}

/** 同一 PDF 同时发出的 Range 上限，用来重叠 RTT，避免打满浏览器或网关连接。 */
export const PDF_RANGE_MAX_CONCURRENT = 4;

/** 只提前预取少量后续切片，用来重叠 RTT，避免把未读区间拆成大量额外请求。 */
export const PDF_RANGE_LOOKAHEAD_CHUNKS = 3;

export interface PdfByteRange {
  begin: number;
  end: number;
}

export interface PdfConcurrentRangeLoaderOptions {
  url: string;
  httpHeaders?: Record<string, string>;
  chunkSize?: number;
  maxConcurrent?: number;
  lookaheadChunks?: number;
  fetchImpl?: typeof fetch;
}

interface PdfRangeTransportInstance {
  onDataRange(begin: number, chunk: Uint8Array): void;

  abort(): void;
}

type PdfjsConcurrentRangeHost<TDocument extends PdfDocumentLike> = {
  PDFDataRangeTransport: new (
    length: number,
    initialData: Uint8Array,
    progressiveDone?: boolean
  ) => PdfRangeTransportInstance;
  getDocument: (options: object) => PdfLoadingTaskLike<TDocument>;
};

type QueuedRange = {
  key: string;
  begin: number;
  end: number;
  priority: number;
  resolve: (value: Uint8Array) => void;
  reject: (reason: unknown) => void;
};

/** 从 206 Content-Range 读取文件总长度。 */
export function parsePdfContentRangeTotal(contentRange: string | null | undefined): number | undefined {
  if (!contentRange) return undefined;
  const match = /^bytes \d+-\d+\/(\d+)$/.exec(contentRange.trim());
  if (!match) return undefined;
  const total = Number(match[1]);
  return Number.isFinite(total) && total > 0 ? total : undefined;
}

/** 线性化 PDF 的 xref 通常在最后一个切片，探测到长度后与首块并行拉取。 */
export function pdfLastChunkRange(fileLength: number, chunkSize: number): PdfByteRange | undefined {
  if (fileLength <= chunkSize || chunkSize < 1) return undefined;
  const begin = Math.floor((fileLength - 1) / chunkSize) * chunkSize;
  return {begin, end: fileLength};
}

/** 从当前已请求终点起，规划后续对齐切片，不回绕到文件头。 */
export function pdfLookaheadByteRanges(
  afterOffset: number,
  fileLength: number,
  chunkSize: number,
  lookaheadChunks: number
): PdfByteRange[] {
  if (fileLength <= 0 || chunkSize < 1 || lookaheadChunks < 1) return [];
  const ranges: PdfByteRange[] = [];
  let begin = Math.max(0, Math.ceil(afterOffset / chunkSize) * chunkSize);
  for (let i = 0; i < lookaheadChunks && begin < fileLength; i += 1) {
    const end = Math.min(fileLength, begin + chunkSize);
    if (end <= begin) break;
    ranges.push({begin, end});
    begin = end;
  }
  return ranges;
}

function rangeKey(begin: number, end: number): string {
  return `${begin}-${end}`;
}

function abortedError(): DOMException {
  return new DOMException('PDF Range 已取消', 'AbortError');
}

/**
 * 在 PDF.js 仍然串行解析的前提下，把当前需要的 Range、文件尾和后续切片并行发出。
 * 只做有界流水线，空闲后不再继续拉未读页。
 */
export class PdfConcurrentRangeLoader {
  private readonly url: string;
  private readonly httpHeaders: Record<string, string>;
  private readonly chunkSize: number;
  private readonly maxConcurrent: number;
  private readonly lookaheadChunks: number;
  private readonly fetchImpl: typeof fetch;
  private readonly cache = new Map<string, Promise<Uint8Array>>();
  private readonly queue: QueuedRange[] = [];
  private controller = new AbortController();
  private active = 0;
  private aborted = false;
  private lastChunkScheduled = false;
  fileLength?: number;

  constructor(options: PdfConcurrentRangeLoaderOptions) {
    this.url = options.url;
    this.httpHeaders = options.httpHeaders ?? {};
    this.chunkSize = options.chunkSize ?? DEFAULT_RANGE_CHUNK_SIZE;
    this.maxConcurrent = Math.max(1, options.maxConcurrent ?? PDF_RANGE_MAX_CONCURRENT);
    this.lookaheadChunks = Math.max(0, options.lookaheadChunks ?? PDF_RANGE_LOOKAHEAD_CHUNKS);
    this.fetchImpl = options.fetchImpl ?? globalThis.fetch.bind(globalThis);
  }

  /** 先拉第一个切片拿到 Content-Range，并立刻并行预取尾块与后续切片。 */
  async probe(): Promise<{ length: number; initialData: Uint8Array }> {
    const initialData = await this.ensureRange(0, this.chunkSize, 0);
    if (this.fileLength == null) {
      throw new Error('PDF Range 响应缺少文件总长度');
    }
    this.scheduleWindow(initialData.byteLength);
    return {length: this.fileLength, initialData: initialData.slice()};
  }

  /** PDF.js 要某段时立即交付缓存或在途结果，并继续有界预取。 */
  requestRange(begin: number, end: number, deliver: (begin: number, chunk: Uint8Array) => void): void {
    void this.ensureRange(begin, end, 0)
      .then((chunk) => {
        if (this.aborted) return;
        deliver(begin, chunk);
        this.scheduleWindow(end);
      })
      .catch(() => {
        if (this.aborted) return;
        this.abort();
      });
  }

  abort(): void {
    if (this.aborted) return;
    this.aborted = true;
    this.controller.abort();
    const queued = this.queue.splice(0, this.queue.length);
    for (const job of queued) {
      this.cache.delete(job.key);
      job.reject(abortedError());
    }
  }

  private scheduleWindow(afterOffset: number): void {
    if (this.aborted || this.fileLength == null) return;
    if (!this.lastChunkScheduled) {
      const last = pdfLastChunkRange(this.fileLength, this.chunkSize);
      if (last) this.prefetch(last.begin, last.end, 1);
      this.lastChunkScheduled = true;
    }
    for (const range of pdfLookaheadByteRanges(
      afterOffset,
      this.fileLength,
      this.chunkSize,
      this.lookaheadChunks
    )) {
      this.prefetch(range.begin, range.end, 2);
    }
  }

  private prefetch(begin: number, end: number, priority: number): void {
    void this.ensureRange(begin, end, priority).catch(() => undefined);
  }

  private ensureRange(begin: number, end: number, priority: number): Promise<Uint8Array> {
    if (this.aborted) return Promise.reject(abortedError());
    const actualEnd = this.fileLength == null ? end : Math.min(end, this.fileLength);
    if (actualEnd <= begin) return Promise.reject(new Error('PDF Range 区间无效'));
    const key = rangeKey(begin, actualEnd);
    const cached = this.cache.get(key);
    if (cached) {
      const queued = this.queue.find((job) => job.key === key);
      if (queued && priority < queued.priority) {
        queued.priority = priority;
        this.queue.sort((left, right) => left.priority - right.priority);
      }
      return cached;
    }

    const pending = new Promise<Uint8Array>((resolve, reject) => {
      this.queue.push({key, begin, end: actualEnd, priority, resolve, reject});
      this.queue.sort((left, right) => left.priority - right.priority);
    });
    this.cache.set(key, pending);
    this.pump();
    return pending;
  }

  private pump(): void {
    while (!this.aborted && this.active < this.maxConcurrent && this.queue.length > 0) {
      const job = this.queue.shift();
      if (!job) break;
      this.active += 1;
      void this.fetchRange(job.begin, job.end)
        .then((bytes) => job.resolve(bytes))
        .catch((error: unknown) => {
          this.cache.delete(job.key);
          job.reject(error);
        })
        .finally(() => {
          this.active -= 1;
          this.pump();
        });
    }
  }

  private async fetchRange(begin: number, end: number): Promise<Uint8Array> {
    const response = await this.fetchImpl(this.url, {
      headers: {
        ...this.httpHeaders,
        Range: `bytes=${begin}-${end - 1}`
      },
      signal: this.controller.signal
    });
    if (this.aborted) throw abortedError();
    if (response.status !== 206) {
      throw new Error(`PDF 文件未按 Range 返回部分内容（HTTP ${response.status}）`);
    }
    const total = parsePdfContentRangeTotal(response.headers.get('Content-Range'));
    if (total != null) this.fileLength = total;
    return new Uint8Array(await response.arrayBuffer());
  }
}

/**
 * 把 PDF.js getDocument 接到并发 Range 加载器。
 * 必须注入同一份 pdfjs-dist 的 PDFDataRangeTransport，否则 instanceof 校验会失败。
 */
export function createPdfjsConcurrentRangeAdapter<TDocument extends PdfDocumentLike>(
  pdfjs: PdfjsConcurrentRangeHost<TDocument>,
  fetchImpl: typeof fetch = globalThis.fetch.bind(globalThis)
): PdfDocumentAdapter<TDocument> {
  return {
    getDocument(options) {
      if (options.data || !options.url) {
        return pdfjs.getDocument(options);
      }
      const loader = new PdfConcurrentRangeLoader({
        url: options.url,
        httpHeaders: options.httpHeaders,
        chunkSize: options.rangeChunkSize,
        fetchImpl
      });
      let inner: PdfLoadingTaskLike<TDocument> | undefined;
      let onPassword: PdfLoadingTaskLike<TDocument>['onPassword'];
      const wrapping: PdfLoadingTaskLike<TDocument> = {
        promise: Promise.resolve().then(async () => {
          const {length, initialData} = await loader.probe();

          class ConcurrentRangeTransport extends pdfjs.PDFDataRangeTransport {
            requestDataRange(begin: number, end: number) {
              loader.requestRange(begin, end, (offset, chunk) => this.onDataRange(offset, chunk));
            }

            abort() {
              loader.abort();
            }
          }

          const transport = new ConcurrentRangeTransport(length, initialData, true);
          inner = pdfjs.getDocument({
            ...options,
            range: transport,
            length
          });
          if (onPassword) inner.onPassword = onPassword;
          return inner.promise;
        }),
        async destroy() {
          loader.abort();
          await inner?.destroy?.();
        }
      };
      Object.defineProperty(wrapping, 'onPassword', {
        get() {
          return inner?.onPassword ?? onPassword;
        },
        set(value: PdfLoadingTaskLike<TDocument>['onPassword']) {
          onPassword = value;
          if (inner) inner.onPassword = value;
        },
        enumerable: true,
        configurable: true
      });
      return wrapping;
    }
  };
}
