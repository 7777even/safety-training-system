import assert from 'node:assert/strict';
import test from 'node:test';
import {
  PDF_RANGE_CHUNK_SIZE,
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
  pdfPageAvailableHeight,
  pdfPageFitScale,
  PdfPageSession,
  pdfStageSizeChanged,
  pdfWheelPageDelta
} from '../src/libs/pdf-page-session.ts';

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return {promise, resolve, reject};
}

async function waitUntil(predicate) {
  while (!predicate()) await new Promise((resolve) => setImmediate(resolve));
}

function createFixture(pageCount = 648) {
  const getPageCalls = [];
  const pages = new Map();
  const document = {
    numPages: pageCount,
    destroyed: false,
    async getPage(pageNo) {
      getPageCalls.push(pageNo);
      const page = {
        pageNo, cleanupCalls: 0, cleanup() {
          this.cleanupCalls += 1;
        }
      };
      pages.set(pageNo, page);
      return page;
    },
    async destroy() {
      this.destroyed = true;
    }
  };
  const loadingTask = {
    promise: Promise.resolve(document),
    destroyed: false,
    async destroy() {
      this.destroyed = true;
    }
  };
  const documentOptions = [];
  const adapter = {
    getDocument(options) {
      documentOptions.push(options);
      return loadingTask;
    }
  };
  return {adapter, document, documentOptions, getPageCalls, loadingTask, pages};
}

test('648 页文档只读取初始目标页，并保留鉴权与 Range 参数', async () => {
  const fixture = createFixture();
  const committedPages = [];
  const session = new PdfPageSession(fixture.adapter, {
    renderPage(page) {
      return {
        promise: Promise.resolve(), cancel() {
        }
      };
    },
    onPageRendered(pageNo) {
      committedPages.push(pageNo);
    }
  });

  await session.open({url: '/api/asset/files/1/2', httpHeaders: {Authorization: 'Bearer token'}}, 1);

  assert.deepEqual(fixture.getPageCalls, [1]);
  assert.deepEqual(committedPages, [1]);
  assert.deepEqual(fixture.documentOptions[0], {
    url: '/api/asset/files/1/2',
    httpHeaders: {Authorization: 'Bearer token'},
    disableRange: false,
    disableStream: true,
    disableAutoFetch: true,
    rangeChunkSize: PDF_RANGE_CHUNK_SIZE,
    useSystemFonts: true
  });
  assert.equal(session.totalPages, 648);
  assert.equal(session.currentPage, 1);
});

test('初始页和跳转页裁剪到文档合法范围', async () => {
  const fixture = createFixture(10);
  const session = new PdfPageSession(fixture.adapter, {
    renderPage() {
      return {
        promise: Promise.resolve(), cancel() {
        }
      };
    }
  });

  await session.open({url: '/document.pdf'}, 99);
  await session.goToPage(-5);

  assert.deepEqual(fixture.getPageCalls, [10, 1]);
  assert.equal(session.currentPage, 1);
});

test('快速跳页取消旧渲染，并只提交最后一页', async () => {
  const fixture = createFixture();
  const pendingRenders = new Map();
  const cancelledPages = [];
  const committedPages = [];
  const session = new PdfPageSession(fixture.adapter, {
    renderPage(page) {
      const pending = deferred();
      pendingRenders.set(page.pageNo, pending);
      return {
        promise: pending.promise,
        cancel() {
          cancelledPages.push(page.pageNo);
          pending.resolve();
        }
      };
    },
    onPageRendered(pageNo) {
      committedPages.push(pageNo);
    }
  });

  const opening = session.open({url: '/document.pdf'}, 1);
  await waitUntil(() => pendingRenders.has(1));
  pendingRenders.get(1).resolve();
  await opening;

  const page300 = session.goToPage(300);
  await waitUntil(() => pendingRenders.has(300));
  const page301 = session.goToPage(301);
  await waitUntil(() => pendingRenders.has(301));
  pendingRenders.get(301).resolve();
  await Promise.all([page300, page301]);

  assert.deepEqual(cancelledPages, [300]);
  assert.deepEqual(committedPages, [1, 301]);
  assert.equal(session.currentPage, 301);
});

