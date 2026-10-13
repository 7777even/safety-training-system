<script setup lang="ts">
import {onBeforeUnmount, ref, watch} from 'vue';
import {getAssetDetail, getAssetPlayInfo, type AssetDetail, type AssetPlayInfo, type AssetProcessStatus} from '@/api/asset';
import AssetViewer from '@/components/asset-viewer/AssetViewer.vue';
import { formatDuration } from '@/utils/duration';

const props = defineProps<{
  asset?: AssetDetail;
}>();

const playInfo = ref<AssetPlayInfo | null>(null);
const playInfoLoading = ref(false);
const playInfoError = ref('');
const processStatus = ref<AssetProcessStatus | null>(null);
let requestVersion = 0;
let processTimer: number | undefined;

/** 清理素材处理轮询，避免切换课程内容后继续请求旧素材。 */
function clearProcessTimer(): void {
  if (processTimer !== undefined) globalThis.clearInterval(processTimer);
  processTimer = undefined;
}

/** PDF 处理完成后再获取 play-info，避免预览到未线性化的中间文件。 */
function startProcessPolling(assetId: number, version: number): void {
  clearProcessTimer();
  processTimer = globalThis.setInterval(async () => {
    try {
      const detail = await getAssetDetail(assetId);
      if (version !== requestVersion) return;
      processStatus.value = detail.processStatus;
      if (detail.processStatus === 'UPLOADED' || detail.processStatus === 'PROCESSING') return;
      clearProcessTimer();
      if (detail.processStatus === 'SUCCEEDED') playInfo.value = await getAssetPlayInfo(assetId);
    } catch {
      // 保持处理中提示，下一轮继续读取最新状态。
    }
  }, 3000);
}

/** 根据当前素材获取受控播放地址，交给统一查看器处理文件格式。 */
watch(
  () => props.asset?.assetId,
  async (assetId) => {
    const version = ++requestVersion;
    clearProcessTimer();
    playInfo.value = null;
    playInfoError.value = '';
    processStatus.value = props.asset?.processStatus ?? null;
    if (!assetId || ['RICH_TEXT', 'LINK', 'LIVE'].includes(props.asset?.assetType ?? '')) return;

    playInfoLoading.value = true;
    try {
      const detail = await getAssetDetail(assetId);
      if (version !== requestVersion) return;
      processStatus.value = detail.processStatus;
      if (detail.processStatus === 'UPLOADED' || detail.processStatus === 'PROCESSING') {
        startProcessPolling(assetId, version);
        return;
      }
      if (detail.processStatus === 'SUCCEEDED') playInfo.value = await getAssetPlayInfo(assetId);
    } catch (error) {
      if (version === requestVersion) playInfoError.value = error instanceof Error ? error.message : '素材预览加载失败';
    } finally {
      if (version === requestVersion) playInfoLoading.value = false;
    }
  },
  {immediate: true}
);

onBeforeUnmount(clearProcessTimer);
</script>

<template>
  <aside class="asset-preview-panel" aria-label="素材预览">
    <div class="preview-heading">
      <strong>素材预览</strong>
      <span v-if="asset">{{ asset.assetName }}</span>
    </div>

    <ElEmpty v-if="!asset" description="选择素材后查看预览" :image-size="64" />
    <div v-else-if="playInfoLoading && (processStatus === 'UPLOADED' || processStatus === 'PROCESSING')" class="preview-message">
      <strong>PDF 正在处理中</strong>
      <span>处理完成后将自动加载预览。</span>
    </div>
    <div v-else-if="playInfoLoading" class="preview-message">正在加载素材预览…</div>
    <div v-else-if="playInfoError" class="preview-message preview-error">
      <p>{{ playInfoError }}</p>
      <span>请稍后重试或下载文件查看。</span>
    </div>
    <div v-else-if="processStatus === 'FAILED'" class="preview-message preview-error">
      <strong>PDF 处理失败</strong>
      <span>请在素材库中重试处理后再预览。</span>
    </div>
    <AssetViewer
      v-else
      :asset-type="asset.assetType"
      :play-info="playInfo"
      :rich-content="asset.richContent"
      :external-url="asset.externalUrl"
      :file-name="asset.assetName"
      @error="playInfoError = $event"
    />

    <ElDescriptions v-if="asset" :column="1" size="small" border>
      <ElDescriptionsItem label="素材类型">{{ asset.assetType }}</ElDescriptionsItem>
      <ElDescriptionsItem label="文件大小">{{ asset.fileSize ? `${Math.ceil(asset.fileSize / 1024)} KB` : '—' }}</ElDescriptionsItem>
      <ElDescriptionsItem label="时长">{{ formatDuration(asset.durationSeconds, '—') }}</ElDescriptionsItem>
    </ElDescriptions>
  </aside>
</template>

<style scoped lang="less">
.asset-preview-panel {
  display: grid;
  min-width: 0;
  align-content: start;
  gap: 14px;
  padding: 18px;
  border-left: 1px solid var(--color-border);
  background: var(--color-bg-subtle);
}
.preview-heading strong,
.preview-heading span {
  display: block;
}
.preview-heading span {
  margin-top: 4px;
  overflow: hidden;
  color: var(--color-text-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.asset-preview-panel :deep(.asset-viewer) {
  min-height: 280px;
  max-height: 435px;
  padding: 0;
  border-radius: var(--radius-control);
  background: #fff;
  overflow: hidden;
}

.asset-preview-panel :deep(.open-file-viewer) {
  min-height: 280px;
  height: 280px !important;
}

.asset-preview-panel :deep(.media-video) {
  max-height: 280px;
}
.preview-message {
  display: grid;
  min-height: 130px;
  place-content: center;
  gap: 8px;
  padding: 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
  background: #fff;
  color: var(--color-text-regular);
  text-align: center;
}

.preview-message p {
  margin: 0;
}
.preview-message span {
  color: var(--color-text-secondary);
  font-size: 12px;
}

.preview-error {
  color: var(--color-danger);
}
</style>
