<script setup lang="ts">
import { computed, defineComponent, h, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import {
  createPdfjsConcurrentRangeAdapter,
  PDF_WHEEL_PAGE_COOLDOWN_MS,
  pdfArrowPageDelta,
  pdfContinuousCurrentPage,
  pdfContinuousFitWidthScale,
  pdfContinuousPageList,
  pdfContinuousPageOffset,
  pdfContinuousPageStride,
  pdfContinuousTotalHeight,
  pdfContinuousVisibleRange,
  pdfContinuousWindowMetrics,
  type PdfLoadingTaskLike,
  pdfPageAvailableHeight,
  pdfPageFitScale,
  PdfPageSession,
  type PdfRenderTaskLike,
  pdfStageSizeChanged,
  pdfWheelPageDelta
} from '@shared/index';
import type { PDFDocumentLoadingTask, PDFDocumentProxy, PDFPageProxy, RenderTask } from 'pdfjs-dist';
import * as pdfjsLib from 'pdfjs-dist';
import pdfWorker from 'pdfjs-dist/build/pdf.worker.min.mjs?url';
import { getAccessToken } from '@/api/request';
import { AUTH_TOKEN_STORAGE_KEY } from '@shared/constants';

const props = defineProps<{
  /** 受控地址；调用方直接持有字节时改用 `data`，两者取其一。 */
  url?: string;
  /**
   * 直接渲染的 PDF 字节，语义与共享 `PdfDocumentSource.data` 一致。
   * 传入时不走并发 Range 加载，也不再发第二次受控请求。
   */
  data?: Uint8Array | null;
  fileName?: string;
  initialPageNo?: number | null;
  layout?: 'page' | 'continuous';
}>();

const emit = defineEmits<{
  error: [message: string];
  loaded: [];
  pagecount: [pageCount: number];
  pageupdate: [pageNo: number];
  fallback: [];
}>();

const MIN_ZOOM = 0.6;
const MAX_ZOOM = 2.4;
const ZOOM_STEP = 0.2;
const MAX_CANVAS_PIXELS = 12_582_912;

pdfjsLib.GlobalWorkerOptions.workerSrc = pdfWorker;

const root = ref<globalThis.HTMLElement | { $el?: globalThis.HTMLElement }>();
const stage = ref<globalThis.HTMLElement | { $el?: globalThis.HTMLElement }>();
const canvas = ref<globalThis.HTMLCanvasElement>();
const loading = ref(false);
const pageLoading = ref(false);
const errorMessage = ref('');
const totalPages = ref(0);
const currentPage = ref(0);
const pageInput = ref(1);
const zoom = ref(1);
const rotation = ref(0);
const password = ref('');
const passwordRequired = ref(false);
const passwordIncorrect = ref(false);
/** 内容超出可视区时才显示滚动辅助控件，短文档不出现无效按钮。 */
const canScroll = ref(false);
/** 长按方向：-1 向上、1 向下、0 表示当前未长按。 */
const holding = ref<-1 | 0 | 1>(0);
let updatePassword: ((value: string) => void) | undefined;
let resizeObserver: globalThis.ResizeObserver | undefined;
let resizeTimer: ReturnType<typeof globalThis.setTimeout> | undefined;
let assistObserver: globalThis.ResizeObserver | undefined;
let scrollFrame: number | undefined;
let scrollStep = 0;
let scrollContainer: globalThis.HTMLElement | null = null;
/** 是否已确定滚动载体（null 容器表示滚动在视口上），避免每次滚动都遍历 DOM。 */
let scrollResolved = false;
let lastWheelPageAt = 0;
let lastFitWidth = 0;
let lastFitHeight = 0;
let renderUiVersion = 0;
const isContinuous = computed(() => props.layout === 'continuous');
const visiblePageNos = ref<number[]>([]);
const scrollHeightPx = ref(0);
const displayHeightPx = ref(1);
const pageStridePx = ref(1);
const continuousCanvases = new Map<number, globalThis.HTMLCanvasElement>();
const continuousPages = new Map<number, PDFPageProxy>();
const continuousRenders = new Map<number, RenderTask>();
const continuousRenderedCanvases = new Map<number, globalThis.HTMLCanvasElement>();
const continuousTokens = new Map<number, number>();
let continuousSyncVersion = 0;
let basePageWidth = 0;
let basePageHeight = 0;
let restoringContinuousPage = false;

type LoadingTask = PDFDocumentLoadingTask & PdfLoadingTaskLike<PDFDocumentProxy>;

/** uni-app H5 的 view ref 可能是原生元素，也可能包一层 `$el`。 */
function nativeElement(
  target:
    | globalThis.HTMLElement
    | {
        $el?: globalThis.HTMLElement;
      }
    | undefined
): globalThis.HTMLElement | undefined {
  if (!target) return undefined;
  if (target instanceof globalThis.HTMLElement) return target;
  return target.$el instanceof globalThis.HTMLElement ? target.$el : undefined;
}

function stageElement(): globalThis.HTMLElement | undefined {
  return nativeElement(stage.value);
}

/** 使用运行时原生 Canvas，绕开 uni-app 模板将 canvas 编译成 uni-canvas 宿主后的尺寸错位。 */
const NativePdfCanvas = defineComponent({
  name: 'NativePdfCanvas',
  props: {
    pageNo: { type: Number, required: true }
  },
  emits: ['ready', 'dispose'],
  setup(props, { attrs, emit }) {
    const nativeCanvas = ref<globalThis.HTMLCanvasElement>();
    onMounted(() => {
      if (nativeCanvas.value) emit('ready', props.pageNo, nativeCanvas.value);
    });
    onBeforeUnmount(() => emit('dispose', props.pageNo));
    return () => h('canvas', { ...attrs, ref: nativeCanvas });
  }
});

function continuousAvailableWidth(): number {
  return Math.max(1, (stageElement()?.clientWidth ?? 390) - 20);
}

function applyContinuousMetrics(): void {
  if (!basePageWidth || !basePageHeight || !totalPages.value) return;
  const availableWidth = continuousAvailableWidth();
  lastFitWidth = availableWidth;
  const scale = pdfContinuousFitWidthScale(basePageWidth, availableWidth) * zoom.value;
  displayHeightPx.value = Math.max(1, Math.floor(basePageHeight * scale));
  pageStridePx.value = pdfContinuousPageStride(displayHeightPx.value);
  scrollHeightPx.value = pdfContinuousTotalHeight(totalPages.value, displayHeightPx.value);
}

function releaseContinuousPage(pageNo: number): void {
  const task = continuousRenders.get(pageNo);
  if (task) {
    try {
      task.cancel();
    } catch {
      // 忽略已结束任务。
    }
    continuousRenders.delete(pageNo);
  }
  continuousPages.get(pageNo)?.cleanup?.();
  continuousPages.delete(pageNo);
  continuousCanvases.delete(pageNo);
  continuousRenderedCanvases.delete(pageNo);
}

function releaseContinuousWindow(): void {
  for (const pageNo of [...continuousCanvases.keys()]) releaseContinuousPage(pageNo);
  visiblePageNos.value = [];
}

async function paintContinuousPage(pageNo: number, force = false): Promise<void> {
  const targetCanvas = continuousCanvases.get(pageNo);
  if (!targetCanvas || !basePageWidth) return;
  if (!force && continuousRenderedCanvases.get(pageNo) === targetCanvas) return;
  const token = (continuousTokens.get(pageNo) ?? 0) + 1;
  continuousTokens.set(pageNo, token);
  const existing = continuousRenders.get(pageNo);
  if (existing) {
    if (!force) return;
    try {
      existing.cancel();
    } catch {
      // 忽略已结束任务。
    }
    continuousRenders.delete(pageNo);
  }
  const page = continuousPages.get(pageNo) ?? (await session.acquirePage(pageNo));
  if (!page || continuousTokens.get(pageNo) !== token || !continuousCanvases.has(pageNo)) {
    if (page && !continuousPages.has(pageNo)) page.cleanup?.();
    return;
  }
  continuousPages.set(pageNo, page);
  const scale = pdfContinuousFitWidthScale(basePageWidth, continuousAvailableWidth()) * zoom.value;
  const viewport = page.getViewport({ scale, rotation: rotation.value });
  const context = targetCanvas.getContext('2d');
  if (!context) return;
  const requestedOutputScale = Math.min(globalThis.devicePixelRatio || 1, 2);
  const maxOutputScale = Math.sqrt(MAX_CANVAS_PIXELS / Math.max(1, viewport.width * viewport.height));
  const outputScale = Math.max(0.5, Math.min(requestedOutputScale, maxOutputScale));
  targetCanvas.width = Math.max(1, Math.floor(viewport.width * outputScale));
  targetCanvas.height = Math.max(1, Math.floor(viewport.height * outputScale));
  targetCanvas.style.width = `${Math.floor(viewport.width)}px`;
  targetCanvas.style.height = `${Math.floor(viewport.height)}px`;
  const task = page.render({
    canvas: targetCanvas,
    canvasContext: context,
    viewport,
    transform: outputScale === 1 ? undefined : [outputScale, 0, 0, outputScale, 0, 0]
  }) as RenderTask;
  continuousRenders.set(pageNo, task);
  try {
    await task.promise;
    if (continuousCanvases.get(pageNo) === targetCanvas && continuousTokens.get(pageNo) === token) {
      continuousRenderedCanvases.set(pageNo, targetCanvas);
    }
  } catch {
    // 离开视口或切换缩放时取消属于预期。
  } finally {
    if (continuousRenders.get(pageNo) === task) continuousRenders.delete(pageNo);
  }
}

function bindContinuousCanvas(pageNo: number, targetCanvas: globalThis.HTMLCanvasElement): void {
  if (continuousCanvases.get(pageNo) === targetCanvas) return;
  continuousCanvases.set(pageNo, targetCanvas);
  globalThis.setTimeout(() => {
    if (visiblePageNos.value.includes(pageNo)) void paintContinuousPage(pageNo);
  });
}

function bindPageCanvas(_pageNo: number, targetCanvas: globalThis.HTMLCanvasElement): void {
  canvas.value = targetCanvas;
}

function releasePageCanvas(): void {
  canvas.value = undefined;
}

function updateContinuousCurrentPage(scrollTop: number): void {
  if (!totalPages.value) return;
  const pageNo = pdfContinuousCurrentPage(scrollTop, pageStridePx.value, totalPages.value);
  if (!pageNo || pageNo === currentPage.value) return;
  const firstPaint = currentPage.value === 0;
  currentPage.value = pageNo;
  pageInput.value = pageNo;
  emit('pageupdate', pageNo);
  if (firstPaint) emit('loaded');
}

function continuousWindowMetrics(): { scrollTop: number; visibleHeight: number } {
  const el = stageElement();
  const viewportHeight = globalThis.innerHeight || 800;
  if (!el) return { scrollTop: 0, visibleHeight: viewportHeight };
  const rect = el.getBoundingClientRect();
  return pdfContinuousWindowMetrics(rect.top, el.offsetHeight, viewportHeight);
}

async function syncContinuousWindow(forcePaint = false): Promise<void> {
  const el = stageElement();
  if (!el || !totalPages.value) return;
  applyContinuousMetrics();
  const metrics = continuousWindowMetrics();
  const range = pdfContinuousVisibleRange(metrics.scrollTop, Math.max(1, metrics.visibleHeight), pageStridePx.value, totalPages.value);
  const nextVisible = pdfContinuousPageList(range.from, range.to);
  const unchanged =
    !forcePaint && nextVisible.length === visiblePageNos.value.length && nextVisible.every((pageNo, index) => pageNo === visiblePageNos.value[index]);
  updateContinuousCurrentPage(metrics.scrollTop);
  if (unchanged) return;
  const previousVisible = visiblePageNos.value;
  const pagesToPaint = forcePaint ? nextVisible : nextVisible.filter((pageNo) => !previousVisible.includes(pageNo));
  const version = ++continuousSyncVersion;
  visiblePageNos.value = nextVisible;
  for (const pageNo of [...continuousCanvases.keys()]) {
    if (!nextVisible.includes(pageNo)) releaseContinuousPage(pageNo);
  }
  await nextTick();
  for (const pageNo of pagesToPaint) {
    if (version !== continuousSyncVersion) return;
    await paintContinuousPage(pageNo, forcePaint);
  }
}

function onWindowScroll(): void {
  if (!isContinuous.value || restoringContinuousPage) return;
  void syncContinuousWindow();
}

/** 返回真正可滚动的元素；返回 null 表示滚动实际发生在视口（window）上。 */
function resolveScrollContainer(): globalThis.HTMLElement | null {
  const doc = globalThis.document;
  const stageEl = stageElement();
  if (stageEl && stageEl.scrollHeight - stageEl.clientHeight > 8) return stageEl;
  let node: globalThis.HTMLElement | null = nativeElement(root.value)?.parentElement ?? null;
  while (node && node !== doc.body && node !== doc.documentElement) {
    const { overflowY } = globalThis.getComputedStyle(node);
    // 只认真正能滚的祖先：overflow 为 auto 但内容没超出的节点会让置底失效。
    if ((overflowY === 'auto' || overflowY === 'scroll') && node.scrollHeight - node.clientHeight > 8) return node;
    node = node.parentElement;
  }
  // body / documentElement 上的 overflow 只是视口滚动的传播者，写入它们的 scrollTop 不会生效。
  return null;
}

/** 视口滚动的最大距离：H5 下滚动可能落在 documentElement，也可能落在 body，两处都取。 */
function viewportMaxScroll(): number {
  const doc = globalThis.document;
  const contentHeight = Math.max(doc.documentElement?.scrollHeight ?? 0, doc.body?.scrollHeight ?? 0);
  const viewportHeight = globalThis.innerHeight || doc.documentElement?.clientHeight || 0;
  return Math.max(0, contentHeight - viewportHeight);
}

function currentScrollTop(): number {
  if (scrollContainer) return scrollContainer.scrollTop;
  const doc = globalThis.document;
  return Math.round(globalThis.scrollY || doc.documentElement?.scrollTop || doc.body?.scrollTop || 0);
}

function currentMaxScroll(): number {
  if (scrollContainer) return Math.max(0, scrollContainer.scrollHeight - scrollContainer.clientHeight);
  return viewportMaxScroll();
}

/** 写入滚动位置：视口滚动统一走 window.scrollTo，避免 quirks 模式下载体判定出错。 */
function applyScrollTop(top: number, smooth: boolean): void {
  if (scrollContainer) {
    if (smooth) scrollContainer.scrollTo({ top, behavior: 'smooth' });
    else scrollContainer.scrollTop = top;
    return;
  }
  globalThis.scrollTo({ top, behavior: smooth ? 'smooth' : 'auto' });
}

/** 解析一次即记住结论；缩放、换页会重置后重新解析。 */
function ensureScrollContainer(): void {
  if (scrollResolved) return;
  scrollContainer = resolveScrollContainer();
  scrollResolved = true;
}

/** 容器或内容尺寸变化后同步控件显示状态；元素容器被移除或不再可滚时重新解析。 */
function syncScrollAssist(): void {
  if (!scrollResolved || (scrollContainer !== null && (!scrollContainer.isConnected || currentMaxScroll() <= 8))) {
    scrollContainer = resolveScrollContainer();
    scrollResolved = true;
  }
  canScroll.value = currentMaxScroll() > 8;
}

/** 长按结束、触摸取消或页面失焦时立即停止，避免滚动失控。 */
function stopScroll(): void {
  scrollStep = 0;
  holding.value = 0;
  if (scrollFrame !== undefined) {
    globalThis.cancelAnimationFrame(scrollFrame);
    scrollFrame = undefined;
  }
}

/** 每帧推进一次滚动，到达顶端或底端后自动停止。 */
function stepScroll(): void {
  if (scrollStep === 0) {
    stopScroll();
    return;
  }
  const current = currentScrollTop();
  const next = Math.min(currentMaxScroll(), Math.max(0, current + scrollStep));
  if (next === current) {
    stopScroll();
    return;
  }
  applyScrollTop(next, false);
  scrollFrame = globalThis.requestAnimationFrame(stepScroll);
}

/** 长按开始持续滚动，步长与 PC 端同口径（按可视高度自适应），长文档也能快速浏览。 */
function startScroll(direction: -1 | 1): void {
  ensureScrollContainer();
  if (currentMaxScroll() <= 8) return;
  const viewportHeight = scrollContainer?.clientHeight || globalThis.innerHeight || 0;
  stopScroll();
  scrollStep = direction * Math.max(24, Math.round(viewportHeight * 0.06));
  holding.value = direction;
  stepScroll();
}

/** 一键回到最顶端或直达最低端，平滑滚动保留方向感。 */
function scrollToEdge(edge: 'top' | 'bottom'): void {
  ensureScrollContainer();
  stopScroll();
  applyScrollTop(edge === 'top' ? 0 : currentMaxScroll(), true);
}

/** 返回课程内容项的开头，便于从长文档继续选择章节或素材。 */
function backToTop(): void {
  scrollToEdge('top');
}

async function goToContinuousPage(pageNo: number): Promise<void> {
  const el = stageElement();
  if (!el || !totalPages.value) return;
  const target = Math.min(totalPages.value, Math.max(1, Math.floor(pageNo) || 1));
  applyContinuousMetrics();
  restoringContinuousPage = true;
  const pageOffset = pdfContinuousPageOffset(target, pageStridePx.value);
  const stageTop = el.getBoundingClientRect().top + (globalThis.scrollY || 0);
  globalThis.scrollTo({ top: Math.max(0, stageTop + pageOffset - 12) });
  await syncContinuousWindow(true);
  restoringContinuousPage = false;
  if (currentPage.value !== target) {
    const firstPaint = currentPage.value === 0;
    currentPage.value = target;
    pageInput.value = target;
    emit('pageupdate', target);
    if (firstPaint) emit('loaded');
  }
}

async function measureContinuousPageSize(): Promise<void> {
  const page = await session.acquirePage(1);
  if (!page) return;
  const viewport = page.getViewport({ scale: 1, rotation: rotation.value });
  basePageWidth = viewport.width;
  basePageHeight = viewport.height;
  page.cleanup?.();
  applyContinuousMetrics();
}

function authHeaders(): Record<string, string> {
  const token = getAccessToken() || uni.getStorageSync(AUTH_TOKEN_STORAGE_KEY);
  return token ? { Authorization: `Bearer ${token}` } : {};
}

/** H5 只渲染当前页，按预览区完整放下该页，并限制高分屏 Canvas 的最大像素量。 */
function renderPdfPage(pageLike: PDFPageProxy): PdfRenderTaskLike {
  const uiVersion = ++renderUiVersion;
  const targetCanvas = canvas.value;
  if (!targetCanvas) throw new Error('PDF 画布尚未初始化');
  const baseViewport = pageLike.getViewport({ scale: 1, rotation: rotation.value });
  const availableWidth = Math.max(1, (stageElement()?.clientWidth ?? 390) - 20);
  const availableHeight = pdfPageAvailableHeight((stageElement()?.clientHeight ?? 0) - 20, (globalThis.innerHeight || 800) - 140);
  lastFitWidth = availableWidth;
  lastFitHeight = availableHeight;
  const fitScale = pdfPageFitScale(baseViewport.width, baseViewport.height, availableWidth, availableHeight);
  const viewport = pageLike.getViewport({ scale: fitScale * zoom.value, rotation: rotation.value });
  const context = targetCanvas.getContext('2d');
  if (!context) throw new Error('当前浏览器不支持 PDF Canvas 渲染');
  const requestedOutputScale = Math.min(globalThis.devicePixelRatio || 1, 2);
  const maxOutputScale = Math.sqrt(MAX_CANVAS_PIXELS / Math.max(1, viewport.width * viewport.height));
  const outputScale = Math.max(0.5, Math.min(requestedOutputScale, maxOutputScale));
  targetCanvas.width = Math.max(1, Math.floor(viewport.width * outputScale));
  targetCanvas.height = Math.max(1, Math.floor(viewport.height * outputScale));
  targetCanvas.style.width = `${Math.floor(viewport.width)}px`;
  targetCanvas.style.height = `${Math.floor(viewport.height)}px`;
  const task = pageLike.render({
    canvas: targetCanvas,
    canvasContext: context,
    viewport,
    transform: outputScale === 1 ? undefined : [outputScale, 0, 0, outputScale, 0, 0]
  }) as RenderTask;
  return {
    promise: task.promise.finally(() => {
      if (uiVersion === renderUiVersion) pageLoading.value = false;
    }),
    cancel: () => task.cancel()
  };
}

const session = new PdfPageSession<PDFPageProxy, PDFDocumentProxy>(
  createPdfjsConcurrentRangeAdapter({
    PDFDataRangeTransport: pdfjsLib.PDFDataRangeTransport,
    getDocument: (options) => pdfjsLib.getDocument(options as Parameters<typeof pdfjsLib.getDocument>[0]) as LoadingTask
  }),
  {
    renderPage: (page) => renderPdfPage(page),
    onLoadingTask(task) {
      const loadingTask = task as LoadingTask;
      loadingTask.onPassword = (callback, reason) => {
        updatePassword = callback;
        passwordRequired.value = true;
        passwordIncorrect.value = reason === pdfjsLib.PasswordResponses.INCORRECT_PASSWORD;
        loading.value = false;
      };
    },
    onDocumentLoaded(pageCount) {
      totalPages.value = pageCount;
      emit('pagecount', pageCount);
    },
    onPageRendered(pageNo) {
      const firstPaint = currentPage.value === 0;
      const pageChanged = currentPage.value !== pageNo;
      currentPage.value = pageNo;
      pageInput.value = pageNo;
      errorMessage.value = '';
      if (pageChanged) emit('pageupdate', pageNo);
      if (firstPaint) emit('loaded');
    }
  }
);

function readableError(error: unknown): string {
  const name = error instanceof Error ? error.name : '';
  if (name === 'PasswordException') return 'PDF 密码不正确或未提供密码';
  if (name === 'InvalidPDFException' || name === 'FormatError') return 'PDF 文件已损坏或格式无效';
  if (name === 'MissingPDFException') return 'PDF 文件不存在或已失效';
  if (name === 'UnexpectedResponseException') return 'PDF 请求失败，请检查访问权限';
  return error instanceof Error && error.message ? error.message : 'PDF 加载失败，请稍后重试';
}

async function loadDocument(): Promise<void> {
  await nextTick();
  if (!props.url && !props.data) return;
  if (!isContinuous.value && !canvas.value) return;
  releaseContinuousWindow();
  loading.value = true;
  errorMessage.value = '';
  totalPages.value = 0;
  currentPage.value = 0;
  passwordRequired.value = false;
  passwordIncorrect.value = false;
  password.value = '';
  updatePassword = undefined;
  zoom.value = 1;
  rotation.value = 0;
  basePageWidth = 0;
  basePageHeight = 0;
  try {
    await session.open(
      props.data ? { data: props.data } : { url: props.url, httpHeaders: authHeaders() },
      props.initialPageNo ?? 1,
      { render: !isContinuous.value }
    );
    if (isContinuous.value && totalPages.value > 0) {
      await measureContinuousPageSize();
      await goToContinuousPage(props.initialPageNo ?? 1);
    }
  } catch (error) {
    if (!passwordRequired.value) {
      errorMessage.value = readableError(error);
      emit('error', errorMessage.value);
    }
  } finally {
    if (!passwordRequired.value) loading.value = false;
  }
}

async function goToPage(pageNo: number, silent = false): Promise<void> {
  if (!totalPages.value) return;
  if (isContinuous.value) {
    await goToContinuousPage(pageNo);
    return;
  }
  if (!silent) pageLoading.value = true;
  try {
    await session.goToPage(pageNo);
  } catch (error) {
    errorMessage.value = readableError(error);
    emit('error', errorMessage.value);
  } finally {
    if (!silent) pageLoading.value = false;
  }
}

function submitPassword(): void {
  if (!updatePassword || !password.value) return;
  loading.value = true;
  passwordRequired.value = false;
  updatePassword(password.value);
}

function changeZoom(delta: number): void {
  zoom.value = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, Number((zoom.value + delta).toFixed(2))));
  if (!currentPage.value) return;
  if (isContinuous.value) {
    void goToContinuousPage(currentPage.value);
    return;
  }
  void goToPage(currentPage.value);
}

