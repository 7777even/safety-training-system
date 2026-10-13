<script setup lang="ts">
import {computed, nextTick, onBeforeUnmount, onMounted, ref, watch, type DirectiveBinding} from 'vue';
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
import type {PDFDocumentLoadingTask, PDFDocumentProxy, PDFPageProxy, RenderTask} from 'pdfjs-dist';
import * as pdfjsLib from 'pdfjs-dist';
import pdfWorker from 'pdfjs-dist/build/pdf.worker.min.mjs?url';

const props = defineProps<{
  url?: string;
  file?: globalThis.Blob | null;
  fileName?: string;
  initialPageNo?: number | null;
  httpHeaders?: Record<string, string>;
  layout?: 'page' | 'continuous';
}>();

const emit = defineEmits<{
  error: [message: string];
  loaded: [];
  pagecount: [pageCount: number];
  pageupdate: [pageNo: number];
  fallback: [];
}>();

const MIN_ZOOM = 0.5;
const MAX_ZOOM = 3;
const ZOOM_STEP = 0.2;
const MAX_CANVAS_PIXELS = 16_777_216;

pdfjsLib.GlobalWorkerOptions.workerSrc = pdfWorker;

const root = ref<globalThis.HTMLElement>();
const stage = ref<globalThis.HTMLElement>();
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
let updatePassword: ((value: string) => void) | undefined;
let resizeObserver: globalThis.ResizeObserver | undefined;
let resizeTimer: ReturnType<typeof globalThis.setTimeout> | undefined;
let lastWheelPageAt = 0;
let pendingPageStep: -1 | 0 | 1 = 0;
let lastFitWidth = 0;
let lastFitHeight = 0;
const isContinuous = computed(() => props.layout === 'continuous');
const visiblePageNos = ref<number[]>([]);
const scrollHeightPx = ref(0);
const displayHeightPx = ref(1);
const pageStridePx = ref(1);
const continuousCanvases = new Map<number, globalThis.HTMLCanvasElement>();
const continuousPages = new Map<number, PDFPageProxy>();
const continuousRenders = new Map<number, RenderTask>();
const continuousTokens = new Map<number, number>();
let continuousSyncVersion = 0;
let basePageWidth = 0;
let basePageHeight = 0;
let restoringContinuousPage = false;

type LoadingTask = PDFDocumentLoadingTask & PdfLoadingTaskLike<PDFDocumentProxy>;

/** 创建当前页 Canvas 渲染任务，按预览区完整放下当前页，像素上限用于防止超大页面耗尽内存。 */
function renderPdfPage(pageLike: PDFPageProxy): PdfRenderTaskLike {
  const targetCanvas = canvas.value;
  if (!targetCanvas) throw new Error('PDF 画布尚未初始化');
  const baseViewport = pageLike.getViewport({scale: 1, rotation: rotation.value});
  const availableWidth = Math.max(1, (stage.value?.clientWidth ?? 600) - 16);
  const availableHeight = pdfPageAvailableHeight(
    (stage.value?.clientHeight ?? 0) - 16,
    (globalThis.innerHeight || 800) - 140
  );
  lastFitWidth = availableWidth;
  lastFitHeight = availableHeight;
  const fitScale = pdfPageFitScale(baseViewport.width, baseViewport.height, availableWidth, availableHeight);
  const viewport = pageLike.getViewport({scale: fitScale * zoom.value, rotation: rotation.value});
  const context = targetCanvas.getContext('2d');
  if (!context) throw new Error('浏览器不支持 PDF Canvas 渲染');
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
    promise: task.promise,
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

/** 将 PDF.js 异常收敛成稳定中文状态。 */
function readableError(error: unknown): string {
  const name = error instanceof Error ? error.name : '';
  if (name === 'PasswordException') return 'PDF 密码不正确或未提供密码。';
  if (name === 'InvalidPDFException' || name === 'FormatError') return 'PDF 文件已损坏或格式无效。';
  if (name === 'MissingPDFException') return 'PDF 文件不存在或已失效。';
  if (name === 'UnexpectedResponseException') return 'PDF 文件请求失败，请检查访问权限。';
  return error instanceof Error && error.message ? error.message : 'PDF 加载失败，请稍后重试。';
}

async function source(): Promise<{ url?: string; data?: Uint8Array; httpHeaders?: Record<string, string> } | null> {
  if (props.file) return {data: new Uint8Array(await props.file.arrayBuffer())};
  if (props.url) return {url: props.url, httpHeaders: props.httpHeaders};
  return null;
}

function continuousAvailableWidth(): number {
  return Math.max(1, (stage.value?.clientWidth ?? 600) - 16);
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
      // PDF.js 对已结束任务可能拒绝重复取消。
    }
    continuousRenders.delete(pageNo);
  }
  continuousPages.get(pageNo)?.cleanup?.();
  continuousPages.delete(pageNo);
  continuousCanvases.delete(pageNo);
}