test('切换文档和销毁会释放页面、文档、加载任务与渲染任务', async () => {
  const first = createFixture(2);
  const second = createFixture(3);
  let call = 0;
  const adapter = {
    getDocument(options) {
      return (call++ === 0 ? first : second).adapter.getDocument(options);
    }
  };
  const session = new PdfPageSession(adapter, {
    renderPage() {
      return {
        promise: Promise.resolve(), cancel() {
        }
      };
    }
  });

  await session.open({url: '/first.pdf'}, 1);
  await session.open({url: '/second.pdf'}, 2);
  await session.destroy();

  assert.equal(first.pages.get(1).cleanupCalls, 1);
  assert.equal(first.document.destroyed, true);
  assert.equal(first.loadingTask.destroyed, true);
  assert.equal(second.pages.get(2).cleanupCalls, 1);
  assert.equal(second.document.destroyed, true);
  assert.equal(second.loadingTask.destroyed, true);
});

test('切换 URL 后旧加载任务取消不能覆盖新文档或抛出旧错误', async () => {
  const firstLoading = deferred();
  const second = createFixture(5);
  let call = 0;
  const adapter = {
    getDocument(options) {
      if (call++ > 0) return second.adapter.getDocument(options);
      return {
        promise: firstLoading.promise,
        destroy() {
          firstLoading.reject(new Error('旧请求已取消'));
        }
      };
    }
  };
  const session = new PdfPageSession(adapter, {
    renderPage() {
      return {
        promise: Promise.resolve(), cancel() {
        }
      };
    }
  });

  const oldOpen = session.open({url: '/old.pdf'}, 1);
  await waitUntil(() => call === 1);
  const newOpen = session.open({url: '/new.pdf'}, 3);

  await assert.doesNotReject(oldOpen);
  await newOpen;
  assert.equal(session.currentPage, 3);
  assert.deepEqual(second.getPageCalls, [3]);
});

test('滚轮在当前页顶部或底部才翻页，中间滚动不换页', () => {
  assert.equal(PDF_WHEEL_PAGE_COOLDOWN_MS, 160);
  assert.equal(pdfWheelPageDelta(80, false, true, 1, 648), 1);
  assert.equal(pdfWheelPageDelta(80, true, true, 1, 648), 1);
  assert.equal(pdfWheelPageDelta(-80, true, false, 2, 648), -1);
  assert.equal(pdfWheelPageDelta(-80, true, true, 2, 648), -1);
  assert.equal(pdfWheelPageDelta(80, false, false, 1, 648), 0);
  assert.equal(pdfWheelPageDelta(-80, false, false, 2, 648), 0);
  assert.equal(pdfWheelPageDelta(80, false, true, 648, 648), 0);
  assert.equal(pdfWheelPageDelta(-80, true, false, 1, 648), 0);
});

test('方向键在合法页码范围内翻页', () => {
  assert.equal(pdfArrowPageDelta('ArrowRight', 1, 648), 1);
  assert.equal(pdfArrowPageDelta('ArrowDown', 1, 648), 1);
  assert.equal(pdfArrowPageDelta('ArrowLeft', 2, 648), -1);
  assert.equal(pdfArrowPageDelta('ArrowUp', 2, 648), -1);
  assert.equal(pdfArrowPageDelta('ArrowLeft', 1, 648), 0);
  assert.equal(pdfArrowPageDelta('ArrowUp', 1, 648), 0);
  assert.equal(pdfArrowPageDelta('ArrowRight', 648, 648), 0);
  assert.equal(pdfArrowPageDelta('ArrowDown', 648, 648), 0);
  assert.equal(pdfArrowPageDelta('Enter', 1, 648), 0);
});

