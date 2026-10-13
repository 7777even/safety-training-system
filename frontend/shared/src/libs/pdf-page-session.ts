/** PDF.js Range 切片字节数。带宽充足时用 256 KB 减少往返，Page Tree 分层后不会拖成整文件。 */
export const PDF_RANGE_CHUNK_SIZE = 262_144;

/** 触控板惯性连发时，两次滚轮翻页的最短间隔，避免一次甩动跳过多页。 */
export const PDF_WHEEL_PAGE_COOLDOWN_MS = 160;

/** 按预览区宽高同时适配，保证默认缩放能完整放下当前页。 */
export function pdfPageFitScale(
  pageWidth: number,
  pageHeight: number,
  availableWidth: number,
  availableHeight: number
): number {
  const widthScale = Math.max(1, availableWidth) / Math.max(1, pageWidth);
  const heightScale = Math.max(1, availableHeight) / Math.max(1, pageHeight);
  return Math.max(0.01, Math.min(widthScale, heightScale));
}

/** 预览区还没按内容撑开时，用视口高度做预算，避免先按很小的 min-height 缩窄。 */
export const PDF_BOUNDED_STAGE_MIN_PX = 400;

export function pdfPageAvailableHeight(
  stageHeight: number,
  viewportHeight: number,
  minBoundedHeight = PDF_BOUNDED_STAGE_MIN_PX
): number {
  const bounded = Math.max(1, stageHeight);
  const viewport = Math.max(240, viewportHeight);
  return bounded >= minBoundedHeight ? Math.min(bounded, viewport) : viewport;
}

/** 滚动条出现/消失造成的 1～数像素抖动不应触发重新渲染。 */
export const PDF_STAGE_RESIZE_THRESHOLD_PX = 8;

export function pdfStageSizeChanged(
  prevWidth: number,
  prevHeight: number,
  nextWidth: number,
  nextHeight: number,
  threshold = PDF_STAGE_RESIZE_THRESHOLD_PX
): boolean {
  return Math.abs(nextWidth - prevWidth) > threshold || Math.abs(nextHeight - prevHeight) > threshold;
}

/** 当前页滚到顶/底后，滚轮才翻到上一页或下一页；页中滚动不换页。 */
export function pdfWheelPageDelta(
  deltaY: number,
  atTop: boolean,
  atBottom: boolean,
  currentPage: number,
  totalPages: number
): -1 | 0 | 1 {
  if (deltaY > 0 && atBottom && currentPage < totalPages) return 1;
  if (deltaY < 0 && atTop && currentPage > 1) return -1;
  return 0;
}

/** 焦点在 PDF 查看器上时，左右/上下方向键翻到上一页或下一页。 */
export function pdfArrowPageDelta(
  key: string,
  currentPage: number,
  totalPages: number
): -1 | 0 | 1 {
  if ((key === 'ArrowRight' || key === 'ArrowDown') && currentPage < totalPages) return 1;
  if ((key === 'ArrowLeft' || key === 'ArrowUp') && currentPage > 1) return -1;
  return 0;
}

/** 学习页连续平铺时页与页之间的间距。 */
export const PDF_CONTINUOUS_PAGE_GAP_PX = 12;

/** 视口外额外保留的页数，避免滚动时空白闪一下。 */
export const PDF_CONTINUOUS_BUFFER_PAGES = 1;

/** 连续平铺最多同时挂载的页面数，防止大视口按总页数铺开 DOM。 */
export const PDF_CONTINUOUS_MAX_VISIBLE_PAGES = 6;

/** 学习页按预览区宽度适配整页，页面自上而下平铺。 */
export function pdfContinuousFitWidthScale(pageWidth: number, availableWidth: number): number {
  return Math.max(1, availableWidth) / Math.max(1, pageWidth);
}

export function pdfContinuousPageStride(displayHeight: number, gap = PDF_CONTINUOUS_PAGE_GAP_PX): number {
  return Math.max(1, Math.floor(displayHeight)) + Math.max(0, gap);
}

export function pdfContinuousTotalHeight(
  pageCount: number,
  displayHeight: number,
  gap = PDF_CONTINUOUS_PAGE_GAP_PX
): number {
  if (pageCount < 1) return 0;
  return pageCount * Math.max(1, Math.floor(displayHeight)) + Math.max(0, pageCount - 1) * Math.max(0, gap);
}