function releaseContinuousWindow(): void {
  for (const pageNo of [...continuousCanvases.keys()]) releaseContinuousPage(pageNo);
  visiblePageNos.value = [];
}

async function paintContinuousPage(pageNo: number): Promise<void> {
  const canvas = continuousCanvases.get(pageNo);
  if (!canvas || !basePageWidth) return;
  const token = (continuousTokens.get(pageNo) ?? 0) + 1;
  continuousTokens.set(pageNo, token);
  const existing = continuousRenders.get(pageNo);
  if (existing) {
    try {
      existing.cancel();
    } catch {
      // 忽略已结束任务。
    }
    continuousRenders.delete(pageNo);
  }
  const page = continuousPages.get(pageNo) ?? await session.acquirePage(pageNo);
  if (!page || continuousTokens.get(pageNo) !== token || !continuousCanvases.has(pageNo)) {
    if (page && !continuousPages.has(pageNo)) page.cleanup?.();
    return;
  }
  continuousPages.set(pageNo, page);
  const scale = pdfContinuousFitWidthScale(basePageWidth, continuousAvailableWidth()) * zoom.value;
  const viewport = page.getViewport({scale, rotation: rotation.value});
  const context = canvas.getContext('2d');
  if (!context) return;
  const requestedOutputScale = Math.min(globalThis.devicePixelRatio || 1, 2);
  const maxOutputScale = Math.sqrt(MAX_CANVAS_PIXELS / Math.max(1, viewport.width * viewport.height));
  const outputScale = Math.max(0.5, Math.min(requestedOutputScale, maxOutputScale));
  canvas.width = Math.max(1, Math.floor(viewport.width * outputScale));
  canvas.height = Math.max(1, Math.floor(viewport.height * outputScale));
  canvas.style.width = `${Math.floor(viewport.width)}px`;
  canvas.style.height = `${Math.floor(viewport.height)}px`;
  const task = page.render({
    canvas,
    canvasContext: context,
    viewport,
    transform: outputScale === 1 ? undefined : [outputScale, 0, 0, outputScale, 0, 0]
  }) as RenderTask;
  continuousRenders.set(pageNo, task);
  try {
    await task.promise;
  } catch {
    // 离开视口或切换缩放时取消属于预期。
  } finally {
    if (continuousRenders.get(pageNo) === task) continuousRenders.delete(pageNo);
  }
}

function bindContinuousCanvas(pageNo: number, el: unknown): void {
  if (!(el instanceof globalThis.HTMLCanvasElement)) return;
  if (continuousCanvases.get(pageNo) === el) return;
  continuousCanvases.set(pageNo, el);
}

/** 连续阅读页用稳定指令绑定 Canvas，避免页码上报触发父组件刷新后反复解绑重绘。 */
const vCoursePdfPageCanvas = {
  mounted(el: globalThis.HTMLCanvasElement, binding: DirectiveBinding<number>) {
    bindContinuousCanvas(binding.value, el);
  },
  updated(el: globalThis.HTMLCanvasElement, binding: DirectiveBinding<number>) {
    if (typeof binding.oldValue === 'number' && binding.oldValue !== binding.value) releaseContinuousPage(binding.oldValue);
    bindContinuousCanvas(binding.value, el);
  },
  unmounted(_el: globalThis.HTMLCanvasElement, binding: DirectiveBinding<number>) {
    releaseContinuousPage(binding.value);
  }
};

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
  const el = stage.value;
  const viewportHeight = globalThis.innerHeight || 800;
  if (!el) return {scrollTop: 0, visibleHeight: viewportHeight};
  const rect = el.getBoundingClientRect();
  return pdfContinuousWindowMetrics(rect.top, el.offsetHeight, viewportHeight);
}