test('预览区尚未撑开时按视口高度预算适配，避免先缩成窄条', () => {
  assert.equal(pdfPageAvailableHeight(288, 900), 900);
  assert.equal(pdfPageAvailableHeight(600, 900), 600);
  assert.equal(pdfPageAvailableHeight(1200, 900), 900);
});

test('预览缩放按预览区宽高同时适配，始终放下完整一页', () => {
  const shortStage = pdfPageFitScale(612, 792, 900, 400);
  assert.ok(792 * shortStage <= 400);
  assert.equal(shortStage, 400 / 792);

  const narrowStage = pdfPageFitScale(612, 792, 300, 2000);
  assert.ok(612 * narrowStage <= 300);
  assert.equal(narrowStage, 300 / 612);

  const largeStage = pdfPageFitScale(612, 792, 1600, 1200);
  assert.ok(612 * largeStage <= 1600);
  assert.ok(792 * largeStage <= 1200);
  assert.equal(largeStage, Math.min(1600 / 612, 1200 / 792));
});

test('预览区尺寸抖动不超过阈值时不重新适配', () => {
  assert.equal(pdfStageSizeChanged(800, 600, 801, 600), false);
  assert.equal(pdfStageSizeChanged(800, 600, 800, 607), false);
  assert.equal(pdfStageSizeChanged(800, 600, 820, 600), true);
  assert.equal(pdfStageSizeChanged(800, 600, 800, 580), true);
});

test('连续平铺只按视口取附近页，648 页也不会展开全部 DOM 窗口', () => {
  const stride = pdfContinuousPageStride(800, 12);
  assert.equal(stride, 812);
  assert.equal(pdfContinuousFitWidthScale(612, 900), 900 / 612);
  assert.equal(pdfContinuousPageOffset(12, 812), 11 * 812);
  assert.equal(pdfContinuousCurrentPage(0, 812, 648), 1);
  assert.equal(pdfContinuousCurrentPage(812, 812, 648), 2);
  assert.equal(pdfContinuousCurrentPage(812 * 647, 812, 648), 648);

  const firstWindow = pdfContinuousVisibleRange(0, 600, 812, 648);
  assert.deepEqual(firstWindow, {from: 1, to: 2});
  assert.deepEqual(pdfContinuousPageList(firstWindow.from, firstWindow.to), [1, 2]);

  const midWindow = pdfContinuousVisibleRange(812 * 100, 600, 812, 648);
  assert.equal(midWindow.to - midWindow.from + 1 <= 6, true);
  assert.ok(midWindow.to - midWindow.from + 1 >= 2);
  assert.ok(midWindow.from >= 100);
  assert.ok(midWindow.to <= 104);

  const totalHeight = pdfContinuousTotalHeight(648, 800, 12);
  assert.equal(totalHeight, 648 * 800 + 647 * 12);

  const atTop = pdfContinuousWindowMetrics(0, totalHeight, 700);
  assert.equal(atTop.scrollTop, 0);
  assert.equal(atTop.visibleHeight, 700);
  const scrolled = pdfContinuousWindowMetrics(-1624, totalHeight, 700);
  assert.equal(scrolled.scrollTop, 1624);
  assert.equal(scrolled.visibleHeight, 700);
  const entering = pdfContinuousWindowMetrics(200, totalHeight, 700);
  assert.equal(entering.scrollTop, 0);
  assert.equal(entering.visibleHeight, 500);
});

test('连续平铺打开文档时不预读全部页面，只按需 acquirePage', async () => {
  const fixture = createFixture(648);
  const session = new PdfPageSession(fixture.adapter, {
    renderPage() {
      return {
        promise: Promise.resolve(), cancel() {
        }
      };
    }
  });

  await session.open({url: '/doc.pdf'}, 12, {render: false});
  assert.equal(session.totalPages, 648);
  assert.equal(session.currentPage, 12);
  assert.deepEqual(fixture.getPageCalls, []);

  const page = await session.acquirePage(12);
  assert.equal(page.pageNo, 12);
  assert.deepEqual(fixture.getPageCalls, [12]);
  await session.destroy();
});
