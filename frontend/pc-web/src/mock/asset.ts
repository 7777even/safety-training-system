import type {
  AssetCreateRequest,
  AssetDetail,
  AssetPage,
  AssetPageParams,
  AssetPlayInfo,
  AssetTranscodeJob,
  AssetUpdateRequest,
  UploadedFileInfo
} from '@/api/asset';

// TODO replace real api：后端素材接口联调完成后删除本文件和 asset.ts 中的 Mock 分支。

/** 模拟 Axios 业务错误边界，使页面沿用真实接口错误处理。 */
class AssetContractMockError extends Error {
  readonly response: { data: { code: number; message: string; data: null } };

  constructor(code: number, message: string) {
    super(message);
    this.name = 'AssetContractMockError';
    this.response = { data: { code, message, data: null } };
  }
}

const INITIAL_ASSETS: AssetDetail[] = [
  {
    assetId: 20,
    assetCode: 'AST-VIDEO-001',
    assetName: '消防安全警示视频',
    assetType: 'VIDEO',
    sourceType: 'UPLOAD',
    fileInfoId: 200,
    externalUrl: null,
    richContent: null,
    mimeType: 'video/mp4',
    fileSize: 104857600,
    pageCount: null,
    durationSeconds: 1800,
    checksum: 'mock-video-checksum',
    processStatus: 'FAILED',
    status: 1,
    version: 1
  },
  {
    assetId: 21,
    assetCode: 'AST-DOC-001',
    assetName: '安全培训手册',
    assetType: 'DOCUMENT',
    sourceType: 'UPLOAD',
    fileInfoId: 201,
    externalUrl: null,
    richContent: null,
    mimeType: 'application/pdf',
    fileSize: 5242880,
    pageCount: 36,
    durationSeconds: null,
    checksum: 'mock-document-checksum',
    processStatus: 'SUCCEEDED',
    status: 1,
    version: 1
  },
  {
    assetId: 22,
    assetCode: 'AST-AUDIO-001',
    assetName: '班前安全提示音频',
    assetType: 'AUDIO',
    sourceType: 'UPLOAD',
    fileInfoId: 202,
    externalUrl: null,
    richContent: null,
    mimeType: 'audio/mpeg',
    fileSize: 3145728,
    pageCount: null,
    durationSeconds: 420,
    checksum: 'mock-audio-checksum',
    processStatus: 'PROCESSING',
    status: 1,
    version: 1
  }
];

const INITIAL_JOBS: AssetTranscodeJob[] = [
  {
    transcodeJobId: 1,
    assetId: 20,
    jobType: 'VIDEO_TRANSCODE',
    jobStatus: 'FAILED',
    startedTime: '2026-07-22T09:00:00',
    finishedTime: '2026-07-22T09:02:00',
    retryCount: 0,
    failureCode: 'MOCK_TRANSCODE_FAILED',
    failureReason: '模拟转码失败，可使用重试操作。',
    createTime: '2026-07-22T09:00:00'
  },
  {
    transcodeJobId: 2,
    assetId: 22,
    jobType: 'AUDIO_TRANSCODE',
    jobStatus: 'RUNNING',
    startedTime: '2026-07-22T10:00:00',
    finishedTime: null,
    retryCount: 0,
    failureCode: null,
    failureReason: null,
    createTime: '2026-07-22T10:00:00'
  }
];

let assets: AssetDetail[] = [];
let jobs: AssetTranscodeJob[] = [];
let uploadedFiles = new Map<number, UploadedFileInfo>();
let protectedAssetIds = new Set<number>();
let nextAssetId = 100;
let nextJobId = 100;
let nextFileInfoId = 500;

/** 恢复稳定种子数据，供页面刷新与自动化测试重复使用。 */
export function resetAssetMockState(): void {
  assets = structuredClone(INITIAL_ASSETS);
  jobs = structuredClone(INITIAL_JOBS);
  uploadedFiles = new Map([
    [200, { fileInfoId: 200, fileName: 'fire-safety.mp4', fileSize: 104857600, fileSuffix: 'mp4' }],
    [201, { fileInfoId: 201, fileName: 'safety-manual.pdf', fileSize: 5242880, fileSuffix: 'pdf' }],
    [202, { fileInfoId: 202, fileName: 'safety-audio.mp3', fileSize: 3145728, fileSuffix: 'mp3' }]
  ]);
  protectedAssetIds = new Set();
  nextAssetId = 100;
  nextJobId = 100;
  nextFileInfoId = 500;
}