async function syncContinuousWindow(forcePaint = false): Promise<void> {
  const el = stage.value;
  if (!el || !totalPages.value) return;
  applyContinuousMetrics();
  const metrics = continuousWindowMetrics();
  const range = pdfContinuousVisibleRange(
    metrics.scrollTop,
    Math.max(1, metrics.visibleHeight),
    pageStridePx.value,
    totalPages.value
  );
  const nextVisible = pdfContinuousPageList(range.from, range.to);
  const unchanged = !forcePaint
    && nextVisible.length === visiblePageNos.value.length
    && nextVisible.every((pageNo, index) => pageNo === visiblePageNos.value[index]);
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
    await paintContinuousPage(pageNo);
  }
}

function onWindowScroll(): void {
  if (!isContinuous.value || restoringContinuousPage) return;
  void syncContinuousWindow();
}

async function goToContinuousPage(pageNo: number): Promise<void> {
  const el = stage.value;
  if (!el || !totalPages.value) return;
  const target = Math.min(totalPages.value, Math.max(1, Math.floor(pageNo) || 1));
  applyContinuousMetrics();
  restoringContinuousPage = true;
  const pageOffset = pdfContinuousPageOffset(target, pageStridePx.value);
  const stageTop = el.getBoundingClientRect().top + (globalThis.scrollY || 0);
  globalThis.scrollTo({top: Math.max(0, stageTop + pageOffset - 12)});
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
  const viewport = page.getViewport({scale: 1, rotation: rotation.value});
  basePageWidth = viewport.width;
  basePageHeight = viewport.height;
  page.cleanup?.();
  applyContinuousMetrics();
}

/** 打开文档时只加载恢复页，不预取其余页面。 */
async function loadDocument(): Promise<void> {
  await nextTick();
  const documentSource = await source();
  if (!documentSource) return;
  if (!isContinuous.value && !canvas.value) return;
  releaseContinuousWindow();
  loading.value = true;
  pageLoading.value = false;
  pendingPageStep = 0;
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
    await session.open(documentSource, props.initialPageNo ?? 1, {render: !isContinuous.value});
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

/** 分页预览允许输入跳转；学习页平铺预览只读显示当前页。 */
function submitPage(): void {
  if (isContinuous.value) return;
  void goToPage(pageInput.value);
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

function resetZoom(): void {
  zoom.value = 1;
  rotation.value = 0;
  if (!currentPage.value) return;
  if (isContinuous.value) {
    void goToContinuousPage(currentPage.value);
    return;
  }
  void goToPage(currentPage.value);
}

function rotate(delta: number): void {
  rotation.value = (rotation.value + delta + 360) % 360;
  if (!currentPage.value) return;
  if (isContinuous.value) {
    void measureContinuousPageSize().then(() => goToContinuousPage(currentPage.value));
    return;
  }
  void goToPage(currentPage.value);
}

function restoreStageScroll(direction: -1 | 1): void {
  const el = stage.value;
  if (!el) return;
  el.scrollTop = direction > 0 ? 0 : el.scrollHeight;
}

function onStageWheel(event: globalThis.WheelEvent): void {
  if (isContinuous.value) return;
  if (loading.value || passwordRequired.value || errorMessage.value) return;
  const el = stage.value;
  if (!el) return;
  // 默认适配比例下页面应直接翻页，不能受小预览区初始化时的滚动高度误差影响。
  const pageFits = zoom.value <= 1;
  const atTop = pageFits || el.scrollTop <= 1;
  const atBottom = pageFits || el.scrollTop + el.clientHeight >= el.scrollHeight - 1;
  const step = pdfWheelPageDelta(event.deltaY, atTop, atBottom, currentPage.value, totalPages.value);
  if (!step) return;
  event.preventDefault();
  const now = Date.now();
  if (now - lastWheelPageAt < PDF_WHEEL_PAGE_COOLDOWN_MS) return;
  lastWheelPageAt = now;
  void requestPageTurn(step);
}

function isEditableKeyTarget(target: globalThis.EventTarget | null): boolean {
  if (!(target instanceof globalThis.HTMLElement)) return false;
  const tag = target.tagName;
  return tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || target.isContentEditable;
}

/** 渲染期间只保留最后一次翻页方向，完成后继续执行，避免用户输入被直接丢弃。 */
async function requestPageTurn(step: -1 | 1): Promise<void> {
  if (pageLoading.value) {
    pendingPageStep = step;
    return;
  }
  let nextStep: -1 | 0 | 1 = step;
  while (nextStep) {
    const targetPage = Math.min(totalPages.value, Math.max(1, currentPage.value + nextStep));
    if (targetPage === currentPage.value) break;
    await goToPage(targetPage);
    await nextTick();
    restoreStageScroll(nextStep);
    nextStep = pendingPageStep;
    pendingPageStep = 0;
  }
}

function onViewerKeydown(event: globalThis.KeyboardEvent): void {
  if (isContinuous.value) return;
  if (loading.value || passwordRequired.value || errorMessage.value) return;
  if (event.altKey || event.ctrlKey || event.metaKey) return;
  if (isEditableKeyTarget(event.target)) return;
  const step = pdfArrowPageDelta(event.key, currentPage.value, totalPages.value);
  if (!step) return;
  event.preventDefault();
  void requestPageTurn(step);
}

function focusViewer(event: globalThis.MouseEvent): void {
  if (isEditableKeyTarget(event.target)) return;
  if (event.target instanceof globalThis.HTMLElement && event.target.closest('button')) return;
  stage.value?.focus({preventScroll: true});
}

onMounted(() => {
  resizeObserver = new globalThis.ResizeObserver(() => {
    if (resizeTimer) globalThis.clearTimeout(resizeTimer);
    resizeTimer = globalThis.setTimeout(() => {
      const el = stage.value;
      if (!el || !currentPage.value) return;
      if (isContinuous.value) {
        const width = Math.max(1, el.clientWidth - 16);
        if (!pdfStageSizeChanged(lastFitWidth, 0, width, 0)) return;
        lastFitWidth = width;
        applyContinuousMetrics();
        void syncContinuousWindow(true);
        return;
      }
      const width = Math.max(1, el.clientWidth - 16);
      const height = pdfPageAvailableHeight(el.clientHeight - 16, (globalThis.innerHeight || 800) - 140);
      if (!pdfStageSizeChanged(lastFitWidth, lastFitHeight, width, height)) return;
      void goToPage(currentPage.value, true);
    }, 160);
  });
  if (root.value) resizeObserver.observe(root.value);
  if (isContinuous.value) globalThis.addEventListener('scroll', onWindowScroll, {passive: true, capture: true});
  void loadDocument();
});

watch(() => [props.url, props.file, props.initialPageNo], () => void loadDocument());

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  if (resizeTimer) globalThis.clearTimeout(resizeTimer);
  globalThis.removeEventListener('scroll', onWindowScroll, {capture: true} as globalThis.EventListenerOptions);
  releaseContinuousWindow();
  void session.destroy();
});

