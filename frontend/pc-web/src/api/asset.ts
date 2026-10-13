import type {ApiResponse} from '@shared/types';
import {sha256Bytes} from '@shared/libs/sha256';
import {request} from './request';
import {
  mockCreateAsset,
  mockDeleteAsset,
  mockGetAssetDetail,
  mockGetAssetPage,
  mockGetAssetPlayInfo,
  mockGetAssetTranscodeJobs,
  mockRetryAssetTranscode,
  mockUpdateAsset,
  mockUploadAssetFile
} from '@/mock/asset';

/** 素材类型，与 asset.openapi.json 的枚举保持一致。 */
export type AssetType = 'DOCUMENT' | 'IMAGE' | 'AUDIO' | 'VIDEO' | 'RICH_TEXT' | 'LINK' | 'LIVE';
/** 素材来源类型，与创建 Contract 保持一致。 */
export type AssetSourceType = 'UPLOAD' | 'CREATED' | 'EXTERNAL';
/** 素材处理状态。 */
export type AssetProcessStatus = 'UPLOADED' | 'PROCESSING' | 'SUCCEEDED' | 'FAILED';
/** 素材启停用状态。 */
export type AssetStatus = 0 | 1;
/** 转码任务类型。 */
export type AssetTranscodeJobType = 'AUDIO_TRANSCODE' | 'VIDEO_TRANSCODE' | 'DOCUMENT_PREVIEW';
/** 转码任务状态。 */
export type AssetTranscodeJobStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';

/** 素材分页查询参数，字段名直接对应 OpenAPI query 参数。 */
export interface AssetPageParams {
  pageNum: number;
  pageSize: number;
  'query.assetName'?: string;
  'query.assetType'?: AssetType;
  'query.processStatus'?: AssetProcessStatus;
  'query.status'?: AssetStatus;
}

/** 素材列表行。 */
export interface AssetListItem {
  assetId: number;
  assetCode: string;
  assetName: string;
  assetType: AssetType;
  processStatus: AssetProcessStatus;
  status: AssetStatus;
}

/** 通用素材分页结构。 */
export interface AssetPage {
  list: AssetListItem[];
  total: number;
}

/** 创建素材时可提交的元数据。 */
export interface AssetCreateRequest {
  assetCode: string;
  assetName: string;
  assetType: AssetType;
  sourceType: AssetSourceType;
  fileInfoId?: number | null;
  externalUrl?: string | null;
  richContent?: string | null;
  mimeType?: string | null;
  fileSize?: number | null;
  pageCount?: number | null;
  durationSeconds?: number | null;
  checksum?: string | null;
  status?: AssetStatus;
}

/** 更新素材仅提交 Schema 明确允许修改的字段。 */
export interface AssetUpdateRequest {
  assetName: string;
  status: AssetStatus;
  version: number;
}

/** 素材详情。 */
export interface AssetDetail extends AssetListItem {
  sourceType: AssetSourceType;
  fileInfoId: number | null;
  externalUrl: string | null;
  richContent: string | null;
  mimeType: string | null;
  fileSize: number | null;
  pageCount: number | null;
  durationSeconds: number | null;
  checksum: string | null;
  version: number;
}

/** 转码任务列表项。 */
export interface AssetTranscodeJob {
  transcodeJobId: number;
  assetId: number;
  jobType: AssetTranscodeJobType;
  jobStatus: AssetTranscodeJobStatus;
  startedTime: string | null;
  finishedTime: string | null;
  retryCount: number;
  failureCode: string | null;
  failureReason: string | null;
  createTime: string;
}

/** 系统文件上传结果，仅使用 system-admin.openapi.json 已定义字段。 */
export interface UploadedFileInfo {
  fileInfoId: number;
  fileName: string;
  fileSize: number;
  fileSuffix?: string;
  fileUrl?: string;
}

export interface AssetPlayInfo {
  assetId: number;
  format: 'hls' | 'pdf' | 'excel' | 'image' | 'original';
  url: string;
  coverUrl: string | null;
  durationSeconds: number | null;
  pageCount: number | null;
}

interface MultipartInitVO {
  uploadId: string;
  partSize: number;
  uploadedParts: number[];
}

interface MultipartPartVO {
  partNumber: number;
  etag: string;
}

interface MultipartCompleteVO {
  fileInfoId: number;
}

const multipartUploadIds = new Map<File, string>();