function rotate(): void {
  rotation.value = (rotation.value + 90) % 360;
  if (!currentPage.value) return;
  if (isContinuous.value) {
    void measureContinuousPageSize().then(() => goToContinuousPage(currentPage.value));
    return;
  }
  void goToPage(currentPage.value);
}

function restoreStageScroll(direction: -1 | 1): void {
  const el = stageElement();
  if (!el) return;
  el.scrollTop = direction > 0 ? 0 : el.scrollHeight;
}

function onStageWheel(event: globalThis.WheelEvent): void {
  if (loading.value || pageLoading.value || passwordRequired.value || errorMessage.value) return;
  const el = stageElement();
  if (!el) return;
  const atTop = el.scrollTop <= 1;
  const atBottom = el.scrollTop + el.clientHeight >= el.scrollHeight - 1;
  const step = pdfWheelPageDelta(event.deltaY, atTop, atBottom, currentPage.value, totalPages.value);
  if (!step) return;
  event.preventDefault();
  const now = Date.now();
  if (now - lastWheelPageAt < PDF_WHEEL_PAGE_COOLDOWN_MS) return;
  lastWheelPageAt = now;
  void goToPage(currentPage.value + step).then(() => {
    void nextTick(() => restoreStageScroll(step));
  });
}

function isEditableKeyTarget(target: globalThis.EventTarget | null): boolean {
  if (!(target instanceof globalThis.HTMLElement)) return false;
  const tag = target.tagName;
  return tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || target.isContentEditable;
}