function findAsset(assetId: number): AssetDetail {
  const asset = assets.find((item) => item.assetId === assetId);
  if (!asset) throw new AssetContractMockError(6011, '素材不存在或已被删除。');
  return asset;
}

/** 只读校验课程引用的素材已启用且处理成功，供课程 Contract Mock 复用。 */
export function assertCourseAssetUsable(assetId: number): void {
  const asset = findAsset(assetId);
  if (asset.status !== 1 || asset.processStatus !== 'SUCCEEDED') {
    throw new AssetContractMockError(6012, '素材尚未处理完成或已停用，暂不可引用。');
  }
}

/** 同步课程侧当前实际引用，用于模拟后端删除素材时的引用保护。 */
export function syncCourseAssetReferences(assetIds: number[]): void {
  protectedAssetIds = new Set(assetIds);
}

function validateCreateRequest(data: AssetCreateRequest): void {
  if (assets.some((item) => item.assetCode === data.assetCode)) {
    throw new AssetContractMockError(6021, '素材编码已存在。');
  }
  if (data.sourceType === 'UPLOAD' && (!data.fileInfoId || !uploadedFiles.has(data.fileInfoId))) {
    throw new AssetContractMockError(6021, '上传来源必须关联已上传文件。');
  }
  if (data.sourceType === 'EXTERNAL' && !data.externalUrl) {
    throw new AssetContractMockError(6021, '外部来源必须填写访问地址。');
  }
  if (data.sourceType === 'CREATED' && !data.richContent) {
    throw new AssetContractMockError(6021, '在线创建来源必须填写富文本内容。');
  }
}

function createTranscodeJob(asset: AssetDetail, retryCount = 0): void {
  if (asset.assetType !== 'AUDIO' && asset.assetType !== 'VIDEO') return;
  jobs.push({
    transcodeJobId: nextJobId++,
    assetId: asset.assetId,
    jobType: asset.assetType === 'AUDIO' ? 'AUDIO_TRANSCODE' : 'VIDEO_TRANSCODE',
    jobStatus: 'PENDING',
    startedTime: null,
    finishedTime: null,
    retryCount,
    failureCode: null,
    failureReason: null,
    createTime: new Date().toISOString()
  });
}

/** 按 OpenAPI query 字段组合筛选并分页。 */
export async function mockGetAssetPage(params: AssetPageParams): Promise<AssetPage> {
  const name = params['query.assetName']?.trim().toLocaleLowerCase();
  const filtered = assets.filter(
    (asset) =>
      (!name || asset.assetName.toLocaleLowerCase().includes(name)) &&
      (!params['query.assetType'] || asset.assetType === params['query.assetType']) &&
      (!params['query.processStatus'] || asset.processStatus === params['query.processStatus']) &&
      (params['query.status'] === undefined || asset.status === params['query.status'])
  );
  const start = Math.max(0, params.pageNum - 1) * params.pageSize;
  return { list: structuredClone(filtered.slice(start, start + params.pageSize)), total: filtered.length };
}

/** 查询单个素材详情。 */
export async function mockGetAssetDetail(assetId: number): Promise<AssetDetail> {
  return structuredClone(findAsset(assetId));
}

