import type {ApiResponse} from '@shared/types'
import * as assetMock from '@/mock/asset'
import {request} from './request'

/** 移动端支持的素材类型，与 asset.openapi.json 保持一致。 */
export type AssetType = 'DOCUMENT' | 'IMAGE' | 'AUDIO' | 'VIDEO' | 'RICH_TEXT' | 'LINK' | 'LIVE'
export type AssetProcessStatus = 'UPLOADED' | 'PROCESSING' | 'SUCCEEDED' | 'FAILED'

/** 后端受控播放信息；url 只能来自 play-info 或文件访问接口。 */
export interface AssetPlayInfo {
  assetId: number
  format: 'hls' | 'pdf' | 'excel' | 'image' | 'original'
  url: string
  coverUrl: string | null
  durationSeconds: number | null
  pageCount: number | null
}

/** 移动端素材详情。 */
export interface AssetDetail {
  assetId: number
  assetName: string
  assetType: AssetType
  fileInfoId: number | null
  externalUrl: string | null
  richContent: string | null
  mimeType: string | null
  processStatus: AssetProcessStatus
}

/** 默认调用线上真实接口，仅显式开启时保留本地 Mock。 */
const USE_API_MOCK = import.meta.env.VITE_USE_API_MOCK === 'true'

/** 解包统一 Result 响应。 */
function unwrapResponse<T>(response: ApiResponse<T>): T {
  if (response.code !== 0) throw new Error(response.message || '素材请求失败，请稍后重试。')
  return response.data
}

/** 查询素材详情。 */
export async function getAssetDetail(assetId: number): Promise<AssetDetail> {
  const response = USE_API_MOCK
    ? await assetMock.mockGetAssetDetail(assetId)
    : await request<ApiResponse<AssetDetail>>({ url: `/api/asset/assets/${assetId}`, method: 'GET' })
  return unwrapResponse(response)
}

/** 获取受控播放或预览地址。 */
export async function getAssetPlayInfo(assetId: number): Promise<AssetPlayInfo> {
  const response = USE_API_MOCK
    ? await assetMock.mockGetAssetPlayInfo(assetId)
    : await request<ApiResponse<AssetPlayInfo>>({ url: `/api/asset/assets/${assetId}/play-info`, method: 'GET' })
  return unwrapResponse(response)
}

/** 构造受控文件访问入口，不暴露存储服务地址。 */
export function getAssetFileUrl(assetId: number, fileInfoId: number): string {
  return `/api/asset/files/${assetId}/${fileInfoId}`
}