function turnPageBy(step: -1 | 1): void {
  const now = Date.now();
  if (now - lastWheelPageAt < PDF_WHEEL_PAGE_COOLDOWN_MS) return;
  lastWheelPageAt = now;
  void goToPage(currentPage.value + step).then(() => {
    void nextTick(() => restoreStageScroll(step));
  });
}

function onViewerKeydown(event: globalThis.KeyboardEvent): void {
  if (isContinuous.value) return;
  if (loading.value || pageLoading.value || passwordRequired.value || errorMessage.value) return;
  if (event.altKey || event.ctrlKey || event.metaKey) return;
  if (isEditableKeyTarget(event.target)) return;
  const step = pdfArrowPageDelta(event.key, currentPage.value, totalPages.value);
  if (!step) return;
  event.preventDefault();
  turnPageBy(step);
}

function focusViewer(event: globalThis.MouseEvent): void {
  if (isEditableKeyTarget(event.target)) return;
  if (event.target instanceof globalThis.HTMLElement && event.target.closest('button')) return;
  stageElement()?.focus({ preventScroll: true });
}

onMounted(() => {
  resizeObserver = new globalThis.ResizeObserver(() => {
    if (resizeTimer) globalThis.clearTimeout(resizeTimer);
    resizeTimer = globalThis.setTimeout(() => {
      const el = stageElement();
      if (!el || !currentPage.value) return;
      if (isContinuous.value) {
        const width = Math.max(1, el.clientWidth - 20);
        if (!pdfStageSizeChanged(lastFitWidth, 0, width, 0)) return;
        lastFitWidth = width;
        applyContinuousMetrics();
        void syncContinuousWindow(true);
        return;
      }
      const width = Math.max(1, el.clientWidth - 20);
      const height = pdfPageAvailableHeight(el.clientHeight - 20, (globalThis.innerHeight || 800) - 140);
      if (!pdfStageSizeChanged(lastFitWidth, lastFitHeight, width, height)) return;
      void goToPage(currentPage.value, true);
    }, 160);
  });
  const rootEl = nativeElement(root.value);
  if (rootEl) resizeObserver.observe(rootEl);
  const el = stageElement();
  if (el && !isContinuous.value) el.addEventListener('wheel', onStageWheel, { passive: false });
  if (isContinuous.value) globalThis.addEventListener('scroll', onWindowScroll, { passive: true, capture: true });
  ensureScrollContainer();
  globalThis.addEventListener('scroll', syncScrollAssist, { passive: true, capture: true });
  globalThis.addEventListener('resize', syncScrollAssist);
  globalThis.addEventListener('mouseup', stopScroll);
  globalThis.addEventListener('blur', stopScroll);
  globalThis.document.addEventListener('visibilitychange', stopScroll);
  // 页数与缩放会显著改变滚动高度，用尺寸观察器统一同步控件显示状态。
  if (rootEl && typeof globalThis.ResizeObserver !== 'undefined') {
    assistObserver = new globalThis.ResizeObserver(syncScrollAssist);
    assistObserver.observe(rootEl);
  }
  syncScrollAssist();
  void loadDocument();
});

