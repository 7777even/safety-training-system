import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const ASSET_API_URL = new URL('../src/api/asset.ts', import.meta.url);
const ASSET_CONSTANTS_URL = new URL('../src/constants/asset.ts', import.meta.url);
const ASSET_PAGE_URL = new URL('../src/views/admin/business-center/resource-library/index.vue', import.meta.url);

/** 将无运行时内部依赖的 TypeScript 模块编译为 Node 可加载的公开测试缝。 */
async function importTypeScriptModule(url, replacements = []) {
  let source = await readFile(url, 'utf8');
  replacements.forEach(([pattern, value]) => {
    source = source.replace(pattern, value);
  });
  const output = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.ES2022, target: ts.ScriptTarget.ES2022 }
  }).outputText;
  return import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}#${Math.random()}`);
}

/** 通过注入请求边界加载素材 API，验证调用方可观察到的 Contract 行为。 */
async function loadAssetApi(responses) {
  const requests = [];
  globalThis.__ASSET_CONTRACT_MOCK_ENABLED__ = false;
  globalThis.__assetRequest = async (config) => {
    requests.push(config);
    return responses.shift();
  };
  const api = await importTypeScriptModule(ASSET_API_URL, [
    [/import type \{ ApiResponse \} from '@shared\/types';/, ''],
    [/import \{ sha256Bytes \} from '@shared\/libs\/sha256';/, 'const sha256Bytes = async () => new ArrayBuffer(32);'],
    [/import \{ request \} from '\.\/request';/, 'const request = globalThis.__assetRequest;'],
    [
      /import \{[\s\S]*?\} from '@\/mock\/asset';/,
      'const mockCreateAsset = () => {}; const mockDeleteAsset = () => {}; const mockGetAssetDetail = () => {}; const mockGetAssetPage = () => {}; const mockGetAssetPlayInfo = () => {}; const mockGetAssetTranscodeJobs = () => {}; const mockRetryAssetTranscode = () => {}; const mockUpdateAsset = () => {}; const mockUploadAssetFile = () => {};'
    ]
  ]);
  return { api, requests };
}

test('素材 API 按 Contract 查询分页并解包统一响应', async () => {
  const { api, requests } = await loadAssetApi([
    { code: 0, message: 'success', data: { list: [{ assetId: 20 }], total: 1 } }
  ]);
  const result = await api.getAssetPage({
    pageNum: 2,
    pageSize: 20,
    'query.assetName': '消防',
    'query.assetType': 'VIDEO',
    'query.processStatus': 'FAILED',
    'query.status': 1
  });

  assert.deepEqual(requests, [
    {
      url: '/api/asset/assets',
      method: 'GET',
      params: {
        pageNum: 2,
        pageSize: 20,
        'query.assetName': '消防',
        'query.assetType': 'VIDEO',
        'query.processStatus': 'FAILED',
        'query.status': 1
      }
    }
  ]);
  assert.deepEqual(result, { list: [{ assetId: 20 }], total: 1 });
});

test('Open File Viewer integration keeps private HLS authentication', async () => {
  const viewerSource = await readFile(new URL('../src/components/asset-viewer/AssetViewer.vue', import.meta.url), 'utf8');
  for (const marker of ['OpenFileViewer', 'officePlugin', 'PdfViewer', 'textPlugin', 'loadBinary(info.url)', "format.toLowerCase() === 'hls'"]) {
    assert.equal(viewerSource.includes(marker), true, `viewer should include ${marker}`);
  }
  assert.equal(viewerSource.includes('pdfPlugin'), false, 'PDF 不应继续进入第三方通用插件');
});

test('素材 API 使用 Contract 路径完成详情、创建、更新、删除、重试和任务查询', async () => {
  const { api, requests } = await loadAssetApi([
    { code: 0, message: 'success', data: { assetId: 20, version: 2 } },
    { code: 0, message: 'success', data: 20 },
    { code: 0, message: 'success', data: null },
    { code: 0, message: 'success', data: null },
    { code: 0, message: 'success', data: null },
    { code: 0, message: 'success', data: [{ transcodeJobId: 1 }] }
  ]);
  const createBody = {
    assetCode: 'AST-VIDEO-001',
    assetName: '消防安全课件',
    assetType: 'VIDEO',
    sourceType: 'UPLOAD',
    fileInfoId: 200,
    status: 1
  };
  const updateBody = { assetName: '消防安全课件（修订）', status: 1, version: 2 };

  await api.getAssetDetail(20);
  await api.createAsset(createBody);
  await api.updateAsset(20, updateBody);
  await api.deleteAsset(20);
  await api.retryAssetTranscode(20);
  await api.getAssetTranscodeJobs(20);

  assert.deepEqual(requests, [
    { url: '/api/asset/assets/20', method: 'GET' },
    { url: '/api/asset/assets', method: 'POST', data: createBody },
    { url: '/api/asset/assets/20', method: 'PUT', data: updateBody },
    { url: '/api/asset/assets/20', method: 'DELETE' },
    { url: '/api/asset/assets/20/retry-transcode', method: 'POST' },
    { url: '/api/asset/transcode-jobs', method: 'GET', params: { assetId: 20 } }
  ]);
});