defineExpose({goToPage});
</script>

<template>
  <section ref="root" class="pdf-viewer" :class="{ 'is-continuous': isContinuous }"
           :aria-label="`${fileName || 'PDF 文档'}预览`" @keydown.capture="onViewerKeydown">
    <header class="pdf-toolbar">
      <div class="page-controls">
        <button type="button" :disabled="true" @click="goToPage(currentPage - 1)">上一页
        </button>
        <input
          v-model.number="pageInput"
          aria-label="PDF 页码"
          type="number"
          min="1"
          :max="totalPages || 1"
          :readonly="isContinuous"
          :tabindex="isContinuous ? -1 : 0"
          @keyup.enter="submitPage"
        >
        <span>/ {{ totalPages || '—' }}</span>
        <button type="button" :disabled="true" @click="goToPage(currentPage + 1)">下一页
        </button>
      </div>
      <div class="zoom-controls">
        <button type="button" :disabled="!currentPage" title="向左旋转" @click="rotate(-90)">↶</button>
        <button type="button" :disabled="zoom <= MIN_ZOOM" @click="changeZoom(-ZOOM_STEP)">−</button>
        <button type="button" :disabled="!currentPage" @click="resetZoom">{{ Math.round(zoom * 100) }}%</button>
        <button type="button" :disabled="zoom >= MAX_ZOOM" @click="changeZoom(ZOOM_STEP)">＋</button>
        <button type="button" :disabled="!currentPage" title="向右旋转" @click="rotate(90)">↷</button>
      </div>
    </header>

    <div ref="stage" class="pdf-stage"
         :class="{ 'is-zoomed': zoom > 1 && !isContinuous, 'is-continuous': isContinuous }"
         tabindex="0" title="点击页面后可使用方向键翻页"
         @pointerdown="focusViewer" @wheel="onStageWheel">
      <canvas v-if="!isContinuous" ref="canvas" class="pdf-canvas"
              :aria-label="currentPage ? `PDF 第 ${currentPage} 页` : 'PDF 页面'"/>
      <div v-else class="pdf-scroll-spacer" :style="{ height: `${scrollHeightPx}px` }">
        <div
          v-for="pageNo in visiblePageNos"
          :key="pageNo"
          class="pdf-page-slot"
          :style="{ top: `${pdfContinuousPageOffset(pageNo, pageStridePx)}px`, height: `${displayHeightPx}px` }"
        >
          <canvas v-course-pdf-page-canvas="pageNo" class="pdf-canvas" :aria-label="`PDF 第 ${pageNo} 页`"/>
        </div>
      </div>
      <div v-if="loading || (pageLoading && !isContinuous)" class="pdf-overlay">
        {{ loading ? '正在打开 PDF…' : '正在渲染第 ' + pageInput + ' 页…' }}
      </div>
      <div v-else-if="passwordRequired" class="pdf-overlay password-panel">
        <strong>{{ passwordIncorrect ? 'PDF 密码不正确' : '该 PDF 需要密码' }}</strong>
        <input v-model="password" aria-label="PDF 密码" type="password" placeholder="请输入 PDF 密码"
               @keyup.enter="submitPassword">
        <button type="button" :disabled="!password" @click="submitPassword">确认</button>
      </div>
      <div v-else-if="errorMessage" class="pdf-overlay error-panel">
        <span>{{ errorMessage }}</span>
        <div class="error-actions">
          <button type="button" @click="loadDocument">重试</button>
          <button type="button" @click="emit('fallback')">受控打开或下载</button>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped lang="less">