watch(
  () => [props.url, props.data, props.initialPageNo],
  () => void loadDocument()
);

// 页数、缩放与旋转都会改变滚动总高度，需要重新解析滚动载体并刷新辅助控件状态。
watch([totalPages, zoom, rotation], () => {
  scrollResolved = false;
  void nextTick(syncScrollAssist);
  globalThis.setTimeout(syncScrollAssist, 240);
});

onBeforeUnmount(() => {
  renderUiVersion += 1;
  resizeObserver?.disconnect();
  assistObserver?.disconnect();
  stopScroll();
  if (resizeTimer) globalThis.clearTimeout(resizeTimer);
  stageElement()?.removeEventListener('wheel', onStageWheel);
  globalThis.removeEventListener('scroll', onWindowScroll, { capture: true } as globalThis.EventListenerOptions);
  globalThis.removeEventListener('scroll', syncScrollAssist, { capture: true } as globalThis.EventListenerOptions);
  globalThis.removeEventListener('resize', syncScrollAssist);
  globalThis.removeEventListener('mouseup', stopScroll);
  globalThis.removeEventListener('blur', stopScroll);
  globalThis.document.removeEventListener('visibilitychange', stopScroll);
  releaseContinuousWindow();
  void session.destroy();
});
</script>

<template>
  <view
    ref="root"
    class="pdf-preview"
    :class="{ 'is-continuous': isContinuous }"
    :aria-label="`${fileName || 'PDF 文档'}预览`"
    @keydown="onViewerKeydown"
  >
    <view class="pdf-sticky-toolbar">
      <view class="pdf-toolbar">
        <button :disabled="currentPage <= 1 || pageLoading" @click="goToPage(currentPage - 1)">上一页</button>
        <input v-model.number="pageInput" class="page-input" type="number" :min="1" :max="totalPages || 1" @confirm="goToPage(pageInput)">
        <text>/ {{ totalPages || '—' }}</text>
        <button :disabled="!totalPages || currentPage >= totalPages || pageLoading" @click="goToPage(currentPage + 1)">下一页</button>
      </view>
      <view class="pdf-toolbar pdf-tools">
        <button :disabled="zoom <= MIN_ZOOM" @click="changeZoom(-ZOOM_STEP)">缩小</button>
        <text>{{ Math.round(zoom * 100) }}%</text>
        <button :disabled="zoom >= MAX_ZOOM" @click="changeZoom(ZOOM_STEP)">放大</button>
        <button :disabled="!currentPage" @click="rotate">旋转</button>
      </view>
    </view>
    <view
      ref="stage"
      class="pdf-stage"
      :class="{ 'is-zoomed': zoom > 1 && !isContinuous, 'is-continuous': isContinuous }"
      tabindex="0"
      @click="focusViewer"
    >
      <NativePdfCanvas v-if="!isContinuous" class="pdf-canvas" :page-no="0" @ready="bindPageCanvas" @dispose="releasePageCanvas" />
      <view v-else class="pdf-scroll-spacer" :style="{ height: `${scrollHeightPx}px` }">
        <view
          v-for="visiblePageNo in visiblePageNos"
          :key="visiblePageNo"
          class="pdf-page-slot"
          :style="{ top: `${pdfContinuousPageOffset(visiblePageNo, pageStridePx)}px`, height: `${displayHeightPx}px` }"
        >
          <NativePdfCanvas class="pdf-canvas" :page-no="visiblePageNo" @ready="bindContinuousCanvas" @dispose="releaseContinuousPage" />
        </view>
      </view>
      <view v-if="loading || (pageLoading && !isContinuous)" class="pdf-overlay">
        <text>{{ loading ? '正在打开 PDF…' : `正在渲染第 ${pageInput} 页…` }}</text>
      </view>
      <view v-else-if="passwordRequired" class="pdf-overlay password-panel">
        <text>{{ passwordIncorrect ? 'PDF 密码不正确' : '该 PDF 需要密码' }}</text>
        <input v-model="password" type="password" placeholder="请输入 PDF 密码" @confirm="submitPassword">
        <button :disabled="!password" @click="submitPassword">确认</button>
      </view>
      <view v-else-if="errorMessage" class="pdf-overlay error-panel">
        <text>{{ errorMessage }}</text>
        <button @click="loadDocument">重试</button>
        <button @click="emit('fallback')">受控打开或下载</button>
      </view>
    </view>
    <view v-if="canScroll" class="scroll-assist" role="group" aria-label="页面滚动辅助">
      <button class="scroll-assist__button" aria-label="一键回到最顶端" @click.stop="backToTop">
        <view class="scroll-assist__icon scroll-assist__icon--line-start">
          <view class="scroll-assist__glyph scroll-assist__glyph--up" />
        </view>
      </button>
      <button
        class="scroll-assist__button"
        :class="{ 'scroll-assist__button--holding': holding === -1 }"
        aria-label="按住持续向上滚动"
        @touchstart.stop.prevent="startScroll(-1)"
        @touchend="stopScroll"
        @touchcancel="stopScroll"
        @mousedown.stop.prevent="startScroll(-1)"
        @mouseup="stopScroll"
        @mouseleave="stopScroll"
      >
        <view class="scroll-assist__icon">
          <view class="scroll-assist__glyph scroll-assist__glyph--up" />
        </view>
      </button>
      <button
        class="scroll-assist__button"
        :class="{ 'scroll-assist__button--holding': holding === 1 }"
        aria-label="按住持续向下滚动"
        @touchstart.stop.prevent="startScroll(1)"
        @touchend="stopScroll"
        @touchcancel="stopScroll"
        @mousedown.stop.prevent="startScroll(1)"
        @mouseup="stopScroll"
        @mouseleave="stopScroll"
      >
        <view class="scroll-assist__icon">
          <view class="scroll-assist__glyph" />
        </view>
      </button>
      <button class="scroll-assist__button" aria-label="一键到最低端" @click.stop="scrollToEdge('bottom')">
        <view class="scroll-assist__icon scroll-assist__icon--line-end">
          <view class="scroll-assist__glyph" />
        </view>
      </button>
    </view>
  </view>