/** 返回与真实 play-info 路径结构一致的预览数据，供素材库离线验证播放器路由。 */
export async function mockGetAssetPlayInfo(assetId: number): Promise<AssetPlayInfo> {
  const asset = findAsset(assetId);
  if (asset.processStatus !== 'SUCCEEDED') throw new AssetContractMockError(6011, '素材尚未处理完成。');
  const format: AssetPlayInfo['format'] = asset.assetType === 'VIDEO' || asset.assetType === 'AUDIO'
    ? 'hls'
    : asset.mimeType === 'application/pdf'
      ? 'pdf'
      : asset.assetType === 'IMAGE'
        ? 'image'
        : 'original';
  return {
    assetId,
    format,
    url: format === 'hls' ? `/api/asset/files/${assetId}/hls/1/index.m3u8` : `/api/asset/files/${assetId}/${asset.fileInfoId ?? 0}`,
    coverUrl: null,
    durationSeconds: asset.durationSeconds,
    pageCount: asset.pageCount
  };
}

/** 创建素材，并为音视频生成待执行转码任务。 */
export async function mockCreateAsset(data: AssetCreateRequest): Promise<number> {
  validateCreateRequest(data);
  const requiresTranscode = data.assetType === 'AUDIO' || data.assetType === 'VIDEO';
  const asset: AssetDetail = {
    assetId: nextAssetId++,
    assetCode: data.assetCode,
    assetName: data.assetName,
    assetType: data.assetType,
    sourceType: data.sourceType,
    fileInfoId: data.fileInfoId ?? null,
    externalUrl: data.externalUrl ?? null,
    richContent: data.richContent ?? null,
    mimeType: data.mimeType ?? null,
    fileSize: data.fileSize ?? null,
    pageCount: data.pageCount ?? null,
    durationSeconds: data.durationSeconds ?? null,
    checksum: data.checksum ?? null,
    processStatus: requiresTranscode ? 'PROCESSING' : 'SUCCEEDED',
    status: data.status ?? 1,
    version: 0
  };
  assets.push(asset);
  if (requiresTranscode) createTranscodeJob(asset);
  return asset.assetId;
}

/** 按乐观锁版本更新素材名称与启停用状态。 */
export async function mockUpdateAsset(assetId: number, data: AssetUpdateRequest): Promise<void> {
  const asset = findAsset(assetId);
  if (asset.version !== data.version) throw new AssetContractMockError(2040, '数据已被更新，请刷新后重试。');
  asset.assetName = data.assetName;
  asset.status = data.status;
  asset.version += 1;
}

/** 模拟课程引用和处理中状态的删除保护。 */
export async function mockDeleteAsset(assetId: number): Promise<void> {
  const asset = findAsset(assetId);
  if (protectedAssetIds.has(assetId)) throw new AssetContractMockError(6023, '素材仍被课程引用，不可删除。');
  if (asset.processStatus === 'PROCESSING') throw new AssetContractMockError(6022, '转码处理中，不可删除。');
  assets = assets.filter((item) => item.assetId !== assetId);
}

/** 仅允许失败的音视频重新进入转码队列。 */
export async function mockRetryAssetTranscode(assetId: number): Promise<void> {
  const asset = findAsset(assetId);
  if ((asset.assetType !== 'AUDIO' && asset.assetType !== 'VIDEO') || asset.processStatus !== 'FAILED') {
    throw new AssetContractMockError(6022, '仅失败的音视频素材允许重试转码。');
  }
  const retryCount = Math.max(0, ...jobs.filter((job) => job.assetId === assetId).map((job) => job.retryCount)) + 1;
  asset.processStatus = 'PROCESSING';
  asset.version += 1;
  createTranscodeJob(asset, retryCount);
}

/** 查询全部转码任务，或按素材 ID 查询。 */
export async function mockGetAssetTranscodeJobs(assetId?: number): Promise<AssetTranscodeJob[]> {
  return structuredClone(assetId === undefined ? jobs : jobs.filter((job) => job.assetId === assetId));
}

/** 模拟系统文件上传并返回可被素材创建关联的 fileInfoId。 */
export async function mockUploadAssetFile(file: File): Promise<UploadedFileInfo> {
  const fileInfo: UploadedFileInfo = {
    fileInfoId: nextFileInfoId++,
    fileName: file.name,
    fileSize: file.size,
    fileSuffix: file.name.includes('.') ? file.name.split('.').pop() : undefined
  };
  uploadedFiles.set(fileInfo.fileInfoId, fileInfo);
  return structuredClone(fileInfo);
}

resetAssetMockState();
