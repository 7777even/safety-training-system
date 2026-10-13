import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const ASSET_MOCK_URL = new URL('../src/mock/asset.ts', import.meta.url);

/** 编译无外部运行时依赖的素材契约 Mock，作为公开测试缝。 */
async function loadAssetMock() {
  const source = await readFile(ASSET_MOCK_URL, 'utf8');
  const output = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.ES2022, target: ts.ScriptTarget.ES2022 }
  }).outputText;
  return import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}#${Math.random()}`);
}

test('素材 Mock 支持组合筛选、分页以及 CRUD', async () => {
  const mock = await loadAssetMock();
  mock.resetAssetMockState();

  const page = await mock.mockGetAssetPage({
    pageNum: 1,
    pageSize: 10,
    'query.assetType': 'VIDEO',
    'query.processStatus': 'FAILED',
    'query.status': 1,
    'query.assetName': '消防'
  });
  assert.equal(page.total, 1);

  const assetId = await mock.mockCreateAsset({
    assetCode: 'AST-LINK-NEW',
    assetName: '外部安全资料',
    assetType: 'LINK',
    sourceType: 'EXTERNAL',
    externalUrl: 'https://example.com/safety',
    status: 1
  });
  const created = await mock.mockGetAssetDetail(assetId);
  assert.equal(created.processStatus, 'SUCCEEDED');

  await mock.mockUpdateAsset(assetId, { assetName: '外部安全资料（修订）', status: 0, version: created.version });
  assert.equal((await mock.mockGetAssetDetail(assetId)).assetName, '外部安全资料（修订）');
  await mock.mockDeleteAsset(assetId);
  await assert.rejects(mock.mockGetAssetDetail(assetId), (error) => error.response?.data?.code === 6011);
});

test('素材 Mock 校验上传关联并模拟删除保护', async () => {
  const mock = await loadAssetMock();
  mock.resetAssetMockState();

  await assert.rejects(
    mock.mockCreateAsset({ assetCode: 'AST-UPLOAD-MISSING', assetName: '未上传', assetType: 'DOCUMENT', sourceType: 'UPLOAD', fileInfoId: 99999 }),
    (error) => error.response?.data?.code === 6021
  );

  const uploaded = await mock.mockUploadAssetFile(new File(['training'], 'training.pdf', { type: 'application/pdf' }));
  const assetId = await mock.mockCreateAsset({
    assetCode: 'AST-UPLOAD-NEW',
    assetName: '培训资料',
    assetType: 'DOCUMENT',
    sourceType: 'UPLOAD',
    fileInfoId: uploaded.fileInfoId,
    mimeType: 'application/pdf'
  });
  assert.equal((await mock.mockGetAssetDetail(assetId)).fileInfoId, uploaded.fileInfoId);

  mock.syncCourseAssetReferences([20]);
  await assert.rejects(mock.mockDeleteAsset(20), (error) => error.response?.data?.code === 6023);
});

test('素材 Mock 仅允许失败音视频重试并生成可查询转码任务', async () => {
  const mock = await loadAssetMock();
  mock.resetAssetMockState();

  await mock.mockRetryAssetTranscode(20);
  const detail = await mock.mockGetAssetDetail(20);
  const jobs = await mock.mockGetAssetTranscodeJobs(20);
  assert.equal(detail.processStatus, 'PROCESSING');
  assert.equal(jobs.at(-1).jobStatus, 'PENDING');
  assert.equal(jobs.at(-1).retryCount, 1);

  await assert.rejects(mock.mockRetryAssetTranscode(21), (error) => error.response?.data?.code === 6022);
  assert.ok((await mock.mockGetAssetTranscodeJobs()).length >= jobs.length);
});