</template>

<style scoped lang="less">
.pdf-preview {
  display: flex;
  width: 100%;
  height: 100%;
  min-height: 620rpx;
  flex: 1;
  flex-direction: column;
  background: #f6f7f9;
}

.pdf-preview.is-continuous {
  height: auto;
}

.pdf-sticky-toolbar {
  position: sticky;
  top: 44px;
  z-index: 12;
  box-shadow: 0 4rpx 14rpx rgb(38 39 45 / 7%);
}

.pdf-toolbar {
  display: flex;
  min-height: 64rpx;
  align-items: center;
  justify-content: center;
  gap: 10rpx;
  padding: 8rpx 20rpx;
  border-bottom: 1rpx solid #edf0f6;
  background: #fff;
  color: #2f3747;
}

.pdf-toolbar button {
  min-width: 88rpx;
  margin: 0;
  padding: 0 14rpx;
  border: 1rpx solid #ccd4e0;
  border-radius: 8rpx;
  background: #fff;
  color: #2f3747;
  font-size: 22rpx;
  line-height: 46rpx;
}

.pdf-toolbar button[disabled] {
  opacity: 0.4;
}

.page-input {
  width: 82rpx;
  height: 46rpx;
  border: 1rpx solid #ccd4e0;
  border-radius: 8rpx;
  background: #fff;
  text-align: center;
}