/** 以文件字节内容计算 SHA-256，作为 multipart 会话的幂等与完整性依据。 */
async function sha256File(file: File): Promise<string> {
  return sha256Bytes(await file.arrayBuffer());
}

/** 大文件使用 multipart，小文件保持一次性上传，阈值与后端分片大小解耦。 */
export function shouldUseMultipart(fileSize: number): boolean {
  return fileSize >= 10 * 1024 * 1024;
}

/** 通过 MinIO multipart 接口上传素材文件，并复用服务端已上传分片实现断点续传。 */
async function uploadMultipartFile(
  file: File,
  onProgress?: (percent: number) => void,
  folder = 'asset',
  signal?: AbortSignal
): Promise<UploadedFileInfo> {
  const checksum = await sha256File(file);
  const init = unwrapResponse(
    await request<ApiResponse<MultipartInitVO>>({
      url: '/api/system/files/multipart/init',
      method: 'POST',
      data: {
        fileName: file.name,
        contentType: file.type || 'application/octet-stream',
        fileSize: file.size,
        checksum,
        folder
      },
      signal
    })
  );
  multipartUploadIds.set(file, init.uploadId);
  const parts: MultipartPartVO[] = [];
  const totalParts = Math.ceil(file.size / init.partSize);
  for (let index = 0; index < totalParts; index += 1) {
    const partNumber = index + 1;
    // 现有 OpenAPI 只返回已上传序号，不返回历史 ETag；恢复时重传该分片以重新取得 ETag。
    const body = new FormData();
    body.append('file', file.slice(index * init.partSize, Math.min(file.size, (index + 1) * init.partSize)), file.name);
    const part = unwrapResponse(
      await request<ApiResponse<MultipartPartVO>>({
        url: `/api/system/files/multipart/${init.uploadId}/parts/${partNumber}`,
        method: 'POST',
        data: body,
        headers: {'Content-Type': 'multipart/form-data'},
        signal,
        onUploadProgress: (event) => {
          if (event.total) onProgress?.(((index + event.loaded / event.total) / totalParts) * 100);
        }
      })
    );
    parts.push(part);
  }
  const complete = unwrapResponse(
    await request<ApiResponse<MultipartCompleteVO>>({
      url: `/api/system/files/multipart/${init.uploadId}/complete`,
      method: 'POST',
      data: {parts},
      signal
    })
  );
  multipartUploadIds.delete(file);
  onProgress?.(100);
  return { fileInfoId: complete.fileInfoId, fileName: file.name, fileSize: file.size, fileSuffix: file.name.split('.').pop() };
}

const ASSET_ERROR_MESSAGES: Record<number, string> = {
  5006: '素材文件格式不兼容，请将旧 DOC/DOCX 转换为标准 DOCX 或 PDF 后再上传。',
  2040: '数据已被更新，请刷新后重试。',
  6011: '素材不存在或已被删除。',
  6021: '素材信息校验失败或素材编码已存在。',
  6022: '当前素材状态不允许该操作。',
  6023: '素材仍被课程引用，不可删除。',
  6024: '转码任务不存在。'
};

/**
 * 素材 Contract Mock 统一开关。
 * 只有显式设置 VITE_USE_API_MOCK=true 时启用 Mock，默认使用真实素材接口。
 */
export function isAssetContractMockEnabled(): boolean {
  const runtimeOverride = (globalThis as { __ASSET_CONTRACT_MOCK_ENABLED__?: boolean }).__ASSET_CONTRACT_MOCK_ENABLED__;
  return runtimeOverride ?? import.meta.env?.VITE_USE_API_MOCK === 'true';
}

/** 从 Axios 边界错误或普通 Error 中提取素材域中文反馈。 */
export function getAssetErrorMessage(error: unknown): string {
  const response = (error as { response?: { data?: { code?: number; message?: string } } } | undefined)?.response?.data;
  if (response?.code !== undefined && ASSET_ERROR_MESSAGES[response.code]) {
    return ASSET_ERROR_MESSAGES[response.code];
  }
  if (response?.message) {
    return response.message;
  }
  return error instanceof Error ? error.message : '请求失败，请稍后重试。';
}

/** 解包统一响应，并将素材域业务错误转换为可操作的中文反馈。 */
function unwrapResponse<TData>(response: ApiResponse<TData>): TData {
  if (response.code !== 0) {
    throw new Error(ASSET_ERROR_MESSAGES[response.code] ?? response.message ?? '请求失败，请稍后重试。');
  }
  return response.data;
}

