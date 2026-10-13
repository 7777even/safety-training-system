import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const ASSET_CONSTANTS_URL = new URL('../src/constants/asset.ts', import.meta.url);
const ASSET_PAGE_URL = new URL('../src/views/admin/business-center/resource-library/index.vue', import.meta.url);
const UPLOAD_COMPONENT_URL = new URL('../src/components/file-upload/UnifiedFileUpload.vue', import.meta.url);
const ASSET_VIEWER_URL = new URL('../src/components/asset-viewer/AssetViewer.vue', import.meta.url);

async function importTypeScriptModule(url) {
  const source = await readFile(url, 'utf8');
  const output = ts.transpileModule(source, {
    compilerOptions: {module: ts.ModuleKind.ES2022, target: ts.ScriptTarget.ES2022}
  }).outputText;
  return import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}#${Math.random()}`);
}

test('素材原始文件按类型限制音视频 2GB、文档 512MB、图片 16MB', async () => {
  const constants = await importTypeScriptModule(ASSET_CONSTANTS_URL);
  const twoGb = 2 * 1024 * 1024 * 1024;
  const docLimit = 512 * 1024 * 1024;
  const imageLimit = 16 * 1024 * 1024;

  assert.equal(constants.ASSET_ORIGINAL_FILE_MAX_BYTES.VIDEO, twoGb);
  assert.equal(constants.ASSET_ORIGINAL_FILE_MAX_BYTES.AUDIO, twoGb);
  assert.equal(constants.ASSET_ORIGINAL_FILE_MAX_BYTES.DOCUMENT, docLimit);
  assert.equal(constants.ASSET_ORIGINAL_FILE_MAX_BYTES.IMAGE, imageLimit);
  assert.equal(constants.getAssetOriginalFileSizeError('VIDEO', twoGb), undefined);
  assert.equal(constants.getAssetOriginalFileSizeError('VIDEO', twoGb + 1), '音视频原始文件不能超过 2GB');
  assert.equal(constants.getAssetOriginalFileSizeError('DOCUMENT', docLimit + 1), '文档原始文件不能超过 512MB');
  assert.equal(constants.getAssetOriginalFileSizeError('IMAGE', imageLimit + 1), '图片原始文件不能超过 16MB');
  assert.equal(constants.getAssetOriginalFileSizeError('RICH_TEXT', imageLimit + 1), undefined);
});

test('素材库上传入口展示并执行原始文件大小预检', async () => {
  const pageSource = await readFile(ASSET_PAGE_URL, 'utf8');
  const uploadSource = await readFile(UPLOAD_COMPONENT_URL, 'utf8');
  for (const marker of [
    'ASSET_ORIGINAL_FILE_MAX_BYTES',
    'getAssetOriginalFileSizeError',
    'getAssetOriginalFileSizeHint',
    ':max-file-size="originalFileMaxBytes"',
    ':size-limit-hint="originalFileSizeHint"',
    'asset-library__file-hint',
    ':disabled="form.fileInfoId !== undefined"',
    'clearUploadedFile',
    '清除文件',
    'asset-library__file-row',
    '同一类型可直接重新上传'
  ]) {
    assert.equal(pageSource.includes(marker), true, `页面应包含 ${marker}`);
  }
  assert.equal(uploadSource.includes('maxFileSize'), true);
  assert.equal(uploadSource.includes('sizeLimitHint'), true);
  assert.equal(uploadSource.includes('原始文件超过允许大小'), true);
});

test('单文件上传必须操作队列里的 Vue 代理对象，且打开弹窗时不得清空正在上传的文件', async () => {
  const uploadSource = await readFile(UPLOAD_COMPONENT_URL, 'utf8');
  assert.match(uploadSource, /queue\.value\.push\(createQueueItem\(file\)\)/);
  assert.match(uploadSource, /const item = queue\.value\[0\]/);
  assert.match(uploadSource, /入队后必须操作 Vue 已代理的对象/);
  assert.match(uploadSource, /:close-on-click-modal="false"/);
  assert.equal(uploadSource.includes('destroy-on-close'), false);
  assert.equal(uploadSource.includes('if (props.mode === \'dialog\' && visible) resetQueue'), false);
});

test('上传预览向共享查看器传入受控高度，避免固定 800px 撑破弹窗', async () => {
  const [uploadSource, viewerSource] = await Promise.all([readFile(UPLOAD_COMPONENT_URL, 'utf8'), readFile(ASSET_VIEWER_URL, 'utf8')]);

  assert.match(uploadSource, /preview-height="360px"/);
  assert.match(viewerSource, /previewHeight\?: string/);
  assert.match(viewerSource, /:height="previewHeight"/);
  assert.doesNotMatch(viewerSource, /height="800px"/);
});

test('关闭素材抽屉后必须清空已上传文件状态', async () => {
  const pageSource = await readFile(ASSET_PAGE_URL, 'utf8');
  assert.match(pageSource, /fileInfoId:\s*undefined/);
  assert.match(pageSource, /function handleDrawerClosed\(\): void/);
  assert.match(pageSource, /Object\.assign\(form, newAssetForm\(\)\)/);
  assert.match(pageSource, /@closed="handleDrawerClosed"/);
});