.pdf-tools {
  min-height: 58rpx;
}

.pdf-tools button {
  min-width: 76rpx;
  line-height: 42rpx;
}

.pdf-stage {
  position: relative;
  display: flex;
  min-height: 480rpx;
  flex: 1;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  padding: 10rpx;
}

.pdf-stage:focus {
  outline: none;
}

.pdf-stage.is-zoomed {
  overflow: auto;
}

.pdf-stage.is-continuous {
  display: block;
  overflow-x: auto;
  overflow-y: clip;
  padding: 12rpx 0 24rpx;
}

.pdf-scroll-spacer {
  position: relative;
  width: 100%;
}

.pdf-page-slot {
  position: absolute;
  left: 0;
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: center;
}

.pdf-canvas {
  display: block;
  max-width: none;
  margin: 0;
  background: #fff;
  box-shadow: 0 6rpx 24rpx rgb(38 39 45 / 10%);
}

.pdf-overlay {
  position: absolute;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 20rpx;
  padding: 36rpx;
  background: rgb(246 247 249 / 94%);
  color: #676971;
  inset: 0;
}

.password-panel input {
  width: 420rpx;
  height: 68rpx;
  padding: 0 18rpx;
  border: 1rpx solid #ccd4e0;
  border-radius: 12rpx;
  background: #fff;
}