export function pdfContinuousPageOffset(pageNo: number, stride: number): number {
  return Math.max(0, Math.floor(pageNo) - 1) * Math.max(1, stride);
}

export function pdfContinuousCurrentPage(scrollTop: number, stride: number, pageCount: number): number {
  if (pageCount < 1) return 0;
  return Math.min(pageCount, Math.max(1, Math.floor(Math.max(0, scrollTop) / Math.max(1, stride)) + 1));
}

/** 只返回当前视口附近的页码窗口，不按文档总页数展开。 */
export function pdfContinuousVisibleRange(
  scrollTop: number,
  viewportHeight: number,
  stride: number,
  pageCount: number,
  buffer = PDF_CONTINUOUS_BUFFER_PAGES,
  maxVisible = PDF_CONTINUOUS_MAX_VISIBLE_PAGES
): { from: number; to: number } {
  if (pageCount < 1 || stride < 1) return {from: 1, to: 0};
  const start = Math.floor(Math.max(0, scrollTop) / stride) + 1 - Math.max(0, buffer);
  const end = Math.ceil((Math.max(0, scrollTop) + Math.max(1, viewportHeight)) / stride) + Math.max(0, buffer);
  const from = Math.min(pageCount, Math.max(1, start));
  const unboundedTo = Math.min(pageCount, Math.max(from, end));
  const to = Math.min(unboundedTo, from + Math.max(1, maxVisible) - 1);
  return {from, to};
}

export function pdfContinuousPageList(from: number, to: number): number[] {
  if (to < from) return [];
  const pages: number[] = [];
  for (let pageNo = from; pageNo <= to; pageNo += 1) pages.push(pageNo);
  return pages;
}

/**
 * 把整页滚动映射成 PDF 长条内部的 scrollTop。
 * 学习页连续平铺跟页面一起变长，不再在固定高度小窗里滑动。
 */
export function pdfContinuousWindowMetrics(
  stageTop: number,
  stageHeight: number,
  viewportHeight: number
): { scrollTop: number; visibleHeight: number } {
  const viewH = Math.max(1, viewportHeight);
  const height = Math.max(0, stageHeight);
  const scrollTop = Math.max(0, -stageTop);
  const visibleTop = Math.max(0, stageTop);
  const visibleBottom = Math.min(viewH, stageTop + height);
  const visibleHeight = Math.max(0, visibleBottom - visibleTop);
  return {scrollTop, visibleHeight};
}

/** PDF.js 文档加载参数的最小稳定子集，供 PC 与 H5 共用加载编排。 */
export interface PdfDocumentSource {
  url?: string;
  data?: Uint8Array;
  httpHeaders?: Record<string, string>;
}

export interface PdfRenderTaskLike {
  promise: Promise<unknown>;

  cancel(): void;
}

export interface PdfPageLike {
  cleanup?(): void;
}

export interface PdfDocumentLike<TPage extends PdfPageLike = PdfPageLike> {
  numPages: number;

  getPage(pageNo: number): Promise<TPage>;

  destroy?(): Promise<void> | void;
}

export interface PdfLoadingTaskLike<TDocument extends PdfDocumentLike = PdfDocumentLike> {
  promise: Promise<TDocument>;

  destroy?(): Promise<void> | void;

  onPassword?: (updatePassword: (password: string) => void, reason: number) => void;
}

export interface PdfDocumentAdapter<TDocument extends PdfDocumentLike = PdfDocumentLike> {
  getDocument(options: PdfDocumentSource & {
    disableRange: false;
    disableStream: true;
    disableAutoFetch: true;
    rangeChunkSize: typeof PDF_RANGE_CHUNK_SIZE;
    useSystemFonts: true;
  }): PdfLoadingTaskLike<TDocument>;
}

export interface PdfPageSessionHandlers<TPage extends PdfPageLike = PdfPageLike> {
  renderPage(page: TPage, pageNo: number): PdfRenderTaskLike;

  onLoadingTask?(task: PdfLoadingTaskLike): void;

  onDocumentLoaded?(pageCount: number): void;

  onPageRendered?(pageNo: number): void;
}

/**
 * 只编排当前 PDF 页的加载、取消和释放，不感知 Vue 或 Canvas。
 * 页面总数只用于合法性裁剪，绝不据此预读取页面。
 */
export class PdfPageSession<
  TPage extends PdfPageLike = PdfPageLike,
  TDocument extends PdfDocumentLike<TPage> = PdfDocumentLike<TPage>
