import type { ApiResponse } from '@shared/types'
import type { AssetDetail, AssetPlayInfo } from '@/api/asset'

/** 移动端素材预览 Contract Mock。 */
function success<T>(data: T): ApiResponse<T> {
  return { code: 0, message: 'success', data }
}

/** 返回课程示例素材详情。 */
export async function mockGetAssetDetail(assetId: number): Promise<ApiResponse<AssetDetail>> {
  return success({
    assetId,
    assetName: '课程示例视频.mp4',
    assetType: 'VIDEO',
    fileInfoId: 700 + assetId,
    externalUrl: null,
    richContent: null,
    mimeType: 'video/mp4',
    processStatus: 'SUCCEEDED'
  })
}

/** 返回受控媒体地址，供本地移动端预览验证。 */
export async function mockGetAssetPlayInfo(assetId: number): Promise<ApiResponse<AssetPlayInfo>> {
  return success({
    assetId,
    format: 'original',
    url: 'https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4',
    coverUrl: null,
    durationSeconds: 5,
    pageCount: null
  })
}