.error-panel {
  color: #d92d20;
}

/* 与 PC 学习页同构的滚动辅助：置顶 / 上移（长按连续）/ 下移（长按连续）/ 置底。 */
.scroll-assist {
  position: fixed;
  z-index: 18;
  right: 24rpx;
  bottom: calc(40rpx + env(safe-area-inset-bottom));
  display: flex;
  overflow: hidden;
  flex-direction: column;
  border: 1rpx solid #dbe2ee;
  border-radius: 22rpx;
  background: rgb(255 255 255 / 96%);
  box-shadow: 0 10rpx 28rpx rgb(15 23 42 / 16%);
}

.scroll-assist__button {
  display: flex;
  width: 92rpx;
  height: 78rpx;
  align-items: center;
  justify-content: center;
  margin: 0;
  padding: 0;
  border: 0;
  border-bottom: 1rpx solid #edf0f6;
  border-radius: 0;
  background: transparent;
  color: #4e6fae;
  line-height: 1;
  user-select: none;
}

/* uni-app 的 button 默认带一条 ::after 边框，会破坏胶囊分隔线。 */
.scroll-assist__button::after {
  border: 0;
}

.scroll-assist__button:last-child {
  border-bottom: 0;
}

.scroll-assist__button--holding {
  background: #eef3ff;
  color: #2563eb;
}

.scroll-assist__icon {
  position: relative;
  display: flex;
  width: 34rpx;
  height: 34rpx;
  align-items: center;
  justify-content: center;
}

.scroll-assist__icon--line-start::before,
.scroll-assist__icon--line-end::before {
  content: '';
  position: absolute;
  left: 5rpx;
  width: 24rpx;
  height: 4rpx;
  border-radius: 2rpx;
  background: currentColor;
}

.scroll-assist__icon--line-start::before {
  top: 2rpx;
}

.scroll-assist__icon--line-end::before {
  bottom: 2rpx;
}

/* 用边框拼箭头，避免依赖字体字形；旋转后用 translate 把视觉重心拉回中心。 */
.scroll-assist__glyph {
  width: 20rpx;
  height: 20rpx;
  border-style: solid;
  border-color: currentColor;
  border-width: 0 4rpx 4rpx 0;
  transform: translate(0, -7rpx) rotate(45deg);
}

.scroll-assist__glyph--up {
  transform: translate(0, 7rpx) rotate(-135deg);
}
</style>