> {
  private readonly adapter: PdfDocumentAdapter<TDocument>;
  private readonly handlers: PdfPageSessionHandlers<TPage>;
  private loadingTask?: PdfLoadingTaskLike<TDocument>;
  private document?: TDocument;
  private page?: TPage;
  private renderTask?: PdfRenderTaskLike;
  private documentVersion = 0;
  private renderVersion = 0;

  totalPages = 0;
  currentPage = 0;

  constructor(adapter: PdfDocumentAdapter<TDocument>, handlers: PdfPageSessionHandlers<TPage>) {
    this.adapter = adapter;
    this.handlers = handlers;
  }

  /** 打开新文档；默认只渲染裁剪后的初始目标页。学习页连续平铺可跳过初始渲染，再按视口按需取页。 */
  async open(source: PdfDocumentSource, initialPageNo = 1, options?: { render?: boolean }): Promise<void> {
    const version = ++this.documentVersion;
    await this.releaseCurrentDocument();
    if (version !== this.documentVersion) return;
    const task = this.adapter.getDocument({
      ...source,
      disableRange: false,
      disableStream: true,
      disableAutoFetch: true,
      rangeChunkSize: PDF_RANGE_CHUNK_SIZE,
      useSystemFonts: true
    });
    this.loadingTask = task;
    this.handlers.onLoadingTask?.(task);

    let document: TDocument;
    try {
      document = await task.promise;
    } catch (error) {
      if (version !== this.documentVersion) return;
      throw error;
    }
    if (version !== this.documentVersion) {
      await document.destroy?.();
      await task.destroy?.();
      return;
    }
    this.document = document;
    this.totalPages = Math.max(0, Math.floor(document.numPages));
    this.handlers.onDocumentLoaded?.(this.totalPages);
    if (this.totalPages < 1) return;
    const clippedPage = Math.min(this.totalPages, Math.max(1, Math.floor(initialPageNo) || 1));
    if (options?.render === false) {
      this.currentPage = clippedPage;
      return;
    }
    await this.goToPage(clippedPage);
  }

  /** 按需读取一页，不取消其他页的渲染；调用方负责在离开视口后 cleanup。 */
  async acquirePage(pageNo: number): Promise<TPage | null> {
    const document = this.document;
    if (!document || this.totalPages < 1) return null;
    const targetPage = Math.min(this.totalPages, Math.max(1, Math.floor(pageNo) || 1));
    return document.getPage(targetPage);
  }

  /** 取消过期渲染，仅提交最后一次成功显示的目标页。 */
  async goToPage(pageNo: number): Promise<boolean> {
    const document = this.document;
    if (!document || this.totalPages < 1) return false;
    const targetPage = Math.min(this.totalPages, Math.max(1, Math.floor(pageNo) || 1));
    const version = ++this.renderVersion;
    this.cancelRender();

    const page = await document.getPage(targetPage);
    if (version !== this.renderVersion || document !== this.document) {
      page.cleanup?.();
      return false;
    }

    const renderTask = this.handlers.renderPage(page, targetPage);
    this.renderTask = renderTask;
    try {
      await renderTask.promise;
    } catch (error) {
      if (version !== this.renderVersion) {
        page.cleanup?.();
        return false;
      }
      throw error;
    }
    if (version !== this.renderVersion || document !== this.document) {
      page.cleanup?.();
      return false;
    }

    this.renderTask = undefined;
    this.page?.cleanup?.();
    this.page = page;
    this.currentPage = targetPage;
    this.handlers.onPageRendered?.(targetPage);
    return true;
  }

  /** 销毁当前会话；后续异步结果均不得再提交。 */
  async destroy(): Promise<void> {
    this.documentVersion += 1;
    this.renderVersion += 1;
    await this.releaseCurrentDocument();
  }

  private cancelRender(): void {
    if (!this.renderTask) return;
    try {
      this.renderTask.cancel();
    } catch {
      // PDF.js 对已经结束的任务可能拒绝重复取消，释放流程继续执行。
    }
    this.renderTask = undefined;
  }

  private async releaseCurrentDocument(): Promise<void> {
    this.renderVersion += 1;
    this.cancelRender();
    this.page?.cleanup?.();
    this.page = undefined;
    this.currentPage = 0;
    this.totalPages = 0;
    const document = this.document;
    const loadingTask = this.loadingTask;
    this.document = undefined;
    this.loadingTask = undefined;
    await document?.destroy?.();
    await loadingTask?.destroy?.();
  }
}
