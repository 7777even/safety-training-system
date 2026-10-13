import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';

const assetViewerFile = new URL('../src/components/asset-viewer/AssetViewer.vue', import.meta.url);
const pdfViewerFile = new URL('../src/components/pdf-viewer/PdfViewer.vue', import.meta.url);
const pdfSessionFile = new URL('../../shared/src/libs/pdf-page-session.ts', import.meta.url);

test('PC PDF 使用专用分页组件并退出第三方 PDF 插件链', async () => {
  const [assetViewer, pdfViewer, pdfSession] = await Promise.all([
    readFile(assetViewerFile, 'utf8'),
    readFile(pdfViewerFile, 'utf8'),
    readFile(pdfSessionFile, 'utf8')
  ]);

  assert.match(assetViewer, /import PdfViewer/);
  assert.match(assetViewer, /<PdfViewer/);
  assert.doesNotMatch(assetViewer, /pdfPlugin/);
  assert.doesNotMatch(assetViewer, /MutationObserver|ofv-pdf-page-wrapper|getBoundingClientRect/);
  assert.match(pdfViewer, /PdfPageSession/);
  assert.match(pdfViewer, /createPdfjsConcurrentRangeAdapter/);
  assert.match(pdfSession, /disableRange|disableStream|disableAutoFetch|rangeChunkSize/);
  assert.match(pdfViewer, /initialPageNo|pagecount|pageupdate|goToPage/);
  assert.match(pdfViewer, /MAX_CANVAS_PIXELS/);
  assert.match(pdfViewer, /task\.cancel|session\.destroy/);
  assert.match(pdfViewer, /pdfWheelPageDelta/);
  assert.match(pdfViewer, /pdfArrowPageDelta/);
  assert.match(pdfViewer, /pdfContinuousVisibleRange/);
  assert.match(pdfViewer, /layout\?: 'page' \| 'continuous'/);
  assert.match(pdfViewer, /pdfPageFitScale/);
  assert.match(pdfViewer, /pdfPageAvailableHeight/);
  assert.match(pdfViewer, /pdfStageSizeChanged/);
  assert.match(pdfViewer, /@wheel="onStageWheel"/);
  assert.doesNotMatch(pdfViewer, /addEventListener\('wheel'/);
  assert.match(pdfViewer, /onViewerKeydown/);
  assert.match(pdfViewer, /@keydown\.capture="onViewerKeydown"/);
  assert.match(pdfViewer, /@pointerdown="focusViewer"/);
  assert.match(pdfViewer, /const pageFits = zoom\.value <= 1/);
  assert.match(pdfViewer, /function onStageWheel[\s\S]*if \(isContinuous\.value\) return/);
  assert.match(pdfViewer, /let pendingPageStep: -1 \| 0 \| 1 = 0/);
  assert.match(pdfViewer, /async function requestPageTurn\(step: -1 \| 1\): Promise<void>/);
  assert.match(pdfViewer, /if \(pageLoading\.value\) \{[\s\S]*pendingPageStep = step/);
  assert.doesNotMatch(pdfViewer, /task\.promise\.finally\([\s\S]*pageLoading\.value = false/);
  assert.doesNotMatch(pdfViewer, /function turnPageBy/);
  assert.match(pdfViewer, /tabindex="0"/);
  assert.match(pdfViewer, /title="点击页面后可使用方向键翻页"/);
  assert.match(pdfViewer, /\.pdf-stage:focus \{[\s\S]*outline: 2px solid var\(--color-primary\)/);
  assert.match(pdfViewer, /is-continuous/);
  assert.match(pdfViewer, /pdfContinuousWindowMetrics/);
  assert.match(pdfViewer, /addEventListener\('scroll'/);
  assert.match(pdfViewer, /v-course-pdf-page-canvas/);
  assert.doesNotMatch(pdfViewer, /:ref="\([^)]+?\) => bindContinuousCanvas/);
  assert.match(pdfViewer, /const pagesToPaint = forcePaint \? nextVisible : nextVisible\.filter/);
  assert.match(pdfViewer, /overflow-x:\s*auto/);
  assert.match(pdfViewer, /:readonly="isContinuous"/);
  assert.match(pdfViewer, /\.pdf-viewer\.is-continuous \.pdf-toolbar[\s\S]*position:\s*sticky/);
  assert.match(pdfViewer, /top:\s*calc\(-1 \* var\(--layout-content-padding\)\)/);
});

test('PDF 请求凭证只通过 header 传递且不拼接到 URL', async () => {
  const pdfViewer = await readFile(pdfViewerFile, 'utf8');

  assert.match(pdfViewer, /httpHeaders/);
  assert.doesNotMatch(pdfViewer, /[?&](token|access_token)=/i);
});