.pdf-viewer {
  display: grid;
  width: 100%;
  height: 100%;
  min-height: 320px;
  flex: 1;
  align-self: stretch;
  grid-template-rows: auto minmax(0, 1fr);
  background: #e9edf3;
}

.pdf-toolbar,
.page-controls,
.zoom-controls,
.error-actions {
  display: flex;
  align-items: center;
}

.pdf-toolbar {
  min-height: 48px;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 12px;
  overflow-x: auto;
  padding: 6px 12px;
  border-bottom: 1px solid #d5dbe5;
  background: #fff;
  color: #344054;
}

.page-controls,
.zoom-controls,
.error-actions {
  gap: 8px;
}

.pdf-toolbar button,
.pdf-toolbar input,
.pdf-overlay button,
.pdf-overlay input {
  min-height: 32px;
  box-sizing: border-box;
  border: 1px solid #cfd6e3;
  border-radius: 6px;
  background: #fff;
  color: #344054;
}

.pdf-toolbar button,
.pdf-overlay button {
  padding: 0 10px;
  cursor: pointer;
}

.pdf-toolbar button:disabled,
.pdf-overlay button:disabled {
  cursor: not-allowed;
  opacity: .45;
}

.pdf-toolbar input {
  width: 64px;
  padding: 0 6px;
  text-align: center;
}

.pdf-toolbar input:read-only {
  pointer-events: none;
  background: #f8fafc;
  appearance: textfield;
  cursor: default;
}

.pdf-toolbar input:read-only::-webkit-inner-spin-button,
.pdf-toolbar input:read-only::-webkit-outer-spin-button {
  margin: 0;
  appearance: none;
}

.pdf-stage {
  position: relative;
  display: flex;
  min-height: 0;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  padding: 8px;
}

.pdf-stage:focus {
  outline: 2px solid var(--color-primary);
  outline-offset: -2px;
}

.pdf-stage.is-zoomed {
  overflow: auto;
}

.pdf-viewer.is-continuous {
  display: flex;
  height: auto;
  overflow: visible;
  flex-direction: column;
}

.pdf-viewer.is-continuous .pdf-toolbar {
  position: sticky;
  top: calc(-1 * var(--layout-content-padding));
  z-index: 20;
}

.pdf-stage.is-continuous {
  display: block;
  overflow-x: auto;
  overflow-y: clip;
  padding: 8px 0;
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
  box-shadow: 0 4px 18px rgb(15 23 42 / 16%);
}

.pdf-overlay {
  position: absolute;
  display: grid;
  min-height: 180px;
  place-content: center;
  gap: 12px;
  padding: 24px;
  background: rgb(233 237 243 / 92%);
  color: #475467;
  inset: 0;
}

.pdf-stage.is-continuous .pdf-overlay {
  position: sticky;
  top: 0;
  min-height: 360px;
  height: 50vh;
  inset: auto;
}

.password-panel input {
  width: 220px;
  padding: 0 10px;
}

.error-panel {
  color: var(--color-danger, #d92d20);
}
</style>