test('素材预览 API 使用现有 play-info Contract 并解包受控地址', async () => {
  const { api, requests } = await loadAssetApi([{ code: 0, message: 'success', data: { assetId: 21, format: 'pdf', url: '/api/asset/files/21/201', coverUrl: null } }]);
  assert.deepEqual(await api.getAssetPlayInfo(21), { assetId: 21, format: 'pdf', url: '/api/asset/files/21/201', coverUrl: null });
  assert.deepEqual(requests, [{ url: '/api/asset/assets/21/play-info', method: 'GET' }]);
});

test('只有失败的音视频素材允许展示转码重试入口', async () => {
  const constants = await importTypeScriptModule(ASSET_CONSTANTS_URL);

  assert.equal(constants.canRetryAssetTranscode({ assetType: 'VIDEO', processStatus: 'FAILED' }), true);
  assert.equal(constants.canRetryAssetTranscode({ assetType: 'AUDIO', processStatus: 'FAILED' }), true);
  assert.equal(constants.canRetryAssetTranscode({ assetType: 'DOCUMENT', processStatus: 'FAILED' }), false);
  assert.equal(constants.canRetryAssetTranscode({ assetType: 'VIDEO', processStatus: 'SUCCEEDED' }), false);
});

test('素材冲突响应转换为用户可理解的删除保护和乐观锁提示', async () => {
  const { api } = await loadAssetApi([]);

  assert.equal(
    api.getAssetErrorMessage({ response: { data: { code: 6023, message: 'conflict', data: null } } }),
    '素材仍被课程引用，不可删除。'
  );
  assert.equal(
    api.getAssetErrorMessage({ response: { data: { code: 2040, message: 'conflict', data: null } } }),
    '数据已被更新，请刷新后重试。'
  );
});

test('素材库页面覆盖筛选分页、权限入口、删除确认、状态刷新和统一预览', async () => {
  const source = await readFile(ASSET_PAGE_URL, 'utf8');

  for (const marker of [
    'DEFAULT_LIST_PAGE_SIZE',
    "'query.assetName'",
    "'query.assetType'",
    "'query.processStatus'",
    "'query.status'",
    "asset:asset:add",
    "asset:asset:edit",
    "asset:asset:delete",
    "asset:job:list",
    'ElMessageBox.confirm',
    'getAssetTranscodeJobs',
    'getAssetPlayInfo',
    'startProcessStatusPolling',
    'SUCCEEDED',
    'destroy-on-close',
    'v-if="previewDrawerVisible"',
    'size="600px"',
    'asset-preview-drawer-header',
    'asset-preview-drawer-body',
    'asset-preview-body',
    'asset-preview-viewer',
    'ASSET_SOURCE_BY_TYPE',
    'ASSET_UPLOAD_ACCEPT',
    'fillUploadedMetadata',
    'QuillEditor',
    'RICH_TEXT_TOOLBAR'
  ]) {
    assert.equal(source.includes(marker), true, `页面应包含 ${marker}`);
  }
  assert.equal(/>\s*预览\s*</.test(source), true);
});

test('统一素材预览覆盖媒体、文档、图片、文本和下载兜底，并为封面请求注入鉴权', async () => {
  const viewerSource = await readFile(new URL('../src/components/asset-viewer/AssetViewer.vue', import.meta.url), 'utf8');
  for (const marker of ['Hls.isSupported()', 'OpenFileViewer', 'officePlugin', 'PdfViewer', 'imagePlugin', 'textPlugin', 'QuillEditor', 'downloadPreview', 'loadCover', ':poster="coverUrl || undefined"', 'video.value?.pause()', "removeAttribute('src')"]) {
    assert.equal(viewerSource.includes(marker), true, `预览组件应包含 ${marker}`);
  }
  assert.equal(viewerSource.includes(':poster="playInfo.coverUrl'), false, '不得把未注入鉴权的 coverUrl 直接交给原生 video');
});

test('rich text and external link previews use asset detail instead of play-info', async () => {
  const pageSource = await readFile(ASSET_PAGE_URL, 'utf8');
  assert.equal(pageSource.includes("item.assetType === 'RICH_TEXT' || item.assetType === 'LINK'"), true);
  assert.equal(pageSource.includes('previewDetail.value = await getAssetDetail(item.assetId)'), true);
  assert.equal(pageSource.includes(':rich-content="previewDetail?.richContent"'), true);
  assert.equal(pageSource.includes(':external-url="previewDetail?.externalUrl"'), true);
});