/** 分页查询素材。 */
export async function getAssetPage(params: AssetPageParams): Promise<AssetPage> {
  if (isAssetContractMockEnabled()) return mockGetAssetPage(params);
  return unwrapResponse(await request<ApiResponse<AssetPage>>({ url: '/api/asset/assets', method: 'GET', params }));
}

/** 查询素材详情。 */
export async function getAssetDetail(assetId: number): Promise<AssetDetail> {
  if (isAssetContractMockEnabled()) return mockGetAssetDetail(assetId);
  return unwrapResponse(await request<ApiResponse<AssetDetail>>({ url: `/api/asset/assets/${assetId}`, method: 'GET' }));
}

/** 创建素材。 */
export async function createAsset(data: AssetCreateRequest): Promise<number> {
  if (isAssetContractMockEnabled()) return mockCreateAsset(data);
  return unwrapResponse(await request<ApiResponse<number>>({ url: '/api/asset/assets', method: 'POST', data }));
}

/** 更新素材名称、状态和乐观锁版本。 */
export async function updateAsset(assetId: number, data: AssetUpdateRequest): Promise<void> {
  if (isAssetContractMockEnabled()) return mockUpdateAsset(assetId, data);
  unwrapResponse(await request<ApiResponse<null>>({ url: `/api/asset/assets/${assetId}`, method: 'PUT', data }));
}

/** 删除素材。 */
export async function deleteAsset(assetId: number): Promise<void> {
  if (isAssetContractMockEnabled()) return mockDeleteAsset(assetId);
  unwrapResponse(await request<ApiResponse<null>>({ url: `/api/asset/assets/${assetId}`, method: 'DELETE' }));
}

/** 重试失败的音视频转码。 */
export async function retryAssetTranscode(assetId: number): Promise<void> {
  if (isAssetContractMockEnabled()) return mockRetryAssetTranscode(assetId);
  unwrapResponse(await request<ApiResponse<null>>({ url: `/api/asset/assets/${assetId}/retry-transcode`, method: 'POST' }));
}

/** 取消当前文件对应的 multipart 会话；暂停只中止请求，不调用此方法，以便后续恢复。 */
export async function cancelAssetUpload(file: File): Promise<void> {
  const uploadId = multipartUploadIds.get(file);
  if (!uploadId) return;
  multipartUploadIds.delete(file);
  unwrapResponse(await request<ApiResponse<null>>({ url: `/api/system/files/multipart/${uploadId}`, method: 'DELETE' }));
}

/** 获取管理端当前机构范围内素材的受控播放或预览信息。 */
export async function getAssetPlayInfo(assetId: number): Promise<AssetPlayInfo> {
  if (isAssetContractMockEnabled()) return mockGetAssetPlayInfo(assetId);
  const response = await request<ApiResponse<AssetPlayInfo>>({ url: `/api/asset/assets/${assetId}/play-info`, method: 'GET' });
  return unwrapResponse(response);
}

/** 查询素材转码任务；assetId 为空时查询全部任务。 */
export async function getAssetTranscodeJobs(assetId?: number): Promise<AssetTranscodeJob[]> {
  if (isAssetContractMockEnabled()) return mockGetAssetTranscodeJobs(assetId);
  return unwrapResponse(
    await request<ApiResponse<AssetTranscodeJob[]>>({
      url: '/api/asset/transcode-jobs',
      method: 'GET',
      ...(assetId === undefined ? {} : { params: { assetId } })
    })
  );
}

/** 上传系统文件并取得 fileInfoId；folder 默认保持素材库原有目录语义。 */
export async function uploadAssetFile(
  file: File,
  onProgress?: (percent: number) => void,
  folder = 'asset',
  signal?: AbortSignal
): Promise<UploadedFileInfo> {
  if (isAssetContractMockEnabled()) {
    onProgress?.(35);
    const result = await mockUploadAssetFile(file);
    onProgress?.(100);
    return result;
  }
  if (shouldUseMultipart(file.size)) return uploadMultipartFile(file, onProgress, folder, signal);
  const data = new FormData();
  data.append('file', file);
  data.append('folder', folder);
  return unwrapResponse(
    await request<ApiResponse<UploadedFileInfo>>({
      url: '/api/system/files',
      method: 'POST',
      data,
      headers: { 'Content-Type': 'multipart/form-data' },
      signal,
      onUploadProgress: (event) => {
        if (event.total) onProgress?.((event.loaded / event.total) * 100);
      }
    })
  );
}
