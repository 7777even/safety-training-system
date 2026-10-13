import type {
  AssetProcessStatus,
  AssetStatus,
  AssetTranscodeJobStatus,
  AssetTranscodeJobType,
  AssetType
} from '@/api/asset';

/** 素材类型选项与中文显示文案。 */
export const ASSET_TYPE_OPTIONS: ReadonlyArray<{ label: string; value: AssetType }> = [
  { label: '文档', value: 'DOCUMENT' },
  { label: '图片', value: 'IMAGE' },
  { label: '音频', value: 'AUDIO' },
  { label: '视频', value: 'VIDEO' },
  { label: '富文本', value: 'RICH_TEXT' },
  { label: '外部链接', value: 'LINK' },
  { label: '直播', value: 'LIVE' }
];

/** 素材处理状态选项。 */
export const ASSET_PROCESS_STATUS_OPTIONS: ReadonlyArray<{ label: string; value: AssetProcessStatus }> = [
  { label: '待处理', value: 'UPLOADED' },
  { label: '处理中', value: 'PROCESSING' },
  { label: '处理成功', value: 'SUCCEEDED' },
  { label: '处理失败', value: 'FAILED' }
];

/** 转码任务状态中文文案。 */
export const TRANSCODE_JOB_STATUS_LABELS: Record<AssetTranscodeJobStatus, string> = {
  PENDING: '等待处理',
  RUNNING: '处理中',
  SUCCEEDED: '处理成功',
  FAILED: '处理失败'
};
/** 转码任务类型中文文案。 */
export const TRANSCODE_JOB_TYPE_LABELS: Record<AssetTranscodeJobType, string> = {
  AUDIO_TRANSCODE: '音频转码',
  VIDEO_TRANSCODE: '视频转码',
  DOCUMENT_PREVIEW: '文档预览'
};

/** 读取素材类型中文文案。 */
export function getAssetTypeLabel(assetType: AssetType): string {
  return ASSET_TYPE_OPTIONS.find((item) => item.value === assetType)?.label ?? assetType;
}

/** 读取素材处理状态中文文案。 */
export function getAssetProcessStatusLabel(processStatus: AssetProcessStatus): string {
  return ASSET_PROCESS_STATUS_OPTIONS.find((item) => item.value === processStatus)?.label ?? processStatus;
}

/** 只有失败的文档、音频或视频允许发起预览处理重试。 */
export function canRetryAssetTranscode(
  asset: Pick<{ assetType: AssetType; processStatus: AssetProcessStatus }, 'assetType' | 'processStatus'>
): boolean {
  return asset.processStatus === 'FAILED' && ['DOCUMENT', 'AUDIO', 'VIDEO'].includes(asset.assetType);
}

/** 读取素材启停用状态文案。 */
export function getAssetStatusLabel(status: AssetStatus): string {
  return status === 1 ? '启用' : '停用';
}

/** 素材原始文件上限，单位字节：音视频 2GB、文档 512MB、图片 16MB。 */
export const ASSET_ORIGINAL_FILE_MAX_BYTES: Partial<Record<AssetType, number>> = {
  AUDIO: 2 * 1024 * 1024 * 1024,
  VIDEO: 2 * 1024 * 1024 * 1024,
  DOCUMENT: 512 * 1024 * 1024,
  IMAGE: 16 * 1024 * 1024
};

/** 读取当前素材类型的原始文件上限说明。 */
export function getAssetOriginalFileSizeHint(assetType: AssetType): string | undefined {
  switch (assetType) {
    case 'AUDIO':
    case 'VIDEO':
      return '音视频原始文件不能超过 2GB';
    case 'DOCUMENT':
      return '文档原始文件不能超过 512MB';
    case 'IMAGE':
      return '图片原始文件不能超过 16MB';
    default:
      return undefined;
  }
}

/** 超过类型上限时返回中文错误；未超限或该类型无限额时返回 undefined。 */
export function getAssetOriginalFileSizeError(assetType: AssetType, fileSize: number): string | undefined {
  const maxBytes = ASSET_ORIGINAL_FILE_MAX_BYTES[assetType];
  if (maxBytes === undefined || fileSize <= maxBytes) return undefined;
  return getAssetOriginalFileSizeHint(assetType);
}

/**
 * 支持本地文件上传的素材类型；富文本、外部链接与直播依赖创建或外部地址，不走文件上传。
 * 顺序即上传弹窗中「素材类型」下拉项的展示顺序。
 */
export const ASSET_UPLOAD_TYPES: ReadonlyArray<AssetType> = ['DOCUMENT', 'IMAGE', 'AUDIO', 'VIDEO'];

/** 上传弹窗按素材类型给出的可选范围，直接透传给通用上传组件。 */
export interface AssetUploadTypeConfig {
  accept: string;
  maxFileSize?: number;
  sizeLimitHint?: string;
}

/** 各素材类型上传时的扩展名与 MIME 白名单，供通用上传组件限制可选文件。 */
export const ASSET_UPLOAD_ACCEPT: Record<AssetType, string> = {
  DOCUMENT:
    '.pdf,.ppt,.pptx,.doc,.docx,.xls,.xlsx,.csv,.txt,.md,application/pdf,application/msword,application/vnd.ms-office,application/vnd.openxmlformats-officedocument',
  IMAGE: '.jpg,.jpeg,.png,.gif,.webp,.bmp,.svg,image/*',
  AUDIO: '.mp3,.wav,.m4a,.aac,.ogg,.flac,audio/*',
  VIDEO: '.mp4,.webm,.mov,.avi,.mkv,.m3u8,video/*',
  RICH_TEXT: '',
  LINK: '',
  LIVE: ''
};

/** 上传弹窗的类型候选，只保留支持文件上传的类型，避免出现无白名单的空选项。 */
export const ASSET_UPLOAD_TYPE_OPTIONS: ReadonlyArray<{ label: string; value: AssetType }> = ASSET_TYPE_OPTIONS.filter((item) =>
  ASSET_UPLOAD_TYPES.includes(item.value)
);

/**
 * 上传弹窗的「素材类型 → 白名单 + 体积上限」配置，统一由契约常量派生。
 * 白名单取 ASSET_UPLOAD_ACCEPT、体积上限取 ASSET_ORIGINAL_FILE_MAX_BYTES，
 * 页面不得再自定义限制，否则会与后端校验口径（错误码 6025）不一致。
 */
export const ASSET_UPLOAD_TYPE_CONFIG: Record<string, AssetUploadTypeConfig> = Object.fromEntries(
  ASSET_UPLOAD_TYPES.map((assetType) => {
    const maxFileSize = ASSET_ORIGINAL_FILE_MAX_BYTES[assetType];
    const sizeLimitHint = getAssetOriginalFileSizeHint(assetType);
    return [
      assetType,
      {
        accept: ASSET_UPLOAD_ACCEPT[assetType],
        ...(maxFileSize === undefined ? {} : { maxFileSize }),
        ...(sizeLimitHint === undefined ? {} : { sizeLimitHint })
      }
    ];
  })
);
